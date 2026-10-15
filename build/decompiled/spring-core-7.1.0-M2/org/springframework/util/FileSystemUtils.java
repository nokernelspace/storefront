package org.springframework.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class FileSystemUtils {
   @Contract("null -> false")
   public static boolean deleteRecursively(@Nullable File root) {
      if (root == null) {
         return false;
      }

      try {
         return deleteRecursively(root.toPath());
      } catch (IOException ex) {
         return false;
      }
   }

   @Contract("null -> false")
   public static boolean deleteRecursively(@Nullable Path root) throws IOException {
      if (root == null) {
         return false;
      }

      if (!Files.exists(root)) {
         return false;
      }

      Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
         public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
         }
      });
      return true;
   }

   public static void copyRecursively(File src, File dest) throws IOException {
      Assert.notNull(src, "Source File must not be null");
      Assert.notNull(dest, "Destination File must not be null");
      copyRecursively(src.toPath(), dest.toPath());
   }

   public static void copyRecursively(Path src, Path dest) throws IOException {
      Assert.notNull(src, "Source Path must not be null");
      Assert.notNull(dest, "Destination Path must not be null");
      BasicFileAttributes srcAttr = Files.readAttributes(src, BasicFileAttributes.class);
      if (srcAttr.isDirectory()) {
         if (src.getClass() == dest.getClass()) {
            Files.walkFileTree(src, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
               public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) throws IOException {
                  Files.createDirectories(dest.resolve(src.relativize(dir)));
                  return FileVisitResult.CONTINUE;
               }

               public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
                  Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                  return FileVisitResult.CONTINUE;
               }
            });
         } else {
            Files.walkFileTree(src, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
               public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) throws IOException {
                  Files.createDirectories(dest.resolve(src.relativize(dir).toString()));
                  return FileVisitResult.CONTINUE;
               }

               public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
                  Files.copy(file, dest.resolve(src.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                  return FileVisitResult.CONTINUE;
               }
            });
         }
      } else {
         if (!srcAttr.isRegularFile()) {
            throw new IllegalArgumentException("Source File must denote a directory or file");
         }

         Files.copy(src, dest);
      }
   }
}
