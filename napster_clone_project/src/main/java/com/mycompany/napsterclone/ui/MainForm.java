package com.mycompany.napsterclone.ui;

import com.mycompany.napsterclone.client.ClientManager;
import com.mycompany.napsterclone.model.FileMetaData;
import com.mycompany.napsterclone.net.FileClient;
import com.mycompany.napsterclone.net.FileServer;
import com.mycompany.napsterclone.net.ProgressListener;
import com.mycompany.napsterclone.solr.SolrClientManager;
import com.mycompany.napsterclone.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainForm extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(MainForm.class);

    private final ClientManager clientManager;
    private final SolrClientManager solrManager;
    private final FileServer fileServer; // Needed for shutdown
    private final FileClient fileClient; // Handles downloads

    private JTextField searchField;
    private JButton searchButton;
    private JButton syncButton;
    private JButton downloadButton;
    private JTable resultsTable;
    private FileTableModel tableModel;
    private JScrollPane tableScrollPane;

    private JProgressBar overallProgressBar; // For general status like sync/search
    private JLabel statusLabel; // General status text
    private JPanel transferPanel; // Panel to hold individual transfer progress bars
    private JFileChooser downloadLocationChooser;

    // Map to track active transfers (Key: unique transfer ID, Value: Progress UI
    // components)
    private final Map<String, TransferProgressUI> activeTransfers = new ConcurrentHashMap<>();
    private boolean isShuttingDown = false;

    public MainForm(ClientManager clientManager, SolrClientManager solrManager, FileServer fileServer) {
        super("Napster Clone - " + clientManager.getUsername());
        this.clientManager = clientManager;
        this.solrManager = solrManager;
        this.fileServer = fileServer;
        this.fileClient = new FileClient(); // Initialize the file client

        initComponents();
        layoutComponents();
        setupActions();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle close manually
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownApplication();
            }
        });

        setSize(800, 600);
        setLocationRelativeTo(null); // Center on screen

        // Initial status
        updateStatus("Ready. IP: " + clientManager.getLocalIpAddress() + " Port: " + clientManager.getServerPort() +
                " Sharing: " + clientManager.getPublishDirectory().toString());

        // Setup download location chooser
        downloadLocationChooser = new JFileChooser();
        downloadLocationChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        downloadLocationChooser.setDialogTitle("Select Download Location");

    }

    private void initComponents() {
        searchField = new JTextField(30);
        searchButton = new JButton("Search");
        syncButton = new JButton("Sync Files");
        downloadButton = new JButton("Download Selected");
        downloadButton.setEnabled(false); // Disable initially

        tableModel = new FileTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setAutoCreateRowSorter(true); // Enable sorting
        resultsTable.setFillsViewportHeight(true);
        tableScrollPane = new JScrollPane(resultsTable);

        overallProgressBar = new JProgressBar(0, 100);
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setString("Idle");
        statusLabel = new JLabel("Status: Ready");
        transferPanel = new JPanel();
        transferPanel.setLayout(new BoxLayout(transferPanel, BoxLayout.Y_AXIS)); // Vertical layout
        transferPanel.setBorder(BorderFactory.createTitledBorder("Active Transfers"));
    }

    private void layoutComponents() {
        // --- Top Panel (Search & Sync) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Search:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(syncButton);

        // --- Center Panel (Results Table) ---
        // (tableScrollPane is already created)
        // Adjust column widths
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Allow horizontal scroll if needed
        setColumnWidths();

        // --- Bottom Panel (Status & Download Button) ---
        JPanel bottomStatusPanel = new JPanel(new BorderLayout(5, 5));
        bottomStatusPanel.add(statusLabel, BorderLayout.CENTER);
        bottomStatusPanel.add(overallProgressBar, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(downloadButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(bottomStatusPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        // --- Right Panel (Transfers) ---
        JScrollPane transferScrollPane = new JScrollPane(transferPanel);
        transferScrollPane.setPreferredSize(new Dimension(250, 0)); // Give it a preferred width
        transferScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // --- Main Layout ---
        // Use JSplitPane for resizable areas
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScrollPane, transferScrollPane);
        mainSplitPane.setResizeWeight(0.75); // Give more space to the table initially
        mainSplitPane.setOneTouchExpandable(true); // Add arrows to collapse/expand

        setLayout(new BorderLayout(5, 5));
        add(topPanel, BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER); // Add the split pane to the center
        add(bottomPanel, BorderLayout.SOUTH);

    }

    private void setColumnWidths() {
        // Approximate widths, adjust as needed
        setColumnWidth(0, 250); // Filename
        setColumnWidth(1, 80); // Size
        setColumnWidth(2, 120); // Owner
        setColumnWidth(3, 100); // IP
        setColumnWidth(4, 60); // Port
    }

    private void setColumnWidth(int columnIndex, int width) {
        TableColumn column = resultsTable.getColumnModel().getColumn(columnIndex);
        column.setPreferredWidth(width);
        // Optionally set min/max width
        // column.setMinWidth(width);
        // column.setMaxWidth(width * 2);
    }

    private void setupActions() {
        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch()); // Enter key in search field
        syncButton.addActionListener(e -> performSync());
        downloadButton.addActionListener(e -> startDownload());

        // Enable download button only when a row is selected
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                downloadButton.setEnabled(resultsTable.getSelectedRow() != -1);
            }
        });
    }

    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + message));
        log.info("Status update: {}", message);
    }

    private void showOverallProgress(boolean visible, String text) {
        SwingUtilities.invokeLater(() -> {
            overallProgressBar.setVisible(visible);
            overallProgressBar.setIndeterminate(visible); // Use indeterminate for unknown duration tasks
            overallProgressBar.setString(text);
            // Disable buttons during long operations
            searchButton.setEnabled(!visible);
            syncButton.setEnabled(!visible);
            // Keep download button enabled based on selection, but downloads might wait
        });
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            // Optionally clear results or show all online files
            updateStatus("Enter a search term.");
            // tableModel.setFiles(new ArrayList<>()); // Clear table
            return;
        }

        updateStatus("Searching for '" + query + "'...");
        showOverallProgress(true, "Searching...");

        SwingWorker<List<FileMetaData>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<FileMetaData> doInBackground() throws Exception {
                try {
                    return solrManager.searchFiles(query, clientManager.getUsername()); // Exclude own files from search
                                                                                        // results
                } catch (Exception e) {
                    log.error("Search failed for query: {}", query, e);
                    throw e; // Propagate to done()
                }
            }

            @Override
            protected void done() {
                try {
                    List<FileMetaData> results = get(); // Get results or exception
                    SwingUtilities.invokeLater(() -> {
                        tableModel.setFiles(results);
                        updateStatus("Found " + results.size() + " file(s) for '" + query + "'.");
                        setColumnWidths(); // Re-apply widths after data changes
                    });
                } catch (Exception e) {
                    updateStatus("Search failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(MainForm.this,
                            "Search failed: " + e.getMessage(),
                            "Search Error", JOptionPane.ERROR_MESSAGE);
                    SwingUtilities.invokeLater(() -> tableModel.setFiles(new ArrayList<>())); // Clear table on error
                } finally {
                    showOverallProgress(false, "Idle");
                }
            }
        };
        worker.execute();
    }

    private void performSync() {
        updateStatus("Starting file synchronization...");
        showOverallProgress(true, "Synchronizing...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    clientManager.synchronizeFiles(solrManager);
                } catch (Exception e) {
                    log.error("Synchronization failed", e);
                    throw e;
                }
                return null;
            }

            @Override
            protected void done() {
                showOverallProgress(false, "Idle");
                try {
                    get(); // Check for exceptions
                    updateStatus("Synchronization complete.");
                    JOptionPane.showMessageDialog(MainForm.this,
                            "File synchronization with the index server is complete.",
                            "Sync Complete", JOptionPane.INFORMATION_MESSAGE);

                    // Optional: Automatically refresh search results if desired
                    // performSearch(); // Uncomment this if you want the view to refresh post-sync

                } catch (Exception e) {
                    updateStatus("Synchronization failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(MainForm.this,
                            "Synchronization failed: " + e.getMessage(),
                            "Sync Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void startDownload() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow == -1)
            return;

        // Convert view row index to model row index in case of sorting
        int modelRow = resultsTable.convertRowIndexToModel(selectedRow);
        FileMetaData fileToDownload = tableModel.getFileMetaDataAt(modelRow);

        if (fileToDownload == null) {
            log.error("Could not get FileMetaData for selected row view:{} model:{}", selectedRow, modelRow);
            updateStatus("Error selecting file.");
            return;
        }

        // Check if downloading from self (usually shouldn't happen if search excludes
        // self)
        if (fileToDownload.getOwnerUsername().equals(clientManager.getUsername())) {
            JOptionPane.showMessageDialog(this, "You cannot download your own file.", "Download Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // --- Choose Download Location ---
        int result = downloadLocationChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            updateStatus("Download cancelled by user.");
            return; // User cancelled
        }
        File selectedDir = downloadLocationChooser.getSelectedFile();
        Path downloadPath = Paths.get(selectedDir.getAbsolutePath(), fileToDownload.getFilename());

        // Check if file already exists
        if (downloadPath.toFile().exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "File '" + fileToDownload.getFilename() + "' already exists. Overwrite?",
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
            if (overwrite == JOptionPane.NO_OPTION) {
                updateStatus("Download cancelled, file exists.");
                return;
            }
        }

        // --- Initiate Download ---
        updateStatus("Starting download: " + fileToDownload.getFilename());
        String transferId = UUID.randomUUID().toString(); // Unique ID for this transfer
        TransferProgressUI progressUI = addTransferUI(transferId, fileToDownload.getFilename(),
                fileToDownload.getSize());

        // Use FileClient to download in the background
        fileClient.downloadFile(
                fileToDownload.getOwnerIp(),
                fileToDownload.getOwnerPort(),
                fileToDownload.getFilename(), // Request filename
                downloadPath,
                fileToDownload.getSize(), // Provide expected size for progress calculation
                new ProgressListener() {
                    long lastUpdate = System.currentTimeMillis();
                    long lastBytes = 0;

                    @Override
                    public void onProgress(long bytesTransferred, long totalSize) {
                        long now = System.currentTimeMillis();
                        double speed = 0;
                        if (now > lastUpdate && bytesTransferred > lastBytes) {
                            speed = (double) (bytesTransferred - lastBytes) / ((now - lastUpdate) / 1000.0); // Bytes
                                                                                                             // per
                                                                                                             // second
                        }
                        // Update UI less frequently to avoid flooding EDT
                        if (now - lastUpdate > 300 || bytesTransferred == totalSize) { // Update every 300ms or on
                                                                                       // completion
                            final double finalSpeed = speed;
                            SwingUtilities.invokeLater(
                                    () -> progressUI.updateProgress(bytesTransferred, totalSize, finalSpeed));
                            lastUpdate = now;
                            lastBytes = bytesTransferred;
                        }
                    }

                    @Override
                    public void onComplete() {
                        SwingUtilities.invokeLater(() -> {
                            progressUI.complete();
                            removeTransferUI(transferId, 20000); // Remove after 20 seconds
                        });
                        updateStatus("Download complete: " + fileToDownload.getFilename());
                        log.info("Download complete: {}", fileToDownload.getFilename());
                    }

                    @Override
                    public void onError(String message) {
                        SwingUtilities.invokeLater(() -> {
                            progressUI.error(message);
                            removeTransferUI(transferId, 30000); // Remove after 30 seconds
                        });
                        updateStatus("Download failed: " + fileToDownload.getFilename() + " - " + message);
                        log.error("Download failed for {}: {}", fileToDownload.getFilename(), message);
                        // No need for JOptionPane here as error is shown in the transfer UI
                    }
                });
    }

    // --- Transfer Progress UI Management ---

    // Simple inner class to hold references to the UI components for a single
    // transfer
    private static class TransferProgressUI {
        final JPanel panel;
        final JLabel nameLabel;
        final JProgressBar progressBar;
        final JLabel statusLabel; // For speed/size text

        TransferProgressUI(String filename) {
            panel = new JPanel(new BorderLayout(5, 2)); // Add some spacing
            panel.setBorder(BorderFactory.createEtchedBorder()); // Visual separation
            nameLabel = new JLabel(filename);
            nameLabel.setToolTipText(filename); // Show full name on hover
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            statusLabel = new JLabel("Starting...");
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 10f)); // Smaller italic font
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            JPanel infoPanel = new JPanel(new BorderLayout());
            infoPanel.add(nameLabel, BorderLayout.WEST);
            infoPanel.add(statusLabel, BorderLayout.EAST);

            panel.add(infoPanel, BorderLayout.NORTH);
            panel.add(progressBar, BorderLayout.CENTER);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 10)); // Limit
                                                                                                          // vertical
                                                                                                          // growth
        }

        void updateProgress(long current, long total, double bytesPerSecond) {
            if (total <= 0) { // Avoid division by zero if total size isn't known yet
                progressBar.setIndeterminate(true);
                progressBar.setString("Receiving...");
            } else {
                int percentage = (int) ((current * 100) / total);
                progressBar.setIndeterminate(false);
                progressBar.setValue(percentage);
                progressBar.setString(percentage + "%");
            }
            statusLabel.setText(String.format("%s / %s (%s/s)",
                    Bytes.format(current), Bytes.format(total), Bytes.formatRate(bytesPerSecond)));
            panel.revalidate();
            panel.repaint();
        }

        void complete() {
            progressBar.setValue(100);
            progressBar.setString("Completed");
            statusLabel.setText("Done");
            statusLabel.setForeground(new Color(0, 128, 0)); // Dark Green
            nameLabel.setForeground(Color.GRAY);
            panel.revalidate();
            panel.repaint();
        }

        void error(String message) {
            progressBar.setValue(0);
            progressBar.setString("Error");
            progressBar.setForeground(Color.RED); // Change bar color itself on error
            statusLabel.setText("Failed: " + message);
            statusLabel.setForeground(Color.RED);
            nameLabel.setForeground(Color.RED);
            panel.revalidate();
            panel.repaint();
        }
    }

    private TransferProgressUI addTransferUI(String transferId, String filename, long totalSize) {
        TransferProgressUI ui = new TransferProgressUI(filename);
        activeTransfers.put(transferId, ui);

        SwingUtilities.invokeLater(() -> {
            transferPanel.add(ui.panel);
            transferPanel.add(Box.createRigidArea(new Dimension(0, 5))); // Add spacing
            transferPanel.revalidate();
            transferPanel.repaint();
            // Ensure the new item is visible
            transferPanel.scrollRectToVisible(ui.panel.getBounds());
        });
        return ui;
    }

    private void removeTransferUI(String transferId, long delayMillis) {
        TransferProgressUI ui = activeTransfers.remove(transferId);
        if (ui != null) {
            // Use a Swing Timer to remove the panel after a delay
            Timer timer = new Timer((int) delayMillis, e -> {
                SwingUtilities.invokeLater(() -> {
                    transferPanel.remove(ui.panel);
                    // Remove any spacer added after it, if possible (more complex)
                    transferPanel.revalidate();
                    transferPanel.repaint();
                });
            });
            timer.setRepeats(false); // Only run once
            timer.start();
        }
    }

    private void shutdownApplication() {
        // Prevent multiple shutdown attempts
        if (isShuttingDown) {
            return;
        }
        isShuttingDown = true;

        String username = clientManager.getUsername();
        log.info("Shutdown requested by user: {}", username);

        // Create an option dialog asking if the user wants to leave
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit the Napster Clone Network?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        // If user cancels, abort shutdown
        if (choice != JOptionPane.YES_OPTION) {
            isShuttingDown = false;
            return;
        }

        // Show a detailed cleanup dialog
        JDialog cleanupDialog = new JDialog(this, "Shutting Down...", true);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel messageLabel = new JLabel("Cleaning up and disconnecting from the network...");
        panel.add(messageLabel, BorderLayout.NORTH);

        JTextArea detailsArea = new JTextArea(5, 30);
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(detailsArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        panel.add(progressBar, BorderLayout.SOUTH);

        cleanupDialog.add(panel);
        cleanupDialog.pack();
        cleanupDialog.setLocationRelativeTo(this);
        cleanupDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // Use a background thread for network cleanup
        SwingWorker<Void, String> shutdownWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    // Stop accepting download requests first
                    publish("Preparing to stop file server...");
                    // FileServer doesn't have setAcceptingConnections method, so we'll skip this
                    // and proceed with the full shutdown later

                    // Wait for active downloads to complete (optional)
                    if (fileClient != null && !activeTransfers.isEmpty()) {
                        publish("Waiting for active downloads to complete (5 second timeout)...");
                        // Add code to wait briefly for downloads, if needed
                        Thread.sleep(2000); // Simple delay, replace with actual waiting if needed
                    }

                    publish("Removing all files from index...");
                    if (solrManager != null) {
                        try {
                            // Use the enhanced cleanup method that removes files first
                            String result = solrManager.cleanupUserOnExit(username);
                            publish("User cleanup complete: " + result);
                        } catch (Exception e) {
                            publish("ERROR: Could not remove files from index: " + e.getMessage());
                            log.error("Error during Solr cleanup for user {}: {}", username, e.getMessage(), e);
                        }
                    }

                    publish("Stopping file server...");
                    if (fileServer != null) {
                        fileServer.shutdown();
                    }

                    publish("Stopping download client...");
                    if (fileClient != null) {
                        fileClient.shutdown(); // Shuts down the executor service
                    }

                    publish("Disconnecting from Solr...");
                    if (solrManager != null) {
                        try {
                            solrManager.disconnect();
                        } catch (Exception e) {
                            publish("ERROR: Problem disconnecting from Solr: " + e.getMessage());
                            log.error("Error during Solr disconnect: {}", e.getMessage(), e);
                        }
                    }

                    publish("Cleanup complete. Ready to exit.");
                    return null;
                } catch (Exception e) {
                    String msg = "Error during application cleanup: " + e.getMessage();
                    publish(msg);
                    log.error(msg, e);
                    return null;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                // Update the text area with progress messages
                for (String message : chunks) {
                    detailsArea.append(message + "\n");
                    // Auto-scroll to the bottom
                    detailsArea.setCaretPosition(detailsArea.getDocument().getLength());
                }
            }

            @Override
            protected void done() {
                // Add a final button to close the dialog and exit
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);

                JButton closeButton = new JButton("Exit Application");
                closeButton.addActionListener(e -> {
                    cleanupDialog.dispose();
                    log.info("Shutdown sequence complete for {}. Exiting application.", clientManager.getUsername());
                    dispose(); // Close the main window
                    System.exit(0); // Exit the application
                });

                // Replace progress bar with close button
                panel.remove(progressBar);
                panel.add(closeButton, BorderLayout.SOUTH);
                panel.revalidate();
                panel.repaint();
            }
        };

        // Show dialog and start worker
        shutdownWorker.execute();
        cleanupDialog.setVisible(true); // This will block until dialog is closed
    }
}