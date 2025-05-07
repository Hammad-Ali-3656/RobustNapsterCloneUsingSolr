import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class CreateCores8984 {

    // Solr URL
    private static final String SOLR_URL = "http://localhost:8984/solr/";

    // Absolute path to configset (using escaped backslashes)
    private static final String CONFIGSET_PATH = "D:\\\\solr-9.8.1\\\\server2\\\\solr\\\\configsets\\\\_default";

    // Core names
    private static final String[] CORE_NAMES = { "users", "files" };

    // Schemas
    private static final String[] SCHEMAS = {
            // Users schema
            "{\"add-field\":{\"name\":\"ip_address\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
                    +
                    "\"add-field\":{\"name\":\"is_online\",\"type\":\"boolean\",\"indexed\":true,\"stored\":true,\"multiValued\":false}}",

            // Files schema
            "{\"add-field\":{\"name\":\"filename\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
                    +
                    "\"add-field\":{\"name\":\"filesize\",\"type\":\"plong\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
                    +
                    "\"add-field\":{\"name\":\"user\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false}}"
    };

    public static void main(String[] args) {
        System.out.println("Starting core creation with absolute configset path...");

        // Start Solr with required permissions
        System.out.println(
                "Make sure Solr is running with: solr start -Dsolr.allowPaths=D\\\\:\\\\solr-9.8.1\\\\server\\\\solr\\\\configsets");

        for (int i = 0; i < CORE_NAMES.length; i++) {
            createCore(CORE_NAMES[i]);
            addSchema(CORE_NAMES[i], SCHEMAS[i]);
        }
    }

    private static void createCore(String coreName) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();

            // URL encode the absolute path
            String encodedPath = URLEncoder.encode(CONFIGSET_PATH, StandardCharsets.UTF_8.toString());

            String createUrl = SOLR_URL + "admin/cores?action=CREATE" +
                    "&name=" + coreName +
                    "&instanceDir=" + coreName +
                    "&configSet=" + encodedPath +
                    "&dataDir=data" +
                    "&numShards=1" +
                    "&replicationFactor=1" +
                    "&wt=json";

            System.out.println("Creating core URL: " + createUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(createUrl))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Successfully created core: " + coreName);
            } else {
                System.out.println("Failed to create core: " + coreName);
                System.out.println("Error: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error creating core " + coreName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void addSchema(String coreName, String schema) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();

            // First try to delete the fields if they exist
            tryDeleteFields(httpClient, coreName);

            // Then add the new schema
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SOLR_URL + coreName + "/schema"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(schema))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Successfully updated schema for core: " + coreName);
            } else {
                System.out.println("Failed to update schema for core: " + coreName);
                System.out.println("Error: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error updating schema for core " + coreName + ": " + e.getMessage());
        }
    }

    private static void tryDeleteFields(HttpClient httpClient, String coreName) throws Exception {
        // Delete all fields we plan to add
        String[] fieldsToDelete = coreName.equals("users") ? new String[] { "ip_address", "is_online" }
                : new String[] { "filename", "filesize", "user" };

        for (String field : fieldsToDelete) {
            try {
                String deleteJson = "{\"delete-field\":{\"name\":\"" + field + "\"}}";
                HttpRequest deleteRequest = HttpRequest.newBuilder()
                        .uri(URI.create(SOLR_URL + coreName + "/schema"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(deleteJson))
                        .build();

                httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                // Field might not exist - that's okay
            }
        }
    }
}