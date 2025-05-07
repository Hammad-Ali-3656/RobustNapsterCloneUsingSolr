package com.mycompany.napsterclone.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Client for downloading files from other peers in the Napster Clone network.
 * Enhanced with better error handling and timeouts that match your existing
 * FileServer configuration.
 */
public class FileClient {
    private static final Logger log = LoggerFactory.getLogger(FileClient.class);

    // These settings align with your server implementation for optimal
    // compatibility
    private static final int CONNECT_TIMEOUT_MS = 10000; // 10 seconds to connect
    private static final int SOCKET_TIMEOUT_MS = 30000; // 30 seconds socket read timeout
    private static final int BUFFER_SIZE = 8192; // 8KB buffer - matching your server

    private final ExecutorService downloadExecutor;

    public FileClient() {
        // Create thread pool for handling multiple concurrent downloads
        this.downloadExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setName("FileClient-Download-" + t.getId());
            return t;
        });
        log.info("FileClient initialized with {} download threads", 3);
    }

    /**
     * Downloads a file from a peer.
     * 
     * @param peerIp       The IP address of the peer
     * @param peerPort     The port of the peer
     * @param filename     The name of the file to download
     * @param saveToPath   Path where the downloaded file should be saved
     * @param expectedSize Expected file size in bytes (for progress calculation)
     * @param listener     Listener to report progress, completion, or errors
     */
    public void downloadFile(String peerIp, int peerPort, String filename, Path saveToPath,
            long expectedSize, ProgressListener listener) {

        downloadExecutor.submit(() -> {
            log.info("Starting download of '{}' from {}:{} to {}", filename, peerIp, peerPort, saveToPath);

            // Create parent directories if needed
            try {
                Path parentDir = saveToPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
            } catch (IOException e) {
                String errorMsg = "Failed to create directory for download: " + e.getMessage();
                log.error(errorMsg);
                listener.onError(errorMsg);
                return;
            }

            // Attempt download (with auto-retry if needed)
            boolean success = false;
            Exception lastException = null;
            int retries = 2; // Try up to 3 times total (initial + 2 retries)

            for (int attempt = 0; attempt <= retries; attempt++) {
                if (attempt > 0) {
                    log.info("Retry attempt {} for file: {}", attempt, filename);
                    try {
                        Thread.sleep(1500); // Wait 1.5 seconds between retries
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                Socket socket = null;
                DataInputStream dataIn = null;
                DataOutputStream dataOut = null;
                FileOutputStream fileOut = null;
                BufferedOutputStream bufferedOut = null;

                try {
                    // Socket with explicit timeouts - note the increased values to prevent timeouts
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(peerIp, peerPort), CONNECT_TIMEOUT_MS);
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS); // Time to wait for data during read operations
                    socket.setReceiveBufferSize(BUFFER_SIZE * 2); // Double buffer size for socket

                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    dataIn = new DataInputStream(in);
                    dataOut = new DataOutputStream(out);

                    // Send file request
                    dataOut.writeUTF(filename);
                    dataOut.flush();

                    // Get server response
                    String response = dataIn.readUTF();
                    if (!"OK".equals(response)) {
                        log.warn("Server error response: {}", response);
                        listener.onError("Server error: " + response);
                        return; // Fatal error - no retry for file not found
                    }

                    // Read file size
                    long fileSize = dataIn.readLong();
                    log.debug("Server reports file size: {} bytes", fileSize);

                    // Create output file
                    fileOut = new FileOutputStream(saveToPath.toFile());
                    bufferedOut = new BufferedOutputStream(fileOut, BUFFER_SIZE * 2); // Double buffer for file output

                    // Progress tracking
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long totalRead = 0;
                    long lastProgressTime = System.currentTimeMillis();
                    int progressInterval = 250; // Report progress every 250ms max

                    // Stall detection
                    long lastStallCheckBytes = 0;
                    long lastStallCheckTime = System.currentTimeMillis();
                    long stallTimeoutMs = 10000; // 10 seconds without progress = stall

                    // Read data in chunks
                    while (totalRead < fileSize) {
                        try {
                            // Stall detection - check if we've made progress since last check
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastStallCheckTime > stallTimeoutMs &&
                                    totalRead == lastStallCheckBytes && totalRead > 0) {
                                throw new IOException("Download stalled - no progress for " +
                                        (stallTimeoutMs / 1000) + " seconds");
                            }

                            // Read a chunk of data with timeout protection
                            bytesRead = in.read(buffer);

                            if (bytesRead == -1) {
                                if (totalRead < fileSize) {
                                    throw new IOException("Unexpected end of stream after " +
                                            totalRead + " of " + fileSize + " bytes");
                                }
                                break; // We're done
                            }

                            // Write to file and update progress
                            bufferedOut.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;

                            // Update stall detection on actual progress
                            if (totalRead > lastStallCheckBytes) {
                                lastStallCheckBytes = totalRead;
                                lastStallCheckTime = currentTime;
                            }

                            // Report progress (but not too frequently)
                            if (currentTime - lastProgressTime > progressInterval || totalRead == fileSize) {
                                listener.onProgress(totalRead, fileSize);
                                lastProgressTime = currentTime;
                            }
                        } catch (IOException e) {
                            // Specifically handle timeout
                            if (e.getMessage() != null && e.getMessage().contains("timed out")) {
                                throw new IOException("Read timed out after " + totalRead +
                                        " of " + fileSize + " bytes. Server may have crashed.", e);
                            }
                            throw e;
                        }
                    }

                    // Flush and close the output
                    bufferedOut.flush();

                    // Success!
                    log.info("Download complete: {} - {} bytes", filename, totalRead);
                    listener.onComplete();
                    success = true;
                    return; // Exit the retry loop on success

                } catch (IOException e) {
                    lastException = e;
                    log.warn("Download attempt {} failed: {}", attempt + 1, e.getMessage());

                    // Only retry for specific errors that might resolve with retry
                    boolean shouldRetry = e instanceof java.net.SocketTimeoutException ||
                            (e.getMessage() != null &&
                                    (e.getMessage().contains("timed out") ||
                                            e.getMessage().contains("reset") ||
                                            e.getMessage().contains("closed") ||
                                            e.getMessage().contains("stalled")));

                    if (!shouldRetry || attempt >= retries) {
                        // This error won't benefit from retrying or we're out of retries
                        break;
                    }

                } finally {
                    // Clean up resources
                    closeQuietly(bufferedOut);
                    closeQuietly(fileOut);
                    closeQuietly(dataOut);
                    closeQuietly(dataIn);
                    closeQuietly(socket);
                }
            }

            // If we get here with success=false, all attempts failed
            if (!success) {
                String errorMsg = lastException != null ? lastException.getMessage() : "Unknown download error";
                listener.onError("Failed to write file to disk: " + errorMsg);

                // Clean up partial download
                try {
                    Files.deleteIfExists(saveToPath);
                } catch (IOException e) {
                    log.debug("Could not delete partial download: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Shuts down the download executor.
     */
    public void shutdown() {
        log.info("Shutting down FileClient");
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Download executor did not terminate in time");
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("FileClient shutdown interrupted");
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes a resource quietly without throwing exceptions.
     */
    private void closeQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                log.trace("Error closing resource: {}", e.getMessage());
            }
        }
    }

    /**
     * Closes a socket quietly without throwing exceptions.
     */
    private void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.trace("Error closing socket: {}", e.getMessage());
            }
        }
    }
}