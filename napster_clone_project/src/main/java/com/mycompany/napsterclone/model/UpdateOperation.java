package com.mycompany.napsterclone.model;

public class UpdateOperation {
    public enum Type {
        ADD, REMOVE
    }

    private final Type type;
    private final FileMetaData fileMetaData;

    public UpdateOperation(Type type, FileMetaData fileMetaData) {
        this.type = type;
        this.fileMetaData = fileMetaData;
    }

    public Type getType() {
        return type;
    }

    public FileMetaData getFileMetaData() {
        return fileMetaData;
    }

    @Override
    public String toString() {
        return "UpdateOperation{" +
                "type=" + type +
                ", fileMetaData=" + fileMetaData.getFilename() +
                '}';
    }
}