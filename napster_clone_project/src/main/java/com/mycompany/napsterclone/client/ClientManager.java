package com.mycompany.napsterclone.client;

import com.mycompany.napsterclone.model.FileMetaData;
import com.mycompany.napsterclone.model.UpdateOperation;
import com.mycompany.napsterclone.solr.SolrClientManager;
import com.mycompany.napsterclone.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientManager {
    private static final Logger log = LoggerFactory.getLogger(ClientManager.class);

    private final String username;
    private final Path publishDirectory;
    private String localIpAddress;
    private int serverPort = -1; // Port the FileServer is listening on

    // In-memory cache of files currently shared BY THIS client
    private final Set<FileMetaData> localSharedFiles = new HashSet<>();

    public ClientManager(String username, Path publishDirectory) {
        this.username = username;
        this.publishDirectory = publishDirectory.toAbsolutePath(); // Ensure absolute path
        try {
            this.localIpAddress = NetworkUtils.getLocalIpAddress();
        } catch (IOException e) {
            log.error("Failed to determine local IP address", e);
            this.localIpAddress = "127.0.0.1"; // Fallback, might cause issues
        }
        log.info("ClientManager initialized for user '{}', sharing '{}', IP: {}",
                username, this.publishDirectory, this.localIpAddress);
    }

    public String getUsername() {
        return username;
    }

    public Path getPublishDirectory() {
        return publishDirectory;
    }

    public String getLocalIpAddress() {
        return localIpAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * Scans the local publish directory, compares it with the Solr index for this
     * user,
     * and sends necessary add/remove updates to Solr.
     *
     * @param solrManager The Solr client manager to interact with the index.
     * @throws IOException If there's an error scanning the directory.
     * @throws Exception   If there's an error communicating with Solr.
     */
    public synchronized void synchronizeFiles(SolrClientManager solrManager) throws Exception {
        log.info("Starting file synchronization for user: {}", username);

        // 1. Get current files from local publish directory
        Set<FileMetaData> currentLocalFiles = scanPublishDirectory();
        log.debug("Found {} files locally in {}", currentLocalFiles.size(), publishDirectory);

        // 2. Get files currently indexed in Solr for THIS user
        List<FileMetaData> indexedFilesList = solrManager.findFilesByUser(username);
        Set<FileMetaData> currentIndexedFiles = new HashSet<>(indexedFilesList);
        log.debug("Found {} files indexed in Solr for {}", currentIndexedFiles.size(), username);

        // 3. Calculate differences
        List<UpdateOperation> updates = new ArrayList<>();

        // Files to ADD: Present locally but not in index
        for (FileMetaData localFile : currentLocalFiles) {
            if (!currentIndexedFiles.contains(localFile)) {
                updates.add(new UpdateOperation(UpdateOperation.Type.ADD, localFile));
            }
        }

        // Files to REMOVE: Present in index but not locally
        for (FileMetaData indexedFile : currentIndexedFiles) {
            // Need to find the corresponding Solr ID for deletion
            String fileId = indexedFile.getId(); // Assuming findFilesByUser returns full FileMetaData with ID
            if (fileId == null) {
                log.warn("Indexed file '{}' missing Solr ID, cannot generate remove operation.",
                        indexedFile.getFilename());
                continue; // Skip if we don't have the ID
            }
            // Recreate a comparable FileMetaData object without IP/Port/ID for comparison
            // based on name/owner
            FileMetaData comparableIndexedFile = new FileMetaData(indexedFile.getFilename(), indexedFile.getSize(),
                    indexedFile.getOwnerUsername());
            if (!currentLocalFiles.contains(comparableIndexedFile)) {
                // Important: Use the indexedFile object which contains the Solr ID for removal
                updates.add(new UpdateOperation(UpdateOperation.Type.REMOVE, indexedFile));
            }
        }

        // 4. Apply updates to Solr
        if (!updates.isEmpty()) {
            log.info("Applying {} updates to Solr index for user {}", updates.size(), username);
            solrManager.applyFileUpdates(username, updates);
            log.info("Solr index updates applied successfully.");
        } else {
            log.info("No changes detected. Local directory and Solr index are synchronized for {}", username);
        }

        // 5. Update local cache (replace entirely with the latest scan)
        localSharedFiles.clear();
        localSharedFiles.addAll(currentLocalFiles);
        log.info("Synchronization complete. Local cache updated with {} files.", localSharedFiles.size());
    }

    /**
     * Scans the publish directory recursively and returns a set of FileMetaData
     * objects.
     *
     * @return A Set of FileMetaData representing files in the publish directory.
     * @throws IOException If an I/O error occurs during directory scanning.
     */
    private Set<FileMetaData> scanPublishDirectory() throws IOException {
        Set<FileMetaData> files = new HashSet<>();
        if (!Files.isDirectory(publishDirectory)) {
            log.warn("Publish directory {} does not exist or is not a directory. Cannot scan.", publishDirectory);
            return files; // Return empty set
        }

        Files.walkFileTree(publishDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile()) {
                    // Use relative path for filename to keep it consistent? No, use just the name.
                    String filename = file.getFileName().toString();
                    long size = attrs.size();
                    files.add(new FileMetaData(filename, size, username)); // ID, IP, Port are set later by SolrManager
                                                                           // when adding
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                log.warn("Failed to access file during scan: {} ({})", file, exc.getMessage());
                return FileVisitResult.CONTINUE; // Skip problematic files
            }
        });
        return files;
    }
}