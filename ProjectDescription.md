# Parallel and Distributed Computing Concepts in Napster Clone

## Overview of Implemented Concepts

This document provides a detailed analysis of the parallel and distributed computing concepts implemented in the Napster Clone project, with specific code references and explanations.

## 1. Hybrid P2P Architecture

The application implements a hybrid P2P architecture that combines elements of both centralized and decentralized systems.

### Centralized Components:

- **File Indexing Service**: `SolrClientManager.java` manages communication with Apache Solr for centralized indexing.

```java
// SolrClientManager.java
public List<FileMetaData> searchFiles(String query, String excludeUsername) throws SolrServerException, IOException {
    // Centralizes search through the Solr index
    SolrQuery solrQuery = new SolrQuery();
    solrQuery.setQuery(queryStr);
    QueryResponse response = solrClient.query(solrQuery);
    // ...
}
```

### Decentralized Components:

- **File Transfer**: The `FileServer.java` and `FileClient.java` enable direct peer-to-peer file transfers without central server intervention.

```java
// FileServer.java
private void handleClient(Socket clientSocket) {
    // Direct file transfer between peers
    // Server reads request and sends file directly to requesting peer
    // ...
}
```

### Distributed Architecture Benefits:

- **Scalability**: Central index scales query operations while distributed transfers prevent bottlenecks
- **Fault Tolerance**: System continues operating if some peers disconnect
- **Load Distribution**: Transfer load is distributed across all peers

## 2. Process & Thread Parallelism

### Thread Pools for Concurrent Operations:

- **Server Thread Pool**: `FileServer.java` uses an ExecutorService to handle multiple concurrent download requests.

```java
// FileServer.java
public FileServer(int port, Path publishDirectory) {
    // Thread pool for parallel handling of file requests
    this.executorService = Executors.newFixedThreadPool(5);
}

// Inside run() method:
executorService.submit(() -> handleClient(clientSocket));
```

- **Client Thread Pool**: `FileClient.java` implements thread pools for concurrent downloads.

```java
// FileClient.java
public FileClient() {
    // Multiple download threads can run in parallel
    this.downloadExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r);
        t.setName("FileClient-Download-" + t.getId());
        return t;
    });
}
```

### Asynchronous Processing:

- **SwingWorker for UI Updates**: `MainForm.java` uses SwingWorker for non-blocking UI operations.

```java
// MainForm.java - performSearch method
SwingWorker<List<FileMetaData>, Void> worker = new SwingWorker<>() {
    @Override
    protected List<FileMetaData> doInBackground() throws Exception {
        // Search operation runs on background thread
        return solrManager.searchFiles(query, clientManager.getUsername());
    }

    @Override
    protected void done() {
        // UI updates on EDT thread
    }
};
worker.execute();
```

- **Shutdown Processing**: The application uses background threads for cleanup.

```java
// MainForm.java - shutdownApplication method
SwingWorker<Void, String> shutdownWorker = new SwingWorker<>() {
    @Override
    protected Void doInBackground() throws Exception {
        // Cleanup operations run in background
    }
};
```

## 3. Distributed Resource Management

### Dynamic Resource Discovery:

- **User and File Discovery**: `SolrClientManager.java` implements methods for finding users and their files.

```java
// SolrClientManager.java
public List<FileMetaData> searchFiles(String query, String excludeUsername) {
    // Dynamic discovery of files across the network
}

public void setUserStatus(String username, boolean online) {
    // Update and track user status in the distributed system
}
```

### Resource Allocation:

- **File Serving**: Each peer allocates its resources to serve files.

```java
// FileServer.java
private void handleClient(Socket clientSocket) {
    // Local resource allocation - bandwidth and CPU for serving files
    // ...
}
```

### Naming Service:

- **Resource Identification**: The system uses unique identifiers for files and users.

```java
// SolrClientManager.java - addFile method
String uniqueId = Hashing.sha256()
    .hashString(ownerUsername + ":" + filename, StandardCharsets.UTF_8)
    .toString();
// Used for distributed identification of resources
```

