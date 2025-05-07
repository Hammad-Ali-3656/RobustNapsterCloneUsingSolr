package com.mycompany.napsterclone.solr;

import com.mycompany.napsterclone.model.FileMetaData;
import com.mycompany.napsterclone.model.UpdateOperation;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient.RemoteSolrException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages communication with Solr for the Napster Clone application.
 * This implementation works with the load-balanced napster_clone core.
 * Enhanced with robust failover support for high availability.
 */
public class SolrClientManager {
    private static final Logger log = LoggerFactory.getLogger(SolrClientManager.class);

    private final String[] solrUrls;
    private Http2SolrClient solrClient;
    private boolean connected = false;
    private int currentSolrUrlIndex = 0;
    private int maxRetries = 3;

    // Field names (ensure these match your Solr schema)
    private static final String FIELD_ID = "id";
    private static final String FIELD_DOC_TYPE = "doc_type_s";
    private static final String DOC_TYPE_USER = "user";
    private static final String DOC_TYPE_FILE = "file";
    private static final String FIELD_USERNAME = "username_s";
    private static final String FIELD_IP = "ip_s";
    private static final String FIELD_PORT = "port_i";
    private static final String FIELD_STATUS = "status_s";
    private static final String FIELD_FILENAME = "filename_s";
    private static final String FIELD_FILENAME_SEARCH = "filename_txt_en";
    private static final String FIELD_SIZE = "size_l";
    private static final String FIELD_OWNER_USERNAME = "owner_username_s";

    private static final String STATUS_ONLINE = "online";
    private static final String STATUS_OFFLINE = "offline";

    private static final String USER_ID_PREFIX = "user-";
    private static final String FILE_ID_PREFIX = "file-";

    /**
     * Creates a new SolrClientManager that connects to the specified Solr URLs.
     * For load-balanced configuration, use: new SolrClientManager(new String[]
     * {"http://localhost:8983/solr/napster_clone",
     * "http://localhost:8984/solr/napster_clone"});
     *
     * @param solrUrls Array of Solr server URLs to connect to
     */
    public SolrClientManager(String[] solrUrls) {
        if (solrUrls == null || solrUrls.length == 0 || solrUrls[0] == null || solrUrls[0].trim().isEmpty()) {
            throw new IllegalArgumentException("Solr URLs cannot be null or empty.");
        }
        this.solrUrls = solrUrls;
    }

    /**
     * Interface for operations that will be executed with failover support
     */
    private interface SolrOperation<T> {
        T execute() throws SolrServerException, IOException;
    }

