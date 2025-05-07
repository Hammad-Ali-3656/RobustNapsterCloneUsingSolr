# Napster Clone: Peer-to-Peer File Sharing Application

## Project Overview

Napster Clone is a Java-based peer-to-peer (P2P) file sharing application that enables users to discover and download files from other peers on the network. It combines centralized indexing with direct peer-to-peer file transfers, inspired by the original Napster architecture.

## Features

- **File Discovery**: Search the network for files shared by other users
- **File Sharing**: Share your own files with other users on the network
- **Direct Download**: Download files directly from peers without server intervention
- **File Sync**: Synchronize your shared files with the central index
- **Transfer Monitoring**: Track download progress with real-time feedback
- **Graceful Disconnection**: Clean exit procedures ensure network integrity

## System Requirements

- Java 11 or higher
- Apache Solr server instance (7.7+ recommended)
- Network connectivity (peers must be able to connect to each other directly)
- 100 MB minimum disk space

## Getting Started

### Setting up Solr (One-time setup)

1. Download Apache Solr from https://solr.apache.org/downloads.html
2. Extract and start Solr: `bin/solr start`
3. Create the Napster Clone core: `bin/solr create -c napsterclone`
4. Upload the schema configuration (provided in `config/solr-schema.xml`)

### Running the Application

1. Ensure the Solr server is running
2. Run the application:
   ```
   java -jar napsterclone.jar
   ```
3. On first run, you'll be prompted to configure:
   - Your username
   - Directory for sharing files
   - Local server port (for receiving download requests)

## Usage Guide

### Sharing Files

1. Place files you want to share in your designated share directory
2. Click "Sync Files" to update the central index with your files

### Finding Files

1. Enter search terms in the search box
2. Click "Search" or press Enter
3. Results will display showing filename, size, and the owner's information

### Downloading Files

1. Select a file from the search results
2. Click "Download Selected"
3. Choose where to save the file
4. Monitor download progress in the transfers panel

### Exiting the Application

1. Close the application window or select File > Exit
2. The application will perform cleanup operations:
   - Remove your files from the index
   - Set your user status to offline
   - Complete any active transfers

## Architecture

This application demonstrates several key concepts in Parallel and Distributed Computing:

### 1. Hybrid P2P Architecture

The application uses a hybrid peer-to-peer architecture:
- **Centralized Indexing**: Uses Apache Solr for file indexing and discovery
- **Decentralized File Transfer**: Direct peer-to-peer file transfers

This approach provides the discoverability benefits of centralized systems with the scalability and efficiency of decentralized transfers.

### 2. Parallel Processing

Multiple parallel processing techniques are implemented:

- **Multithreaded File Transfers**: Each file transfer operates in its own thread
- **Thread Pools**: Both server and client use thread pools to manage concurrent operations efficiently
- **Asynchronous Operations**: UI operations and network operations run in separate threads

### 3. Distributed Resource Management

- **Dynamic Peer Discovery**: Users and their files are discovered at runtime
- **Decentralized Resource Allocation**: Each peer manages its own resources (files, bandwidth)
- **Distributed File Storage**: Files remain distributed across the network on individual peers

### 4. Fault Tolerance & Recovery

- **Automatic Retry**: Failed downloads are automatically retried
- **Stall Detection**: Detects and recovers from stalled transfers
- **Timeout Handling**: Configurable timeouts prevent indefinite blocking operations
- **Graceful Degradation**: If peers disconnect, the system continues operating

### 5. Distributed Synchronization

- **File Metadata Synchronization**: File information is synchronized with the central index
- **User Status Management**: Users' online/offline status is tracked
- **Network Cleanup**: When users exit, their resources are properly removed from the network

### 6. Concurrent Data Structures

- **Thread-safe Collections**: ConcurrentHashMap for tracking active transfers
- **Atomic Operations**: Ensuring thread safety during critical operations
- **Immutable Data Transfer**: FileMetaData objects are immutable for safe concurrent access

## Implementation Details

### Core Components

1. **SolrClientManager**: Handles communication with Apache Solr for indexing and searching
2. **FileServer**: Manages file sharing and incoming download requests
3. **FileClient**: Handles downloading files from other peers
4. **ClientManager**: Manages user status and shared files
5. **MainForm**: User interface for interaction with the system

### Communication Protocols

- **Solr HTTP API**: Used for index operations (REST/HTTP)
- **Socket Communication**: Used for direct file transfers between peers
- **DataStreams**: Java DataInputStream/DataOutputStream for structured data exchange

### Concurrency Model

- **Task-based Threading**: Each network operation runs as a separate task
- **Thread Pools**: Managed thread pools for both server and client operations
- **SwingWorker**: Used for background tasks that update the UI

## Technical Challenges & Solutions

### Challenge: Network Timeouts
**Solution**: Implemented configurable timeouts, automatic retries, and stall detection

### Challenge: Resource Cleanup on Exit
**Solution**: Developed a cleanup protocol to remove user files from index on application exit

### Challenge: File Transfer Reliability
**Solution**: Added buffered I/O, progress tracking, and error recovery mechanisms

### Challenge: Peer Discovery
**Solution**: Used a central index (Solr) for peer and file discovery while keeping transfers decentralized

## Future Enhancements

- **NAT Traversal**: Improve connectivity for users behind firewalls/NAT
- **Data Compression**: Compress files during transfer to improve performance
- **Content Verification**: Add checksums to verify file integrity
- **Distributed Index**: Move to a fully distributed index (DHT) for complete decentralization
- **Transfer Resumption**: Allow pausing and resuming downloads

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## Acknowledgments

- Original Napster concept by Shawn Fanning
- Apache Solr team for their powerful search platform
- Java community for robust concurrency and networking libraries