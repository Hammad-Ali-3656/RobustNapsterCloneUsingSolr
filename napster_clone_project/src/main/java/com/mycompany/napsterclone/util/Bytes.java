package com.mycompany.napsterclone.util;

import java.text.DecimalFormat;

public class Bytes {

    private static final long KILO = 1024;
    private static final long MEGA = KILO * 1024;
    private static final long GIGA = MEGA * 1024;
    private static final long TERA = GIGA * 1024;

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");
    private static final DecimalFormat RATE_FORMAT = new DecimalFormat("#,##0.#");

    /**
     * Formats a byte count into a human-readable string (e.g., "1.2 MB").
     *
     * @param bytes The number of bytes.
     * @return A human-readable string representation.
     */
    public static String format(long bytes) {
        if (bytes < KILO) {
            return bytes + " B";
        } else if (bytes < MEGA) {
            return FORMAT.format((double) bytes / KILO) + " KB";
        } else if (bytes < GIGA) {
            return FORMAT.format((double) bytes / MEGA) + " MB";
        } else if (bytes < TERA) {
            return FORMAT.format((double) bytes / GIGA) + " GB";
        } else {
            return FORMAT.format((double) bytes / TERA) + " TB";
        }
    }

    /**
     * Formats a transfer rate (bytes per second) into a human-readable string
     * (e.g., "150.5 KB/s").
     *
     * @param bytesPerSecond The transfer rate in bytes per second.
     * @return A human-readable string representation of the rate.
     */
    public static String formatRate(double bytesPerSecond) {
        if (bytesPerSecond < 0)
            return "0 B/s"; // Handle negative input if necessary

        if (bytesPerSecond < KILO) {
            return RATE_FORMAT.format(bytesPerSecond) + " B/s";
        } else if (bytesPerSecond < MEGA) {
            return RATE_FORMAT.format(bytesPerSecond / KILO) + " KB/s";
        } else if (bytesPerSecond < GIGA) {
            return RATE_FORMAT.format(bytesPerSecond / MEGA) + " MB/s";
        } else {
            return RATE_FORMAT.format(bytesPerSecond / GIGA) + " GB/s";
        }
    }
}