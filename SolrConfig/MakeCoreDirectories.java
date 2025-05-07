import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MakeCoreDirectories {
    // Path to your Solr installations
    public static final String SOLR_HOME_1 = "D:/solr-9.8.1/";
    public static final String SOLR_HOME_2 = "D:/solr-9.8.1/server2/";

    public static void main(String[] args) {
        createCoreDirectories(SOLR_HOME_1, "napster_clone");
        createCoreDirectories(SOLR_HOME_2, "napster_clone");
        System.out.println("Core directories creation complete.");
    }

    private static void createCoreDirectories(String solrHome, String coreName) {
        String confDir = solrHome + "server/solr/configsets/_default/conf";
        String coreDir = solrHome + "server/solr/" + coreName + "/conf";

        File confSource = new File(confDir);
        File coreDest = new File(coreDir);

        try {
            copyDirectory(confSource, coreDest);
            System.out.println(coreName + " core directory created successfully in " + solrHome);
        } catch (IOException e) {
            System.out.println("Error creating " + coreName + " core in " + solrHome + ": " + e.getMessage());
        }
    }

    public static void copyDirectory(File sourceDir, File destDir) throws IOException {
        // Create destination directory if it doesn't exist
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        // List all files and sub-directories in the source directory
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                // Construct the destination file path
                Path destFilePath = destDir.toPath().resolve(file.getName());
                if (file.isDirectory()) {
                    // If it's a directory, recursively copy it
                    copyDirectory(file, destFilePath.toFile());
                } else {
                    // If it's a file, copy it to the destination directory
                    Files.copy(file.toPath(), destFilePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}