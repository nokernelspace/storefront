package org.springframework.aot.generate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.Assert;
import org.springframework.util.function.ThrowingConsumer;

public class InMemoryGeneratedFiles implements GeneratedFiles {
   private final Map<GeneratedFiles.Kind, Map<String, InputStreamSource>> files = new HashMap<>();

   @Override
   public void handleFile(GeneratedFiles.Kind kind, String path, ThrowingConsumer<GeneratedFiles.FileHandler> handler) {
      Map<String, InputStreamSource> paths = this.files.computeIfAbsent(kind, key -> new LinkedHashMap<>());
      handler.accept(new InMemoryGeneratedFiles.InMemoryFileHandler(paths, path));
   }

   public Map<String, InputStreamSource> getGeneratedFiles(GeneratedFiles.Kind kind) {
      Assert.notNull(kind, "'kind' must not be null");
      return Collections.unmodifiableMap(this.files.getOrDefault(kind, Collections.emptyMap()));
   }

   public @Nullable String getGeneratedFileContent(GeneratedFiles.Kind kind, String path) throws IOException {
      InputStreamSource source = this.getGeneratedFile(kind, path);
      return source != null ? new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8) : null;
   }

   public @Nullable InputStreamSource getGeneratedFile(GeneratedFiles.Kind kind, String path) {
      Assert.notNull(kind, "'kind' must not be null");
      Assert.hasLength(path, "'path' must not be empty");
      Map<String, InputStreamSource> paths = this.files.get(kind);
      return paths != null ? paths.get(path) : null;
   }

   private static class InMemoryFileHandler extends GeneratedFiles.FileHandler {
      private final Map<String, InputStreamSource> paths;
      private final String key;

      InMemoryFileHandler(Map<String, InputStreamSource> paths, String key) {
         super(paths.containsKey(key), () -> paths.get(key));
         this.paths = paths;
         this.key = key;
      }

      @Override
      protected void copy(InputStreamSource content, boolean override) {
         this.paths.put(this.key, content);
      }

      @Override
      public String toString() {
         return this.key;
      }
   }
}
