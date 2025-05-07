import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class CreateNapsterCloneCores {
    // Solr URLs for both instances
    private static final String SOLR_URL_8983 = "http://localhost:8983/solr/";
    private static final String SOLR_URL_8984 = "http://localhost:8984/solr/";

    // Absolute paths to configsets (using escaped backslashes)
    private static final String CONFIGSET_PATH_8983 = "D:\\\\solr-9.8.1\\\\server\\\\solr\\\\configsets\\\\_default";
    private static final String CONFIGSET_PATH_8984 = "D:\\\\solr-9.8.1\\\\server2\\\\solr\\\\configsets\\\\_default";

    // Core name
    private static final String CORE_NAME = "napster_clone";

    // Schema definition
    private static final String SCHEMA = "{\"add-field\":{\"name\":\"doc_type_s\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"username_s\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"ip_s\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"port_i\",\"type\":\"pint\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"status_s\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"filename_s\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"filename_txt_en\",\"type\":\"text_en\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"size_l\",\"type\":\"plong\",\"indexed\":true,\"stored\":true,\"multiValued\":false},"
            +
            "\"add-field\":{\"name\":\"owner_username_s\",\"type\":\"string\",\"indexed\":true,\"stored\":true,\"multiValued\":false}}";

    public static void main(String[] args) {
        System.out.println("Starting Napster Clone core creation and schema update process...");

        // Process for Solr instance on port 8983
        System.out.println("\n=== Processing Solr instance on port 8983 ===");
        processSolrInstance(SOLR_URL_8983, CONFIGSET_PATH_8983);

        // Process for Solr instance on port 8984
        System.out.println("\n=== Processing Solr instance on port 8984 ===");
        processSolrInstance(SOLR_URL_8984, CONFIGSET_PATH_8984);

        System.out.println("\nSetting up replication between instances...");
        setupReplication();

        System.out.println("\nProcess completed for both Solr instances.");
    }

    private static void setupReplication() {
        // Configure first server as master
        configureAsMaster(SOLR_URL_8983 + CORE_NAME + "/replication");

        // Configure second server as slave pointing to master
        configureAsSlave(SOLR_URL_8984 + CORE_NAME + "/replication",
                "http://localhost:8983/solr/" + CORE_NAME + "/replication");
    }

    private static void configureAsMaster(String replicationUrl) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            String masterConfig = "?command=details&isMaster=true&enableReplication=true";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(replicationUrl + masterConfig))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Master configuration response: " + response.statusCode());

        } catch (Exception e) {
            System.out.println("Error configuring master: " + e.getMessage());
        }
    }

    private static void configureAsSlave(String slaveUrl, String masterUrl) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            String slaveConfig = "?command=details&isSlave=true&masterUrl=" +
                    URLEncoder.encode(masterUrl, StandardCharsets.UTF_8.toString()) +
                    "&pollInterval=00:00:60";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(slaveUrl + slaveConfig))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Slave configuration response: " + response.statusCode());

        } catch (Exception e) {
            System.out.println("Error configuring slave: " + e.getMessage());
        }
    }

    private static void processSolrInstance(String solrUrl, String configsetPath) {
        System.out.println("Using configset path: " + configsetPath);

        // Step 1: Delete core if it exists
        deleteCoreIfExists(solrUrl, CORE_NAME);

        // Step 2: Create core
        createCore(solrUrl, CORE_NAME, configsetPath);

        // Step 3: Update schema
        addSchema(solrUrl, CORE_NAME, SCHEMA);
    }

    private static void deleteCoreIfExists(String solrUrl, String coreName) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();

            String deleteUrl = solrUrl + "admin/cores?action=UNLOAD" +
                    "&core=" + coreName +
                    "&deleteInstanceDir=true" +
                    "&deleteDataDir=true" +
                    "&wt=json";

            System.out.println("\nAttempting to delete core if it exists: " + coreName);
            System.out.println("Delete URL: " + deleteUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Successfully deleted core: " + coreName);
            } else {
                System.out.println("Core did not exist or could not be deleted: " + coreName);
                System.out.println("Response: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error deleting core " + coreName + ": " + e.getMessage());
        }
    }

    private static void createCore(String solrUrl, String coreName, String configsetPath) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();

            // URL encode the absolute path
            String encodedPath = URLEncoder.encode(configsetPath, StandardCharsets.UTF_8.toString());

            String createUrl = solrUrl + "admin/cores?action=CREATE" +
                    "&name=" + coreName +
                    "&instanceDir=" + coreName +
                    "&configSet=" + encodedPath +
                    "&dataDir=data" +
                    "&numShards=1" +
                    "&replicationFactor=1" +
                    "&wt=json";

            System.out.println("\nCreating core: " + coreName);
            System.out.println("Create URL: " + createUrl);

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

    private static void addSchema(String solrUrl, String coreName, String schema) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();

            // First try to delete the fields if they exist
            tryDeleteFields(httpClient, solrUrl, coreName);

            // Then add the new schema
            System.out.println("\nUpdating schema for core: " + coreName);
            System.out.println("Schema: " + schema);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(solrUrl + coreName + "/schema"))
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

    private static void tryDeleteFields(HttpClient httpClient, String solrUrl, String coreName) throws Exception {
        // Delete all fields we plan to add
        String[] fieldsToDelete = {
                "doc_type_s", "username_s", "ip_s", "port_i", "status_s",
                "filename_s", "filename_txt_en", "size_l", "owner_username_s"
        };

        for (String field : fieldsToDelete) {
            try {
                String deleteJson = "{\"delete-field\":{\"name\":\"" + field + "\"}}";
                HttpRequest deleteRequest = HttpRequest.newBuilder()
                        .uri(URI.create(solrUrl + coreName + "/schema"))
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