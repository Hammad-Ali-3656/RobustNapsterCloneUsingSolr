import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DeleteNapsterCloneCores {
    // Solr URLs
    private static final String[] SOLR_URLS = {
            "http://localhost:8983/solr/admin/cores",
            "http://localhost:8984/solr/admin/cores"
    };

    // Core name
    private static final String CORE_NAME = "napster_clone";

    public static void main(String[] args) {
        System.out.println("Deleting Napster Clone cores from all Solr instances...");

        for (String solrUrl : SOLR_URLS) {
            deleteCore(solrUrl, CORE_NAME);
        }

        System.out.println("Core deletion process completed.");
    }

    private static void deleteCore(String solrUrl, String coreName) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(solrUrl + "?action=UNLOAD&core=" + coreName +
                        "&deleteInstanceDir=true&deleteDataDir=true&deleteIndex=true&wt=json"))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("Successfully deleted core '" + coreName + "' from " + solrUrl);
            } else {
                System.out.println("Error deleting core '" + coreName + "' from " + solrUrl);
                System.out.println("Error message: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error deleting core '" + coreName + "' from " + solrUrl);
            System.out.println("Error message: " + e.getMessage());
        }
    }
}