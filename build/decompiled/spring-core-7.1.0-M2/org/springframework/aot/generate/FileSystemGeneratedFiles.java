package org.springframework.aot.generate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.Assert;
import org.springframework.util.function.ThrowingConsumer;

public class FileSystemGeneratedFiles implements GeneratedFiles {
   private final Function<GeneratedFiles.Kind, Path> roots;

   public FileSystemGeneratedFiles(Path root) {
      this(conventionRoots(root));
   }

   public FileSystemGeneratedFiles(Function<GeneratedFiles.Kind, Path> roots) {
      Assert.notNull(roots, "'roots' must not be null");
      Assert.isTrue(Arrays.stream(GeneratedFiles.Kind.values()).map(roots).noneMatch(Objects::isNull), "'roots' must return a value for all file kinds");
      this.roots = roots;
   }

   private static Function<GeneratedFiles.Kind, Path> conventionRoots(Path root) {
      Assert.notNull(root, "'root' must not be null");
      return kind -> {
         return switch (kind) {
            case SOURCE -> root.resolve("sources");
            case RESOURCE -> root.resolve("resources");
            case CLASS -> root.resolve("classes");
         };
      };
   }

   @Override
   public void handleFile(GeneratedFiles.Kind kind, String path, ThrowingConsumer<GeneratedFiles.FileHandler> handler) {
      FileSystemGeneratedFiles.FileSystemFileHandler fileHandler = new FileSystemGeneratedFiles.FileSystemFileHandler(this.toPath(kind, path));
      handler.accept(fileHandler);
   }

   private Path toPath(GeneratedFiles.Kind kind, String path) {
      Assert.notNull(kind, "'kind' must not be null");
      Assert.hasLength(path, "'path' must not be empty");
      Path root = this.roots.apply(kind).toAbsolutePath().normalize();
      Path relativePath = root.resolve(path).toAbsolutePath().normalize();
      Assert.isTrue(relativePath.startsWith(root), "'path' must be relative");
      return relativePath;
   }

   static final class FileSystemFileHandler extends GeneratedFiles.FileHandler {
      private final Path path;

      FileSystemFileHandler(Path path) {
         super(Files.exists(path), () -> new FileSystemResource(path));
         this.path = path;
      }

      @Override
      protected void copy(InputStreamSource content, boolean override) {
         if (override) {
            this.copy(content, StandardCopyOption.REPLACE_EXISTING);
         } else {
            this.copy(content);
         }
      }

      private void copy(InputStreamSource content, CopyOption... copyOptions) {
         try (InputStream inputStream = content.getInputStream()) {
            Files.createDirectories(this.path.getParent());
            Files.copy(inputStream, this.path, copyOptions);
         } catch (IOException ex) {
            throw new IllegalStateException(ex);
         }
      }

      @Override
      public String toString() {
         return this.path.toString();
      }
   }
}
