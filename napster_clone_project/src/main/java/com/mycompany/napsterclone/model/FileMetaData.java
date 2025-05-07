package com.mycompany.napsterclone.model;

import java.util.Objects;

public class FileMetaData {
    private String id; // Solr document ID (e.g., file-<owner>-<filename_hash>)
    private String filename;
    private long size;
    private String ownerUsername;
    private String ownerIp; // IP of the user hosting the file
    private int ownerPort; // Port of the user's FileServer

    /**
     * Full constructor.
     * 
     * @param id            Solr document ID
     * @param filename      Name of the file
     * @param size          Size of the file in bytes
     * @param ownerUsername Username of the file owner
     * @param ownerIp       IP address of the file owner
     * @param ownerPort     Port of the file owner's server
     */
    public FileMetaData(String id, String filename, long size, String ownerUsername, String ownerIp, int ownerPort) {
        // Basic validation
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty.");
        }
        if (ownerUsername == null || ownerUsername.trim().isEmpty()) {
            // Allow null owner for local scans before association, but Solr entries should
            // have it.
            // For simplicity here, let's assume it's generally required when fully
            // populated.
            // throw new IllegalArgumentException("Owner username cannot be null or
            // empty.");
        }
        if (size < 0) {
            throw new IllegalArgumentException("File size cannot be negative.");
        }
        if (ownerPort < 0 || ownerPort > 65535) { // 0 can be a valid placeholder if not yet known
            // throw new IllegalArgumentException("Owner port is out of valid range
            // (0-65535).");
        }

        this.id = id;
        this.filename = filename;
        this.size = size;
        this.ownerUsername = ownerUsername;
        this.ownerIp = ownerIp;
        this.ownerPort = ownerPort;
    }

    /**
     * Constructor for local file representation before Solr ID or full owner
     * details are known.
     * 
     * @param filename      Name of the file
     * @param size          Size of the file in bytes
     * @param ownerUsername Username of the file owner (current client)
     */
    public FileMetaData(String filename, long size, String ownerUsername) {
        this(null, filename, size, ownerUsername, null, 0); // ID, IP, Port are set later
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getOwnerIp() {
        return ownerIp;
    }

    public int getOwnerPort() {
        return ownerPort;
    }

    // Setters (use judiciously, prefer immutability where possible after creation)
    public void setId(String id) {
        this.id = id;
    }

    public void setOwnerIp(String ownerIp) {
        this.ownerIp = ownerIp;
    }

    public void setOwnerPort(int ownerPort) {
        this.ownerPort = ownerPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FileMetaData that = (FileMetaData) o;
        // For synchronization, primarily compare based on owner and filename.
        // Size could also be included if versioning or changes are important.
        return Objects.equals(ownerUsername, that.ownerUsername) &&
                Objects.equals(filename, that.filename);
        // If ID is always present and unique, comparing by ID might be simpler for some
        // cases:
        // return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        // Consistent with the equals method for sync purposes.
        return Objects.hash(ownerUsername, filename);
        // If using ID for equals: return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FileMetaData{" +
                "id='" + id + '\'' +
                ", filename='" + filename + '\'' +
                ", size=" + size +
                ", ownerUsername='" + ownerUsername + '\'' +
                ", ownerIp='" + ownerIp + '\'' +
                ", ownerPort=" + ownerPort +
                '}';
    }
}
