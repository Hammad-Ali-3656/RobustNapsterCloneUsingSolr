import java.io.File;

public class DeleteCoreDirectories {
    public static final String SOLR_HOME = "D:/Programs/Solr/solr-9.5.0/";
    

    public static void main(String[] args) {
        String usersCoreDir = SOLR_HOME + "server/solr/users";
        String filesCoreDir = SOLR_HOME + "server/solr/files";
        File users = new File(usersCoreDir);
        File files = new File(filesCoreDir);

        try {
            deleteDirectory(users);
            deleteDirectory(files);
            System.out.println("Core directories deleted successfully.");
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }

    public static void deleteDirectory(File directory) {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursively delete subdirectories
                    deleteDirectory(file);
                } else {
                    // Delete files
                    file.delete();
                }
            }
        }

        // Delete the empty directory after all its contents have been deleted
        directory.delete();
    }
}