    /**
     * Connects to one of the configured Solr servers.
     * Will try servers in sequence until a successful connection is established.
     * 
     * @throws IOException         if a connection cannot be established due to I/O
     *                             errors
     * @throws SolrServerException if a connection cannot be established due to
     *                             Solr-specific errors
     */
    public synchronized void connect() throws IOException, SolrServerException {
        if (connected && solrClient != null) {
            try {
                solrClient.ping();
                log.debug("Already connected to Solr and connection is active.");
                return;
            } catch (Exception e) {
                log.warn("Previous Solr connection lost (ping failed), attempting to reconnect.", e);
                disconnect();
            }
        }

        IOException lastIOException = null;
        SolrServerException lastSolrException = null;
        Exception lastException = null;

        // Try each Solr URL in sequence
        for (int i = 0; i < solrUrls.length; i++) {
            currentSolrUrlIndex = (currentSolrUrlIndex + i) % solrUrls.length;
            String currentUrl = solrUrls[currentSolrUrlIndex];
            log.info("Attempting to connect to Solr instance: {}", currentUrl);

            Http2SolrClient.Builder builder = new Http2SolrClient.Builder(currentUrl)
                    .withConnectionTimeout(5000, TimeUnit.MILLISECONDS)
                    .withIdleTimeout(15000, TimeUnit.MILLISECONDS);

            Http2SolrClient clientAttempt = builder.build();

            try {
                clientAttempt.ping();
                this.solrClient = clientAttempt;
                connected = true;
                log.info("Successfully connected to Solr at {}", currentUrl);
                return; // Success
            } catch (RemoteSolrException e) {
                log.warn("RemoteSolrException connecting to {}: {}", currentUrl, e.getMessage());
                lastSolrException = new SolrServerException("Remote Solr error: " + e.getMessage(), e);
                lastException = e;
            } catch (SolrServerException e) {
                log.warn("SolrServerException connecting to {}: {}", currentUrl, e.getMessage());
                lastSolrException = e;
                lastException = e;
            } catch (IOException e) {
                log.warn("IOException connecting to {}: {}", currentUrl, e.getMessage());
                lastIOException = e;
                lastException = e;
            } catch (Exception e) {
                log.error("Unexpected error connecting to {}: {}", currentUrl, e.getMessage(), e);
                lastIOException = new IOException("Unexpected error during Solr connection: " + e.getMessage(), e);
                lastException = e;
            } finally {
                if (!connected && clientAttempt != null && clientAttempt != this.solrClient) {
                    try {
                        clientAttempt.close();
                    } catch (Exception ioe) {
                        log.error("Error closing failed Solr client attempt", ioe);
                    }
                }
            }
        }

        // If we get here, all connection attempts failed
        log.error("Failed to connect to any of {} configured Solr instances", solrUrls.length);

        if (lastSolrException != null) {
            throw lastSolrException;
        } else if (lastIOException != null) {
            throw lastIOException;
        } else if (lastException != null) {
            throw new IOException("Failed to connect to any Solr instance: " + lastException.getMessage(),
                    lastException);
        } else {
            throw new IOException(
                    "Failed to connect to any configured Solr instance (no specific exception recorded).");
        }
    }

    /**
     * Disconnects from the current Solr server.
     */
    public synchronized void disconnect() {
        if (solrClient != null) {
            log.info("Disconnecting from Solr instance: {}", solrClient.getBaseURL());
            try {
                solrClient.close();
            } catch (Exception e) {
                log.error("Error closing Solr client connection: {}", e.getMessage(), e);
            } finally {
                solrClient = null;
                connected = false;
            }
        }
    }