## 4. Concurrency Control & Synchronization

### Thread-safe Collections:

- **Active Transfers Tracking**: Uses ConcurrentHashMap for thread-safe access.

```java
// MainForm.java
private final Map<String, TransferProgressUI> activeTransfers = new ConcurrentHashMap<>();
```

### Atomic Operations:

- **Flag Operations**: Uses AtomicBoolean and volatile variables for thread-safe state changes.

```java
// FileServer.java
private volatile boolean running = false;
private volatile boolean acceptingConnections = true;
```

```java
// FileClient.java
private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

// In downloadFile method
if (isShuttingDown.getAndSet(true)) { // Atomic check and set
    return;
}
```

### Synchronized Operations:

- **Resource Cleanup**: The SolrClientManager ensures synchronized cleanup.

```java
// SolrClientManager.java
public String cleanupUserOnExit(String username) throws SolrServerException, IOException {
    // Synchronized operation for cleaning up user resources
    setUserStatus(username, false);
    int filesRemoved = removeUserFiles(username);
    // ...
}
```

## 5. Fault Tolerance & Recovery

### Retry Mechanisms:

- **Download Retries**: The FileClient implements automatic retry for failed downloads.

```java
// FileClient.java
for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    if (attempt > 0) {
        log.info("Retry attempt {} for file: {}", attempt, filename);
        Thread.sleep(RETRY_DELAY_MS);
    }

    try {
        success = doDownload(/*...*/);
        if (success) break;
    } catch (Exception e) {
        // Retry logic
    }
}
```

### Error Handling:

- **Graceful Error Recovery**: The system implements exception handling across components.

```java
// SolrClientManager.java - executeWithRetryAndFailover
private <T> T executeWithRetryAndFailover(SolrOperation<T> operation, String operationName) {
    Exception lastException = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return operation.execute();
        } catch (Exception e) {
            // Handle failure and retry
            lastException = e;
        }
    }
    // Handle complete failure
}
```

### Timeout Management:

- **Stall Detection**: The FileClient implements stall detection to recover from frozen transfers.

```java
// FileClient.java
// Stall detection
long lastStallCheckBytes = 0;
long lastStallCheckTime = System.currentTimeMillis();

// In the download loop
if (currentTime - lastStallCheckTime > stallTimeoutMs &&
        totalRead == lastStallCheckBytes && totalRead > 0) {
    throw new IOException("Download stalled - no progress for " +
            (stallTimeoutMs / 1000) + " seconds");
}
```

### Socket Timeouts:

- **Connection Timeouts**: The system implements connection and read timeouts.

```java
// FileClient.java
Socket socket = new Socket();
socket.connect(new InetSocketAddress(peerIp, peerPort), CONNECT_TIMEOUT_MS);
socket.setSoTimeout(SOCKET_TIMEOUT_MS); // Time to wait for data during read operations
```

## 6. Distributed Communication

### Socket Communication:

- **Direct Peer Communication**: Peers communicate directly for file transfers.

```java
// FileClient.java - doDownload method
socket = new Socket();
socket.connect(new InetSocketAddress(peerIp, peerPort), CONNECT_TIMEOUT_MS);
// Communication protocol
dataOut.writeUTF(filename); // Request
response = dataIn.readUTF();  // Response
```

### HTTP Communication:

- **Index Communication**: The SolrClientManager uses HTTP for index operations.

```java
// SolrClientManager.java
public SolrClientManager(String solrUrl) {
    // HTTP-based client for Solr communication
    this.solrClient = new HttpSolrClient.Builder(solrUrl).build();
}
```

### Protocol Design:

- **Custom Protocol**: The file transfer uses a simple custom protocol.

```java
// FileServer.java - handleClient
// Protocol: 1) Client sends filename
String requestedFile = dataIn.readUTF();

// 2) Server sends status + size
dataOut.writeUTF("OK");
dataOut.writeLong(fileSize);

// 3) Server sends file bytes
// ...
```

