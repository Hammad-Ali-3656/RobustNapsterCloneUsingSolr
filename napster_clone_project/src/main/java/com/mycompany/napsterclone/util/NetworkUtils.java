package com.mycompany.napsterclone.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

public class NetworkUtils {

    private static final Logger log = LoggerFactory.getLogger(NetworkUtils.class);

    /**
     * Tries to find a non-loopback, non-virtual, site-local IP address for the host
     * machine.
     *
     * @return A string representation of the IP address.
     * @throws IOException If no suitable IP address is found.
     */
    public static String getLocalIpAddress() throws IOException {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                // Skip loopback, virtual, and down interfaces
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress addr = inetAddresses.nextElement();
                    // Look for IPv4 site-local addresses (e.g., 192.168.x.x, 10.x.x.x,
                    // 172.16-31.x.x)
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        log.debug("Found suitable local IP address: {} on interface {}", addr.getHostAddress(),
                                ni.getDisplayName());
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            log.error("SocketException while trying to get network interfaces", e);
            throw new IOException("Cannot determine local IP address due to SocketException", e);
        }
        // Fallback if no site-local found (might return loopback or public IP, less
        // ideal for P2P)
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            log.warn("No site-local IP found, falling back to InetAddress.getLocalHost(): {}",
                    localhost.getHostAddress());
            return localhost.getHostAddress();
        } catch (UnknownHostException e) {
            log.error("UnknownHostException while getting localhost address", e);
            throw new IOException("Cannot determine local IP address", e);
        }
    }

    /**
     * Checks if a specific TCP port is available (not in use).
     *
     * @param port The port number to check.
     * @return true if the port is likely available, false otherwise.
     */
    public static boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) {
            return false; // Invalid port range
        }
        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            // Check TCP port
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            // Check UDP port (less critical for this app, but good practice)
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true; // Both sockets opened successfully
        } catch (IOException e) {
            log.trace("Port {} is likely in use: {}", port, e.getMessage());
            return false; // Port is likely occupied
        } finally {
            // Ensure sockets are closed after checking
            if (ds != null) {
                ds.close();
            }
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }
    }

    /**
     * Finds the first available port starting from a given port number.
     *
     * @param startPort   The port number to start searching from.
     * @param maxAttempts The maximum number of ports to check.
     * @return An available port number.
     * @throws IOException If no available port is found within the attempts.
     */
    public static int findAvailablePort(int startPort, int maxAttempts) throws IOException {
        log.debug("Searching for available port starting from {} (max {} attempts)", startPort, maxAttempts);
        for (int port = startPort; port < startPort + maxAttempts; port++) {
            if (isPortAvailable(port)) {
                log.info("Found available port: {}", port);
                return port;
            }
        }
        throw new IOException(
                "Could not find an available port after " + maxAttempts + " attempts starting from " + startPort);
    }
}