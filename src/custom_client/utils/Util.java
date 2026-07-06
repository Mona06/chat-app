package custom_client.utils;

import java.io.File;

public final class Util {
    public static String rootDirectory = System.getProperty("user.dir");

    // Method to format file size in a readable format
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }


    public static File searchFile(File directory, String fileName) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File found = searchFile(file, fileName);
                    if (found != null) return found;
                } else if (file.getName().equalsIgnoreCase(fileName)) {
                    return file;
                }
            }
        }
        return null;
    }
}
