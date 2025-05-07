package com.mycompany.napsterclone.ui;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

// --- NOT CURRENTLY USED --- Example of rendering progress in a table cell
public class TransferProgressRenderer extends JProgressBar implements TableCellRenderer {

    public TransferProgressRenderer() {
        super(0, 100);
        setStringPainted(true);
        // Customize appearance if needed
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus,
            int row, int column) {
        if (value instanceof Integer) {
            int progress = (Integer) value;
            setValue(progress);
            setString(progress + "%");
        } else if (value instanceof String) {
            // Handle states like "Pending", "Complete", "Error"
            setIndeterminate(false);
            setValue(0); // Or 100 for complete
            setString((String) value);
            // Maybe change color based on state
            if ("Error".equalsIgnoreCase((String) value)) {
                setForeground(Color.RED);
            } else if ("Complete".equalsIgnoreCase((String) value)) {
                setForeground(Color.GREEN);
            } else {
                setForeground(UIManager.getColor("ProgressBar.foreground"));
            }

        } else {
            setValue(0);
            setString("N/A");
        }

        // Handle selection background/foreground if needed
        // if (isSelected) { ... }

        return this;
    }
}