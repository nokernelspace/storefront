package io.github.mimerme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {
    public static void touch(String path) {
        Path filePath = Paths.get(path);
        // Or relative path: Paths.get("data/output/myfile.txt");

        try {
            // Step 1: Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                System.out.println("Directories created successfully.");
            }

            // Step 2: Create the file if it doesn't exist
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
                System.out.println("File created successfully: " + filePath);
            } else {
                // System.out.println("File already exists.");
            }

        } catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
        }

    }


    public static String cwd() {
        return System.getProperty("user.dir");
    }
}