## 7. Resource Cleanup & Graceful Exit

### Network Cleanup:

- **User Exit Protocol**: The system implements a cleanup protocol when users leave.

```java
// MainForm.java - shutdownApplication
publish("Removing all files from index...");
if (solrManager != null) {
    try {
        // Use the enhanced cleanup method that removes files first
        String result = solrManager.cleanupUserOnExit(username);
        publish("User cleanup complete: " + result);
    } catch (Exception e) {
        // Error handling
    }
}
```

### Resource Release:

- **File Server Shutdown**: The FileServer implements proper thread pool shutdown.

```java
// FileServer.java - shutdown method
public void shutdown() {
    log.info("FileServer shutdown requested");
    running = false;
    executorService.shutdown();
    try {
        if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
            log.warn("FileServer executor did not terminate in the specified time.");
            executorService.shutdownNow();
        }
    } catch (InterruptedException e) {
        // Handling interruption
    }
}
```

## 8. Performance Optimizations

### Buffered I/O:

- **Transfer Buffering**: Both client and server use buffered streams.

```java
// FileClient.java
bufferedOut = new BufferedOutputStream(fileOut, BUFFER_SIZE * 2);
```

### Progress Tracking:

- **Throttled Updates**: Progress updates are throttled to avoid UI congestion.

```java
// FileClient.java
if (currentTime - lastProgressTime > progressInterval || totalRead == fileSize) {
    listener.onProgress(totalRead, fileSize);
    lastProgressTime = currentTime;
}
```

### Socket Optimization:

- **Socket Configuration**: The FileClient implements TCP socket optimizations.

```java
// FileClient.java
socket.setTcpNoDelay(true); // Disable Nagle's algorithm
socket.setReceiveBufferSize(BUFFER_SIZE * 2); // Larger buffer
```

## 9. Distributed Memory Management

### File Caching:

- **Distributed File Storage**: Files are stored locally by each peer.

```java
// ClientManager.java
private Path publishDirectory; // Local file storage
```

### Metadata Synchronization:

- **Index Updates**: The SolrClientManager synchronizes metadata.

```java
// ClientManager.java - synchronizeFiles method
solrManager.addFile(file.getName(), file.length(), username, ip, port);
```

## 10. Distributed Event Processing

### Event Callbacks:

- **Progress Listener**: The system implements callbacks for transfer progress.

```java
// FileClient.java - downloadFile method
listener.onProgress(totalRead, fileSize);
listener.onComplete();
// or
listener.onError(errorMsg);
```

### Event Processing on UI:

- **Asynchronous Event Handling**: UI updates happen on the Event Dispatch Thread.

```java
// MainForm.java - TransferProgressUI class
SwingUtilities.invokeLater(() -> {
    progressUI.updateProgress(bytesTransferred, totalSize, finalSpeed);
});
```

## Summary of Parallel and Distributed Computing Concepts

The Napster Clone application effectively demonstrates:

1. **Hybrid P2P Architecture**: Combining centralized discovery with decentralized transfers
2. **Multithreading**: Parallel operations through thread pools and asynchronous processing
3. **Distributed Resource Management**: Dynamic discovery and allocation of distributed resources
4. **Concurrency Control**: Thread-safe collections and atomic operations
5. **Fault Tolerance**: Retry mechanisms and timeout management
6. **Distributed Communication**: Socket-based peer communication and HTTP for indexing
7. **Resource Coordination**: Cleanup protocols and graceful exit strategies
8. **Performance Optimizations**: Buffered I/O and socket optimizations
9. **Distributed Memory Management**: Local storage with metadata synchronization
10. **Event Processing**: Asynchronous callbacks and UI event handling

These implementations showcase a comprehensive application of parallel and distributed computing concepts to create a robust and efficient peer-to-peer file sharing system.
