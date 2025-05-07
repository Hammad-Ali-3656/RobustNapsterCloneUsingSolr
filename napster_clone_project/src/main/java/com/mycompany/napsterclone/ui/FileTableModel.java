package com.mycompany.napsterclone.ui;

import com.mycompany.napsterclone.model.FileMetaData;
import com.mycompany.napsterclone.util.Bytes;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class FileTableModel extends AbstractTableModel {

    private final String[] columnNames = { "Filename", "Size", "Owner", "IP Address", "Port" };
    private List<FileMetaData> files;

    public FileTableModel() {
        this.files = new ArrayList<>();
    }

    public void setFiles(List<FileMetaData> files) {
        this.files = new ArrayList<>(files); // Make a copy
        fireTableDataChanged(); // Notify the table view about the data change
    }

    public FileMetaData getFileMetaDataAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < files.size()) {
            return files.get(rowIndex);
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return files.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= files.size()) {
            return null; // Should not happen with proper checks
        }
        FileMetaData file = files.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return file.getFilename();
            case 1:
                return Bytes.format(file.getSize()); // Format size for display
            case 2:
                return file.getOwnerUsername();
            case 3:
                return file.getOwnerIp();
            case 4:
                return file.getOwnerPort();
            default:
                return null;
        }
    }

    // Optional: Specify column classes for sorting
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return String.class;
            case 1:
                return String.class; // Displayed as formatted string, sorting might be basic
            // If you store raw size and use a renderer, you could return Long.class for
            // better sorting
            case 2:
                return String.class;
            case 3:
                return String.class;
            case 4:
                return Integer.class;
            default:
                return Object.class;
        }
    }
}