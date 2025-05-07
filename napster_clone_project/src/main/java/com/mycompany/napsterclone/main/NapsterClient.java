package com.mycompany.napsterclone.main;

import com.mycompany.napsterclone.ui.JoinForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class NapsterClient {
    private static final Logger log = LoggerFactory.getLogger(NapsterClient.class);

    public static void main(String[] args) {
        setSystemLookAndFeel();

        // Schedule a job for the event-dispatching thread:
        // creating and showing this application's GUI.
        SwingUtilities.invokeLater(() -> {
            try {
                JoinForm joinForm = new JoinForm();
                joinForm.setVisible(true);
            } catch (Exception e) {
                log.error("Failed to launch JoinForm", e);
                JOptionPane.showMessageDialog(null,
                        "Application failed to start: " + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static void setSystemLookAndFeel() {
        try {
            // Try Nimbus first for a modern look
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    log.info("Using Nimbus Look and Feel");
                    return;
                }
            }
            // Fallback to system default
            String systemLookAndFeel = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(systemLookAndFeel);
            log.info("Using System Look and Feel: {}", systemLookAndFeel);
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            log.warn("Could not set preferred Look and Feel, using default.", e);
        }
    }
}