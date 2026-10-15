package org.springframework.context.aot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.FileSystemGeneratedFiles;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.nativex.FileNativeConfigurationWriter;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;

public abstract class AbstractAotProcessor<T> {
   public static final String AOT_PROCESSING = "spring.aot.processing";
   private final AbstractAotProcessor.Settings settings;

   protected AbstractAotProcessor(AbstractAotProcessor.Settings settings) {
      this.settings = settings;
   }

   protected AbstractAotProcessor.Settings getSettings() {
      return this.settings;
   }

   public final T process() {
      try {
         System.setProperty("spring.aot.processing", "true");
         return this.doProcess();
      } finally {
         System.clearProperty("spring.aot.processing");
      }
   }

   protected abstract T doProcess();

   protected void deleteExistingOutput() {
      this.deleteExistingOutput(this.getSettings().getSourceOutput(), this.getSettings().getResourceOutput(), this.getSettings().getClassOutput());
   }

   private void deleteExistingOutput(Path... paths) {
      for (Path path : paths) {
         try {
            FileSystemUtils.deleteRecursively(path);
         } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete existing output in '" + path + "'", ex);
         }
      }
   }

   protected FileSystemGeneratedFiles createFileSystemGeneratedFiles() {
      return new FileSystemGeneratedFiles(this::getRoot);
   }

   private Path getRoot(Kind kind) {
      return switch (kind) {
         case SOURCE -> this.getSettings().getSourceOutput();
         case RESOURCE -> this.getSettings().getResourceOutput();
         case CLASS -> this.getSettings().getClassOutput();
         default -> throw new IncompatibleClassChangeError();
      };
   }

   protected void writeHints(RuntimeHints hints) {
      FileNativeConfigurationWriter writer = new FileNativeConfigurationWriter(
         this.getSettings().getResourceOutput(), this.getSettings().getGroupId(), this.getSettings().getArtifactId()
      );
      writer.write(hints);
   }

   public static final class Settings {
      private final Path sourceOutput;
      private final Path resourceOutput;
      private final Path classOutput;
      private final String groupId;
      private final String artifactId;

      private Settings(Path sourceOutput, Path resourceOutput, Path classOutput, String groupId, String artifactId) {
         this.sourceOutput = sourceOutput;
         this.resourceOutput = resourceOutput;
         this.classOutput = classOutput;
         this.groupId = groupId;
         this.artifactId = artifactId;
      }

      public static AbstractAotProcessor.Settings.Builder builder() {
         return new AbstractAotProcessor.Settings.Builder();
      }

      public Path getSourceOutput() {
         return this.sourceOutput;
      }

      public Path getResourceOutput() {
         return this.resourceOutput;
      }

      public Path getClassOutput() {
         return this.classOutput;
      }

      public String getGroupId() {
         return this.groupId;
      }

      public String getArtifactId() {
         return this.artifactId;
      }

      public static final class Builder {
         private @Nullable Path sourceOutput;
         private @Nullable Path resourceOutput;
         private @Nullable Path classOutput;
         private @Nullable String groupId;
         private @Nullable String artifactId;

         private Builder() {
         }

         public AbstractAotProcessor.Settings.Builder sourceOutput(Path sourceOutput) {
            this.sourceOutput = sourceOutput;
            return this;
         }

         public AbstractAotProcessor.Settings.Builder resourceOutput(Path resourceOutput) {
            this.resourceOutput = resourceOutput;
            return this;
         }

         public AbstractAotProcessor.Settings.Builder classOutput(Path classOutput) {
            this.classOutput = classOutput;
            return this;
         }

         public AbstractAotProcessor.Settings.Builder groupId(String groupId) {
            Assert.hasText(groupId, "'groupId' must not be empty");
            this.groupId = groupId;
            return this;
         }

         public AbstractAotProcessor.Settings.Builder artifactId(String artifactId) {
            Assert.hasText(artifactId, "'artifactId' must not be empty");
            this.artifactId = artifactId;
            return this;
         }

         public AbstractAotProcessor.Settings build() {
            Assert.notNull(this.sourceOutput, "'sourceOutput' must not be null");
            Assert.notNull(this.resourceOutput, "'resourceOutput' must not be null");
            Assert.notNull(this.classOutput, "'classOutput' must not be null");
            Assert.notNull(this.groupId, "'groupId' must not be null");
            Assert.notNull(this.artifactId, "'artifactId' must not be null");
            return new AbstractAotProcessor.Settings(this.sourceOutput, this.resourceOutput, this.classOutput, this.groupId, this.artifactId);
         }
      }
   }
}
