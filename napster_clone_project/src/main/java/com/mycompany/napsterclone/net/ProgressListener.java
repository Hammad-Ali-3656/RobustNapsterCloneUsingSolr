package com.mycompany.napsterclone.net;

public interface ProgressListener {
    /**
     * Called periodically to update the progress of a transfer.
     *
     * @param bytesTransferred The total number of bytes transferred so far.
     * @param totalSize        The total expected size of the transfer in bytes.
     *                         May be 0 or -1 if the total size is unknown.
     */
    default void onProgress(long bytesTransferred, long totalSize) {
    }

    /**
     * Called when the transfer completes successfully.
     */
    default void onComplete() {
    }

    /**
     * Called if an error occurs during the transfer.
     *
     * @param message A message describing the error.
     */
    default void onError(String message) {
    }
}