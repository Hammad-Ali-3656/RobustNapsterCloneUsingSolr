import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class LoadBalancer {
    // Configure Solr servers with napster_clone core
    private static final String[] SOLR_SERVERS = {
            "http://localhost:8983/solr/napster_clone/",
            "http://localhost:8984/solr/napster_clone/"
    };

    // Default port for the load balancer
    static int port = 8080;

    // Track active requests per server
    private static int[] requestCounts;

    // Failover tracking
    private static boolean[] serverStatus;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Using default port 8080.");
            }
        }

        // Initialize tracking arrays
        requestCounts = new int[SOLR_SERVERS.length];
        serverStatus = new boolean[SOLR_SERVERS.length];

        for (int i = 0; i < SOLR_SERVERS.length; i++) {
            requestCounts[i] = 0;
            serverStatus[i] = true; // Assume all servers are initially up
        }

        // Start health check thread
        startHealthCheckThread();

        // Create and start the HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new RequestHandler());
        server.setExecutor(Executors.newFixedThreadPool(10)); // Use a thread pool
        server.start();
        System.out.println("Load balancer started on port " + port);
        System.out.println("Balancing requests between: ");
        for (String solrServer : SOLR_SERVERS) {
            System.out.println("  - " + solrServer);
        }
    }

    private static void startHealthCheckThread() {
        Thread healthChecker = new Thread(() -> {
            while (true) {
                checkServerHealth();
                try {
                    Thread.sleep(10000); // Check health every 10 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        healthChecker.setDaemon(true);
        healthChecker.start();
    }

    private static void checkServerHealth() {
        for (int i = 0; i < SOLR_SERVERS.length; i++) {
            try {
                HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create(SOLR_SERVERS[i] + "admin/ping?wt=json"))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .build();

                java.net.http.HttpResponse<String> response = httpClient.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());

                boolean wasDown = !serverStatus[i];
                serverStatus[i] = (response.statusCode() == 200);

                if (wasDown && serverStatus[i]) {
                    System.out.println(SOLR_SERVERS[i] + " is back online.");
                } else if (!serverStatus[i]) {
                    System.out.println(SOLR_SERVERS[i] + " appears to be down.");
                }

            } catch (Exception e) {
                if (serverStatus[i]) {
                    System.out.println(SOLR_SERVERS[i] + " is down: " + e.getMessage());
                    serverStatus[i] = false;
                }
            }
        }
    }

    static class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            InputStream requestBodyStream = null;
            OutputStream responseBodyStream = null;

            try {
                String requestUri = exchange.getRequestURI().toString();

                // Read request body
                requestBodyStream = exchange.getRequestBody();
                byte[] requestBodyBytes = readRequestBody(requestBodyStream);

                // Select server and forward request
                int serverIndex = getAvailableServerIndex();

                if (serverIndex >= 0) {
                    // We have an available server
                    requestCounts[serverIndex]++;

                    String solrUrl = SOLR_SERVERS[serverIndex] + requestUri;
                    System.out.println("Forwarding request to: " + solrUrl);

                    // Forward request to selected Solr server
                    HttpURLConnection solrConnection = forwardRequest(
                            solrUrl,
                            exchange.getRequestMethod(),
                            exchange.getRequestHeaders(),
                            requestBodyBytes);

                    // Get response from Solr server
                    int statusCode = solrConnection.getResponseCode();
                    InputStream responseStream = (statusCode >= 200 && statusCode < 300)
                            ? solrConnection.getInputStream()
                            : solrConnection.getErrorStream();

                    // Forward Solr response back to client
                    forwardResponse(exchange, statusCode, responseStream);

                    // Update request count
                    requestCounts[serverIndex]--;
                } else {
                    // No available servers - service unavailable
                    String errorMessage = "All Solr servers are currently unavailable";
                    exchange.sendResponseHeaders(503, errorMessage.length());
                    responseBodyStream = exchange.getResponseBody();
                    responseBodyStream.write(errorMessage.getBytes());
                }

            } catch (Exception e) {
                e.printStackTrace();
                String errorMsg = "Internal server error: " + e.getMessage();
                exchange.sendResponseHeaders(500, errorMsg.length());
                responseBodyStream = exchange.getResponseBody();
                responseBodyStream.write(errorMsg.getBytes());
            } finally {
                if (requestBodyStream != null) {
                    requestBodyStream.close();
                }
                if (responseBodyStream != null) {
                    responseBodyStream.close();
                }
                exchange.close();
            }
        }

        private int getAvailableServerIndex() {
            // First, find any available server with the least requests
            int minIndex = -1;
            int minRequests = Integer.MAX_VALUE;

            for (int i = 0; i < SOLR_SERVERS.length; i++) {
                if (serverStatus[i] && requestCounts[i] < minRequests) {
                    minRequests = requestCounts[i];
                    minIndex = i;
                }
            }

            return minIndex;
        }

        private byte[] readRequestBody(InputStream requestBodyStream) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int bytesRead;
            byte[] data = new byte[1024];
            while ((bytesRead = requestBodyStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        }

        private HttpURLConnection forwardRequest(String solrUrl, String requestMethod, Headers requestHeaders,
                byte[] requestBody) throws IOException, URISyntaxException {
            HttpURLConnection solrConnection = (HttpURLConnection) new URI(solrUrl).toURL().openConnection();
            solrConnection.setRequestMethod(requestMethod);
            solrConnection.setConnectTimeout(10000); // 10-second connect timeout
            solrConnection.setReadTimeout(30000); // 30-second read timeout

            // Forward headers
            for (Map.Entry<String, List<String>> header : requestHeaders.entrySet()) {
                // Skip host header as it causes problems
                if (!"Host".equalsIgnoreCase(header.getKey())) {
                    for (String value : header.getValue()) {
                        solrConnection.addRequestProperty(header.getKey(), value);
                    }
                }
            }

            // Write request body, if present and for appropriate methods
            if ((requestMethod.equals("POST") || requestMethod.equals("PUT")) && requestBody.length > 0) {
                solrConnection.setDoOutput(true);
                try (OutputStream os = solrConnection.getOutputStream()) {
                    os.write(requestBody);
                }
            }

            return solrConnection;
        }

        private void forwardResponse(HttpExchange exchange, int statusCode, InputStream responseStream)
                throws IOException {
            // Set response headers from Solr response
            exchange.sendResponseHeaders(statusCode, 0);

            // Stream the response body
            try (OutputStream responseBody = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = responseStream.read(buffer)) != -1) {
                    responseBody.write(buffer, 0, bytesRead);
                }
                responseBody.flush();
            } finally {
                responseStream.close();
            }
        }
    }
}