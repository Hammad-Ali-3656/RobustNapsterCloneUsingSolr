package com.mycompany.napsterclone.ui;

import com.mycompany.napsterclone.client.ClientManager;
import com.mycompany.napsterclone.net.FileServer;
import com.mycompany.napsterclone.solr.SolrClientManager;
import com.mycompany.napsterclone.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File; // Keep this import for JFileChooser
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JoinForm extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(JoinForm.class);

    private JTextField usernameField;
    private JTextField publishDirField;
    private JTextField portField;
    private JButton browseButton;
    private JButton joinButton;
    private JLabel statusLabel;

    // --- Solr Configuration ---
    // Consider loading from a properties file or environment variables for
    // flexibility
    private static final String[] SOLR_URLS = {
            "http://localhost:8983/solr/napster_clone",
            "http://localhost:8984/solr/napster_clone"
    }; // Updated to use napster_clone cores on both Solr instances

    private static final int DEFAULT_SERVER_PORT = 6000; // Starting port for auto-detection
    private static final int MAX_PORT_SEARCH_ATTEMPTS = 100;

    public JoinForm() {
        super("Napster Clone - Join Network");
        initComponents();
        layoutComponents();
        setupActions();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        log.info("JoinForm initialized.");
    }

    private void initComponents() {
        usernameField = new JTextField(20);
        publishDirField = new JTextField(30);
        // Set a default directory for ease of testing (optional)
        // publishDirField.setText(System.getProperty("user.home") + File.separator +
        // "NapsterShare");
        portField = new JTextField(String.valueOf(DEFAULT_SERVER_PORT), 6);
        browseButton = new JButton("Browse...");
        joinButton = new JButton("Join Network");
        statusLabel = new JLabel("Enter details and click Join.", SwingConstants.CENTER);
        statusLabel.setForeground(Color.GRAY);
    }

    private void layoutComponents() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8); // Increased insets
        gbc.anchor = GridBagConstraints.WEST;

        // Username
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        usernameField.setToolTipText("Enter your desired username (alphanumeric, _, ., -)");
        panel.add(usernameField, gbc);

        // Publish Directory
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Share Folder:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        publishDirField.setToolTipText("Select the folder containing files you want to share.");
        panel.add(publishDirField, gbc);
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        panel.add(browseButton, gbc);

        // Server Port
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Server Port (0 for auto):"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        portField.setToolTipText("Port for your FileServer (0 to find automatically, or specify e.g., 6000-6005).");
        panel.add(portField, gbc);

        // Join Button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        joinButton.setFont(joinButton.getFont().deriveFont(Font.BOLD));
        panel.add(joinButton, gbc);

        // Status Label
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(statusLabel, gbc);

        add(panel, BorderLayout.CENTER);
        // Add some padding around the main panel
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void setupActions() {
        browseButton.addActionListener(e -> browseForDirectory());
        joinButton.addActionListener(e -> attemptJoin());
        usernameField.addActionListener(e -> attemptJoin());
        publishDirField.addActionListener(e -> attemptJoin());
        portField.addActionListener(e -> attemptJoin());
    }

    private void browseForDirectory() {
        JFileChooser chooser = new JFileChooser();
        // Try to set a sensible starting directory
        try {
            Path currentPath = Paths.get(publishDirField.getText());
            if (Files.isDirectory(currentPath)) {
                chooser.setCurrentDirectory(currentPath.toFile());
            } else {
                chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            }
        } catch (InvalidPathException | NullPointerException ex) {
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }

        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Directory to Share");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            publishDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            log.debug("User selected publish directory: {}", chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void attemptJoin() {
        String username = usernameField.getText().trim();
        String dirPathStr = publishDirField.getText().trim();
        String portStr = portField.getText().trim();

        if (!validateInputs(username, dirPathStr, portStr)) {
            return;
        }

        Path publishPath = Paths.get(dirPathStr); // Already validated
        int requestedPort = Integer.parseInt(portStr); // Already validated

        enableComponents(false);
        updateStatus("Attempting to join network...", Color.BLUE);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            private ClientManager clientManagerInstance;
            private SolrClientManager solrManagerInstance;
            private FileServer fileServerInstance;
            private String actualIp;
            private int actualPort;
            private boolean joinSuccess = false;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    publish("Initializing...");
                    solrManagerInstance = new SolrClientManager(SOLR_URLS);
                    publish("Connecting to index server...");
                    solrManagerInstance.connect();

                    clientManagerInstance = new ClientManager(username, publishPath);
                    actualIp = clientManagerInstance.getLocalIpAddress();

                    publish("Determining server port...");
                    if (requestedPort == 0) { // Auto-detect port
                        actualPort = NetworkUtils.findAvailablePort(DEFAULT_SERVER_PORT, MAX_PORT_SEARCH_ATTEMPTS);
                    } else { // User specified port
                        if (!NetworkUtils.isPortAvailable(requestedPort)) {
                            throw new IOException("Port " + requestedPort
                                    + " is already in use. Please choose another or use 0 for auto.");
                        }
                        actualPort = requestedPort;
                    }
                    clientManagerInstance.setServerPort(actualPort);
                    log.info("Resolved FileServer details - IP: {}, Port: {}", actualIp, actualPort);

                    publish("Starting file server on port " + actualPort + "...");
                    fileServerInstance = new FileServer(actualPort, clientManagerInstance.getPublishDirectory());
                    Thread serverThread = new Thread(fileServerInstance, "FileServerThread-" + actualPort);
                    serverThread.setDaemon(true); // Important for clean exit
                    serverThread.start();

                    // Brief pause to check if server started successfully
                    Thread.sleep(500); // Give server a moment to bind or fail
                    if (!fileServerInstance.isRunning()) {
                        throw new IOException("Failed to start File Server on port " + actualPort
                                + ". Check logs for details (e.g. port already in use).");
                    }
                    log.info("FileServer reported as running.");

                    publish("Registering with network...");
                    solrManagerInstance.registerUser(username, actualIp, actualPort);

                    publish("Performing initial file synchronization...");
                    clientManagerInstance.synchronizeFiles(solrManagerInstance);

                    joinSuccess = true;
                    publish("Successfully joined the network!");

                } catch (Exception e) {
                    log.error("Join process failed for user '{}'", username, e);
                    // Cleanup partially started components
                    if (fileServerInstance != null)
                        fileServerInstance.shutdown();
                    if (solrManagerInstance != null)
                        solrManagerInstance.disconnect();
                    throw e; // Propagate exception to done()
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                // Update status label on EDT with the latest message
                if (!chunks.isEmpty()) {
                    updateStatus(chunks.get(chunks.size() - 1), Color.BLUE);
                }
            }

            @Override
            protected void done() {
                try {
                    get(); // This will re-throw exceptions from doInBackground
                    if (joinSuccess) {
                        updateStatus("Join successful! Launching main application...", Color.GREEN.darker());
                        log.info("User '{}' joined successfully. IP: {}, Port: {}, Sharing: {}",
                                username, actualIp, actualPort, clientManagerInstance.getPublishDirectory());

                        // Launch MainForm
                        MainForm mainForm = new MainForm(clientManagerInstance, solrManagerInstance,
                                fileServerInstance);
                        mainForm.setVisible(true);
                        JoinForm.this.dispose(); // Close this JoinForm

                    } else {
                        // This case should ideally be covered by an exception being thrown
                        enableComponents(true);
                        updateStatus("Join failed. Please check details and logs.", Color.RED);
                    }
                } catch (Exception e) {
                    // Handle exceptions from doInBackground or get()
                    Throwable rootCause = e.getCause() != null ? e.getCause() : e;
                    log.error("Error during join completion for user '{}': {}", username, rootCause.getMessage(),
                            rootCause);
                    String errorMessage = "Join Failed: " + rootCause.getMessage();
                    // Provide more specific messages for common issues
                    if (rootCause instanceof java.net.ConnectException || (rootCause.getMessage() != null
                            && rootCause.getMessage().contains("Connection refused"))) {
                        errorMessage = "Join Failed: Could not connect to Solr index server. Is it running at "
                                + SOLR_URLS[0] + "?";
                    } else if (rootCause.getMessage() != null
                            && rootCause.getMessage().contains("port already in use")) {
                        errorMessage = "Join Failed: " + rootCause.getMessage()
                                + " Try a different port or use 0 for auto.";
                    }

                    showErrorDialog(errorMessage); // Use a helper for JOptionPane
                    enableComponents(true);
                    updateStatus("Join failed. See error dialog and logs.", Color.RED);
                }
            }
        };
        worker.execute();
    }

    private boolean validateInputs(String username, String dirPathStr, String portStr) {
        if (username.isEmpty() || !username.matches("^[a-zA-Z0-9_.-]+$") || username.length() > 50) {
            showErrorDialog("Invalid username. Use 1-50 alphanumeric characters, underscore, dot, or hyphen.");
            return false;
        }
        if (dirPathStr.isEmpty()) {
            showErrorDialog("Publish directory cannot be empty. Please select a folder.");
            return false;
        }
        try {
            Path publishPath = Paths.get(dirPathStr);
            if (!Files.isDirectory(publishPath)) {
                showErrorDialog("The selected publish path is not a valid directory: " + dirPathStr);
                return false;
            }
            if (!Files.exists(publishPath)) { // Check if directory exists
                // Optionally, offer to create it:
                // int choice = JOptionPane.showConfirmDialog(this, "Directory does not exist.
                // Create it?", "Create Directory", JOptionPane.YES_NO_OPTION);
                // if (choice == JOptionPane.YES_OPTION) { try {
                // Files.createDirectories(publishPath); } catch (IOException ex) {
                // showErrorDialog("Could not create directory: " + ex.getMessage()); return
                // false; }}
                // else { return false; }
                showErrorDialog("Selected publish directory does not exist: " + dirPathStr);
                return false;
            }
        } catch (InvalidPathException ipe) {
            showErrorDialog("Invalid publish directory path format: " + ipe.getMessage());
            return false;
        }
        try {
            int portNum = Integer.parseInt(portStr);
            if (portNum < 0 || portNum > 65535) {
                showErrorDialog("Invalid port number. Must be between 0 (for auto) and 65535.");
                return false;
            }
        } catch (NumberFormatException nfe) {
            showErrorDialog("Port must be a numeric value.");
            return false;
        }
        return true;
    }

    private void enableComponents(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            usernameField.setEnabled(enabled);
            publishDirField.setEnabled(enabled);
            portField.setEnabled(enabled);
            browseButton.setEnabled(enabled);
            joinButton.setEnabled(enabled);
        });
    }

    private void updateStatus(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        });
    }

    private void showErrorDialog(String message) {
        log.warn("Input/Configuration Error: {}", message);
        // Ensure JOptionPane is called on the EDT
        SwingUtilities.invokeLater(
                () -> JOptionPane.showMessageDialog(this, message, "Input Error", JOptionPane.ERROR_MESSAGE));
    }
}