    /**
     * Ensures a connection to Solr exists, attempting to reconnect if necessary.
     * 
     * @throws IllegalStateException if a connection cannot be established
     */
    private void ensureConnected() throws IllegalStateException {
        if (connected && solrClient != null) {
            try {
                // Quick ping to verify connection is still valid
                solrClient.ping();
                return;
            } catch (Exception e) {
                log.warn("Solr connection check failed, will attempt to reconnect: {}", e.getMessage());
                disconnect(); // Clean up the failed connection
            }
        }

        log.warn("Solr client not connected. Attempting to reconnect...");
        try {
            connect();
        } catch (SolrServerException e) {
            log.error("Automatic reconnection to Solr failed.", e);
            throw new IllegalStateException("Not connected to Solr and reconnection failed: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("Automatic reconnection to Solr failed.", e);
            throw new IllegalStateException("Not connected to Solr and reconnection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a Solr operation with failover and retry support.
     * If an operation fails, it will try to reconnect to another Solr instance and
     * retry.
     *
     * @param operation     The Solr operation to execute
     * @param operationName A descriptive name for the operation (for logging)
     * @return The result of the operation
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     */
    private <T> T executeWithRetryAndFailover(SolrOperation<T> operation, String operationName)
            throws SolrServerException, IOException {
        ensureConnected();

        Exception lastException = null;
        int attemptsRemaining = maxRetries;

        while (attemptsRemaining > 0) {
            try {
                // Try the operation
                return operation.execute();
            } catch (Exception e) {
                attemptsRemaining--;
                lastException = e;

                log.warn("Operation '{}' failed on server {}. Attempts remaining: {}. Error: {}",
                        operationName, solrUrls[currentSolrUrlIndex], attemptsRemaining, e.getMessage());

                if (attemptsRemaining <= 0) {
                    log.error("All retry attempts exhausted for operation '{}'", operationName);
                    break;
                }

                // Try to reconnect to a different server
                try {
                    disconnect();
                    // Advance to next server in the list
                    currentSolrUrlIndex = (currentSolrUrlIndex + 1) % solrUrls.length;
                    connect();
                } catch (Exception reconnectEx) {
                    log.error("Failed to reconnect after operation failure: {}", reconnectEx.getMessage());
                    // If we can't reconnect at all, give up
                    if (reconnectEx instanceof SolrServerException) {
                        throw (SolrServerException) reconnectEx;
                    } else if (reconnectEx instanceof IOException) {
                        throw (IOException) reconnectEx;
                    } else {
                        throw new SolrServerException("Failed to reconnect: " + reconnectEx.getMessage(), reconnectEx);
                    }
                }
            }
        }

        // If we got here, all attempts failed
        if (lastException instanceof SolrServerException) {
            throw (SolrServerException) lastException;
        } else if (lastException instanceof IOException) {
            throw (IOException) lastException;
        } else if (lastException != null) {
            throw new SolrServerException("Operation failed: " + operationName, lastException);
        } else {
            throw new SolrServerException("Operation failed without specific exception: " + operationName);
        }
    }

    /**
     * Generates a document ID for a user.
     * 
     * @param username The username
     * @return The document ID
     */
    private String getUserDocId(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty for generating user document ID.");
        }
        return USER_ID_PREFIX + username;
    }

    /**
     * Registers a user in the Solr index or updates an existing user.
     * Sets the user status as online.
     * 
     * @param username The username
     * @param ip       The user's IP address
     * @param port     The user's port
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     */
    public void registerUser(String username, String ip, int port) throws SolrServerException, IOException {
        log.info("Registering/Updating user '{}' (IP: {}, Port: {}) as online", username, ip, port);

        executeWithRetryAndFailover(() -> {
            SolrInputDocument userDoc = new SolrInputDocument();
            userDoc.setField(FIELD_ID, getUserDocId(username));
            userDoc.setField(FIELD_DOC_TYPE, DOC_TYPE_USER);
            userDoc.setField(FIELD_USERNAME, username);
            userDoc.setField(FIELD_IP, ip);
            userDoc.setField(FIELD_PORT, port);
            userDoc.setField(FIELD_STATUS, STATUS_ONLINE);

            UpdateResponse response = solrClient.add(userDoc, 10000);
            handleUpdateResponse(response, "register user " + username);
            return null;
        }, "registerUser(" + username + ")");
    }

    /**
     * Updates a user's online status in the Solr index.
     * 
     * @param username The username
     * @param online   True for online status, false for offline
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     */
    public void setUserStatus(String username, boolean online) throws SolrServerException, IOException {
        String status = online ? STATUS_ONLINE : STATUS_OFFLINE;
        log.info("Setting status for user '{}' to {}", username, status);

        executeWithRetryAndFailover(() -> {
            SolrInputDocument userDoc = new SolrInputDocument();
            userDoc.setField(FIELD_ID, getUserDocId(username));
            userDoc.setField(FIELD_STATUS, Map.of("set", status));

            UpdateResponse response = solrClient.add(userDoc, 10000);
            handleUpdateResponse(response, "update user status " + username);
            return null;
        }, "setUserStatus(" + username + "," + online + ")");
    }

    /**
     * Generates a document ID for a file.
     * 
     * @param ownerUsername The username of the file owner
     * @param filename      The filename
     * @return The document ID
     */
    private String getFileDocId(String ownerUsername, String filename) {
        if (ownerUsername == null || ownerUsername.trim().isEmpty() || filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Owner username and filename cannot be null or empty for generating file document ID.");
        }
        return FILE_ID_PREFIX + ownerUsername + "-" + Objects.hash(filename);
    }

    /**
     * Prepares a file document for addition to Solr and adds it to the batch.
     * 
     * @param ownerUsername The username of the file owner
     * @param file          The file metadata
     * @param batch         The batch to add the document to
     */
    private void publishFileToSolrBatch(String ownerUsername, FileMetaData file, List<SolrInputDocument> batch) {
        if (file == null)
            return;
        SolrInputDocument fileDoc = new SolrInputDocument();
        String docId = getFileDocId(ownerUsername, file.getFilename());
        file.setId(docId);

        fileDoc.setField(FIELD_ID, docId);
        fileDoc.setField(FIELD_DOC_TYPE, DOC_TYPE_FILE);
        fileDoc.setField(FIELD_OWNER_USERNAME, ownerUsername);
        fileDoc.setField(FIELD_FILENAME, file.getFilename());
        fileDoc.setField(FIELD_FILENAME_SEARCH, file.getFilename());
        fileDoc.setField(FIELD_SIZE, file.getSize());
        batch.add(fileDoc);
        log.trace("Prepared ADD operation for file: {} (ID: {})", file.getFilename(), docId);
    }

    /**
     * Prepares a file for removal from Solr and adds its ID to the batch.
     * 
     * @param file           The file metadata
     * @param deleteIdsBatch The batch to add the file ID to
     */
    private void removeFileFromSolrBatch(FileMetaData file, List<String> deleteIdsBatch) {
        if (file == null)
            return;
        String docId = file.getId();
        if (docId == null || !docId.startsWith(FILE_ID_PREFIX)) {
            log.warn("File '{}' missing Solr ID for removal, attempting to derive.", file.getFilename());
            docId = getFileDocId(file.getOwnerUsername(), file.getFilename());
        }
        deleteIdsBatch.add(docId);
        log.trace("Prepared REMOVE operation for file ID: {}", docId);
    }

    /**
     * Applies a batch of file updates (additions and removals) to the Solr index.
     * 
     * @param username The username of the file owner
     * @param updates  The list of update operations
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     */
    public synchronized void applyFileUpdates(String username, List<UpdateOperation> updates)
            throws SolrServerException, IOException {
        if (updates == null || updates.isEmpty()) {
            log.info("No file updates to apply for user {}", username);
            return;
        }
        log.info("Applying {} file updates for user {}...", updates.size(), username);

        executeWithRetryAndFailover(() -> {
            List<SolrInputDocument> addBatch = new ArrayList<>();
            List<String> deleteIdsBatch = new ArrayList<>();

            for (UpdateOperation op : updates) {
                if (op == null || op.getFileMetaData() == null)
                    continue;

                FileMetaData file = op.getFileMetaData();
                if (op.getType() == UpdateOperation.Type.ADD) {
                    publishFileToSolrBatch(username, file, addBatch);
                } else if (op.getType() == UpdateOperation.Type.REMOVE) {
                    removeFileFromSolrBatch(file, deleteIdsBatch);
                }
            }

            if (!addBatch.isEmpty()) {
                log.debug("Adding {} files to Solr for user {}", addBatch.size(), username);
                UpdateResponse addResponse = solrClient.add(addBatch, 10000);
                handleUpdateResponse(addResponse, "batch add files for " + username);
            }
            if (!deleteIdsBatch.isEmpty()) {
                log.debug("Deleting {} files from Solr for user {}", deleteIdsBatch.size(), username);
                UpdateResponse deleteResponse = solrClient.deleteById(deleteIdsBatch, 10000);
                handleUpdateResponse(deleteResponse, "batch delete files for " + username);
            }

            log.info("Successfully applied {} file updates for {}", updates.size(), username);
            return null;
        }, "applyFileUpdates(" + username + ", " + updates.size() + " updates)");
    }

    /**
     * Finds all files belonging to a specific user.
     * 
     * @param username The username of the file owner
     * @return A list of file metadata
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     */
    public List<FileMetaData> findFilesByUser(String username) throws SolrServerException, IOException {
        log.debug("Querying Solr for files owned by user: {}", username);

        return executeWithRetryAndFailover(() -> {
            List<FileMetaData> userFiles = new ArrayList<>();

            SolrQuery query = new SolrQuery();
            query.setQuery(String.format("%s:%s AND %s:%s",
                    FIELD_DOC_TYPE, DOC_TYPE_FILE,
                    FIELD_OWNER_USERNAME, ClientUtils.escapeQueryChars(username)));
            query.setRows(Integer.MAX_VALUE);
            query.setFields(FIELD_ID, FIELD_FILENAME, FIELD_SIZE, FIELD_OWNER_USERNAME);

            QueryResponse response = solrClient.query(query);
            SolrDocumentList docList = response.getResults();
            if (docList != null) {
                for (SolrDocument doc : docList) {
                    userFiles.add(createFileMetaDataFromDoc(doc, null, 0));
                }
            }
            log.debug("Found {} indexed files for user '{}'", userFiles.size(), username);
            return userFiles;
        }, "findFilesByUser(" + username + ")");
    }

    /**
     * Helper method to get details of all online users except the requesting user.
     * 
     * @param requestingUsername The username of the user making the request (to
     *                           exclude)
     * @return A map of username to user document
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     */
    private Map<String, SolrDocument> getOnlineUserDetails(String requestingUsername)
            throws SolrServerException, IOException {
        return executeWithRetryAndFailover(() -> {
            SolrQuery userQuery = new SolrQuery();
            userQuery.setQuery(String.format("%s:%s AND %s:%s AND -%s:%s",
                    FIELD_DOC_TYPE, DOC_TYPE_USER,
                    FIELD_STATUS, STATUS_ONLINE,
                    FIELD_USERNAME, ClientUtils.escapeQueryChars(requestingUsername)));
            userQuery.setRows(10000);
            userQuery.setFields(FIELD_USERNAME, FIELD_IP, FIELD_PORT);

            QueryResponse userResponse = solrClient.query(userQuery);
            SolrDocumentList onlineUsersDocs = userResponse.getResults();

            if (onlineUsersDocs == null) {
                return Map.of();
            }

            return onlineUsersDocs.stream()
                    .filter(doc -> doc.getFieldValue(FIELD_USERNAME) != null)
                    .collect(Collectors.toMap(
                            doc -> (String) doc.getFieldValue(FIELD_USERNAME),
                            doc -> doc,
                            (doc1, doc2) -> doc1));
        }, "getOnlineUserDetails(" + requestingUsername + ")");
    }

    /**
     * Searches for files matching a query string, excluding those owned by the
     * requesting user.
     * Only returns files from users currently online.
     * 
     * @param queryString        The search query
     * @param requestingUsername The username of the user making the request (to
     *                           exclude their files)
     * @return A list of file metadata matching the query
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     */
    public List<FileMetaData> searchFiles(String queryString, String requestingUsername)
            throws SolrServerException, IOException {
        log.info("Searching for files matching '{}', excluding user '{}'", queryString, requestingUsername);

        return executeWithRetryAndFailover(() -> {
            List<FileMetaData> results = new ArrayList<>();

            Map<String, SolrDocument> onlineUserDetailsMap = getOnlineUserDetails(requestingUsername);
            if (onlineUserDetailsMap.isEmpty()) {
                log.info("No other online users found. Search yields no results for query: {}", queryString);
                return results;
            }

            // Step 2: Search for files
            SolrQuery fileQuery = new SolrQuery();
            String ownerFilter = onlineUserDetailsMap.keySet().stream()
                    .map(ClientUtils::escapeQueryChars)
                    .collect(Collectors.joining(" OR "));

            fileQuery.setQuery(String.format("%s:%s AND %s:(%s) AND %s:(%s)",
                    FIELD_DOC_TYPE, DOC_TYPE_FILE,
                    FIELD_OWNER_USERNAME, ownerFilter,
                    FIELD_FILENAME_SEARCH, ClientUtils.escapeQueryChars(queryString)));
            fileQuery.setRows(500);
            fileQuery.setFields(FIELD_ID, FIELD_FILENAME, FIELD_SIZE, FIELD_OWNER_USERNAME);

            QueryResponse fileResponse = solrClient.query(fileQuery);
            SolrDocumentList fileDocs = fileResponse.getResults();
            if (fileDocs != null) {
                for (SolrDocument fileDoc : fileDocs) {
                    String ownerUsername = (String) fileDoc.getFieldValue(FIELD_OWNER_USERNAME);
                    SolrDocument ownerDetailsDoc = onlineUserDetailsMap.get(ownerUsername);
                    if (ownerDetailsDoc != null) {
                        String ownerIp = (String) ownerDetailsDoc.getFieldValue(FIELD_IP);
                        Integer ownerPort = getIntegerField(ownerDetailsDoc, FIELD_PORT);

                        if (ownerIp != null && ownerPort != null && ownerPort > 0) {
                            results.add(createFileMetaDataFromDoc(fileDoc, ownerIp, ownerPort));
                        } else {
                            log.warn("Skipping file '{}' from user '{}' due to missing IP/Port details.",
                                    fileDoc.getFieldValue(FIELD_FILENAME), ownerUsername);
                        }
                    } else {
                        log.warn("File '{}' owner '{}' details not found in online map.",
                                fileDoc.getFieldValue(FIELD_FILENAME), ownerUsername);
                    }
                }
            }
            log.info("Search for '{}' completed. Found {} matching files.", queryString, results.size());
            return results;
        }, "searchFiles(" + queryString + "," + requestingUsername + ")");
    }

    /**
     * Creates a FileMetaData object from a Solr document.
     * 
     * @param doc       The Solr document
     * @param ownerIp   The IP address of the file owner (may be null)
     * @param ownerPort The port of the file owner (may be 0)
     * @return A FileMetaData object
     */
    private FileMetaData createFileMetaDataFromDoc(SolrDocument doc, String ownerIp, Integer ownerPort) {
        String id = (String) doc.getFieldValue(FIELD_ID);
        String filename = (String) doc.getFieldValue(FIELD_FILENAME);
        Long size = getLongField(doc, FIELD_SIZE);
        String ownerUsername = (String) doc.getFieldValue(FIELD_OWNER_USERNAME);

        return new FileMetaData(
                id != null ? id : "unknown-id",
                filename != null ? filename : "unknown-filename",
                size != null ? size : 0L,
                ownerUsername != null ? ownerUsername : "unknown-owner",
                ownerIp,
                (ownerPort != null ? ownerPort : 0));
    }

    /**
     * Helper method to get an Integer field from a Solr document.
     * 
     * @param doc       The Solr document
     * @param fieldName The field name
     * @return The field value or null if not found
     */
    private Integer getIntegerField(SolrDocument doc, String fieldName) {
        Object value = doc.getFieldValue(fieldName);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value != null) {
            log.warn("Unexpected type for integer field '{}' in doc id '{}': {}. Returning null.",
                    fieldName, doc.getFieldValue(FIELD_ID), value.getClass().getName());
        }
        return null;
    }

    /**
     * Helper method to get a Long field from a Solr document.
     * 
     * @param doc       The Solr document
     * @param fieldName The field name
     * @return The field value or null if not found
     */
    private Long getLongField(SolrDocument doc, String fieldName) {
        Object value = doc.getFieldValue(fieldName);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            log.trace("Field '{}' in doc id '{}' is Number but not Long, converting.", fieldName,
                    doc.getFieldValue(FIELD_ID));
            return ((Number) value).longValue();
        } else if (value != null) {
            log.warn("Unexpected type for long field '{}' in doc id '{}': {}. Returning null.",
                    fieldName, doc.getFieldValue(FIELD_ID), value.getClass().getName());
        }
        return null;
    }

    /**
     * Handles the response from a Solr update operation.
     * 
     * @param response             The update response
     * @param operationDescription A description of the operation
     * @throws SolrServerException If the update operation failed
     */
    private void handleUpdateResponse(UpdateResponse response, String operationDescription) throws SolrServerException {
        int status = response.getStatus();
        long qTime = response.getQTime();
        if (status == 0) {
            log.debug("Solr update operation '{}' successful. QTime: {}ms", operationDescription, qTime);
        } else {
            log.warn("Solr update operation '{}' completed with status code: {}. QTime: {}ms. Check Solr logs.",
                    operationDescription, status, qTime);
            // Uncomment to fail on non-zero status
            // throw new SolrServerException("Solr update failed for '" +
            // operationDescription + "' with status: " + status);
        }
    }

    /**
     * Gets the URL of the currently connected Solr server
     * 
     * @return The current Solr URL or null if not connected
     */
    public String getCurrentSolrUrl() {
        if (connected && solrClient != null) {
            return solrClient.getBaseURL();
        }
        return null;
    }

    /**
     * Sets the maximum number of retries for operations
     * 
     * @param maxRetries The maximum number of retries
     */
    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }
        this.maxRetries = maxRetries;
    }

    /**
     * Removes all files belonging to a specific user from the Solr index.
     * Should be called when a user goes offline or leaves the network.
     * 
     * @param username The username of the user whose files should be removed
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     * @return The number of files removed
     */
    public int removeUserFiles(String username) throws SolrServerException, IOException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty when removing user files.");
        }

        log.info("Removing all files for user '{}' from the index", username);

        return executeWithRetryAndFailover(() -> {
            // First, get all file IDs for this user
            SolrQuery query = new SolrQuery();
            query.setQuery(String.format("%s:%s AND %s:%s",
                    FIELD_DOC_TYPE, DOC_TYPE_FILE,
                    FIELD_OWNER_USERNAME, ClientUtils.escapeQueryChars(username)));
            query.setRows(Integer.MAX_VALUE);
            query.setFields(FIELD_ID);

            QueryResponse response = solrClient.query(query);
            SolrDocumentList docList = response.getResults();

            if (docList != null && !docList.isEmpty()) {
                List<String> fileIds = new ArrayList<>(docList.size());
                for (SolrDocument doc : docList) {
                    String id = (String) doc.getFieldValue(FIELD_ID);
                    if (id != null) {
                        fileIds.add(id);
                    }
                }

                if (!fileIds.isEmpty()) {
                    log.info("Deleting {} files owned by user '{}'", fileIds.size(), username);
                    UpdateResponse deleteResponse = solrClient.deleteById(fileIds, 10000);
                    handleUpdateResponse(deleteResponse, "remove all files for user " + username);

                    // Commit to make changes visible immediately
                    UpdateResponse commitResponse = solrClient.commit();
                    handleUpdateResponse(commitResponse, "commit file deletion for user " + username);
                    return fileIds.size();
                }
            }

            log.info("No files found to remove for user '{}'", username);
            return 0;
        }, "removeUserFiles(" + username + ")");
    }

    /**
     * Performs a full cleanup for a user leaving the network.
     * Sets the user status to offline and removes all their shared files.
     * 
     * @param username The username of the departing user
     * @throws SolrServerException If a Solr-specific error occurs
     * @throws IOException         If an I/O error occurs
     * @return A summary of cleanup actions performed
     */
    public String cleanupUserOnExit(String username) throws SolrServerException, IOException {
        log.info("Performing full cleanup for user '{}' leaving the network", username);
        StringBuilder summary = new StringBuilder();

        try {
            // First set user as offline
            setUserStatus(username, false);
            summary.append("User status set to offline. ");

            // Then remove all their files
            int filesRemoved = removeUserFiles(username);
            summary.append(filesRemoved).append(" files removed from index. ");

            // Finally commit changes to make them immediately visible
            executeWithRetryAndFailover(() -> {
                UpdateResponse commitResponse = solrClient.commit();
                handleUpdateResponse(commitResponse, "commit changes for user " + username + " exit");
                return null;
            }, "commitUserExitChanges(" + username + ")");
            summary.append("Changes committed successfully.");

            log.info("Successfully completed cleanup for user '{}': {}", username, summary);
            return summary.toString();
        } catch (Exception e) {
            String errorMsg = "Error during cleanup for user '" + username + "': " + e.getMessage();
            log.error(errorMsg, e);
            summary.append("FAILED: ").append(errorMsg);
            throw e;
        }
    }

}