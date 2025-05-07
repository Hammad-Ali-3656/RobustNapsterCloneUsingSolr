package com.mycompany.napsterclone.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FileServer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(FileServer.class);

    private final int port;
    private final Path publishDirectory;
    private final ExecutorService executorService;
    private volatile boolean running = false;
    private volatile boolean acceptingConnections = true; // Added for graceful shutdown

    public FileServer(int port, Path publishDirectory) {
        this.port = port;
        this.publishDirectory = publishDirectory;
        this.executorService = Executors.newFixedThreadPool(5); // Limit concurrent file transfers
        log.info("FileServer initialized with port {} and publish dir {}", port, publishDirectory);
    }

    /**
     * Sets whether the server should accept new incoming connections.
     * Useful for graceful shutdown - stop accepting new connections but
     * allow existing transfers to complete.
     *
     * @param accepting true to accept new connections, false to reject them
     */
    public void setAcceptingConnections(boolean accepting) {
        this.acceptingConnections = accepting;
        log.info("File server now {} new connections", accepting ? "accepting" : "rejecting");
    }

    @Override
    public void run() {
        running = true;
        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(port);
            log.info("FileServer started on port {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    if (!acceptingConnections) {
                        // Reject new connections during shutdown
                        log.info("Rejecting connection from {} - server is shutting down",
                                clientSocket.getInetAddress().getHostAddress());
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            log.error("Error closing rejected connection: {}", e.getMessage());
                        }
                        continue;
                    }

                    // Handle client connection in thread pool
                    executorService.submit(() -> handleClient(clientSocket));

                } catch (IOException e) {
                    if (running) {
                        log.error("Error accepting client connection: {}", e.getMessage(), e);
                    } else {
                        log.debug("ServerSocket closed during shutdown");
                    }
                }
            }
        } catch (IOException e) {
            log.error("FileServer failed to start: {}", e.getMessage(), e);
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    log.error("Error closing server socket: {}", e.getMessage(), e);
                }
            }
            log.info("FileServer stopped");
        }
    }

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
            log.warn("FileServer shutdown interrupted: {}", e.getMessage());
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void handleClient(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        log.info("Client connected from {}", clientAddress);

        try (InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream();
                DataInputStream dataIn = new DataInputStream(in);
                DataOutputStream dataOut = new DataOutputStream(out)) {

            // Read request - the filename to serve
            String requestedFile = dataIn.readUTF();
            log.debug("Client requested file: {}", requestedFile);

            // Security check - prevent path traversal attacks
            if (requestedFile.contains("..")) {
                log.warn("Path traversal attempt detected from {}: {}", clientAddress, requestedFile);
                dataOut.writeUTF("ERROR: Invalid filename");
                return;
            }

            // Resolve the file path within the publish directory
            Path filePath = publishDirectory.resolve(requestedFile).normalize();

            // Ensure the resolved path is still within the publish directory
            if (!filePath.startsWith(publishDirectory)) {
                log.warn("Path traversal attempt detected from {}: {}", clientAddress, requestedFile);
                dataOut.writeUTF("ERROR: Invalid filename");
                return;
            }

            File file = filePath.toFile();

            if (!file.exists() || !file.isFile() || !file.canRead()) {
                log.warn("File not found or not readable: {}", filePath);
                dataOut.writeUTF("ERROR: File not found or not readable");
                return;
            }

            long fileSize = file.length();
            log.info("Sending file {} to client {} (size: {} bytes)", requestedFile, clientAddress, fileSize);

            // Send success indicator and file size
            dataOut.writeUTF("OK");
            dataOut.writeLong(fileSize);

            // Send the file
            try (FileInputStream fileIn = new FileInputStream(file)) {
                byte[] buffer = new byte[8192]; // 8KB buffer
                int bytesRead;
                long totalSent = 0;
                long lastProgressLog = 0;

                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;

                    // Log progress every 5MB
                    if (totalSent - lastProgressLog > 5 * 1024 * 1024) {
                        log.debug("Progress sending {} to {}: {} of {} bytes ({}%)",
                                requestedFile, clientAddress, totalSent, fileSize,
                                (int) ((totalSent * 100) / fileSize));
                        lastProgressLog = totalSent;
                    }
                }

                out.flush();
                log.info("Completed sending file {} to client {} ({} bytes)",
                        requestedFile, clientAddress, totalSent);
            }

        } catch (IOException e) {
            log.error("Error handling client {}: {}", clientAddress, e.getMessage(), e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                log.warn("Error closing client socket: {}", e.getMessage());
            }
        }
    }
}