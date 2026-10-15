package org.springframework.http.codec.multipart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

abstract class FileStorage {
   private static final Log logger = LogFactory.getLog(FileStorage.class);

   protected FileStorage() {
   }

   public abstract Mono<Path> directory();

   public static FileStorage fromPath(Path path) throws IOException {
      if (!Files.exists(path)) {
         Files.createDirectory(path);
      }

      return new FileStorage.PathFileStorage(path);
   }

   public static FileStorage tempDirectory(Supplier<Scheduler> scheduler) {
      return new FileStorage.TempFileStorage(scheduler);
   }

   private static final class PathFileStorage extends FileStorage {
      private final Mono<Path> directory;

      public PathFileStorage(Path directory) {
         this.directory = Mono.just(directory);
      }

      @Override
      public Mono<Path> directory() {
         return this.directory;
      }
   }

   private static final class TempFileStorage extends FileStorage {
      private static final String IDENTIFIER = "spring-multipart-";
      private final Supplier<Scheduler> scheduler;
      private volatile Mono<Path> directory = tempDirectory();

      public TempFileStorage(Supplier<Scheduler> scheduler) {
         this.scheduler = scheduler;
      }

      @Override
      public Mono<Path> directory() {
         return this.directory.flatMap(this::createNewDirectoryIfDeleted).subscribeOn(this.scheduler.get());
      }

      private Mono<Path> createNewDirectoryIfDeleted(Path directory) {
         if (!Files.exists(directory)) {
            Mono<Path> newDirectory = tempDirectory();
            this.directory = newDirectory;
            return newDirectory;
         } else {
            return Mono.just(directory);
         }
      }

      private static Mono<Path> tempDirectory() {
         return Mono.fromCallable(() -> {
            Path directory = Files.createTempDirectory("spring-multipart-");
            if (FileStorage.logger.isDebugEnabled()) {
               FileStorage.logger.debug("Created temporary storage directory: " + directory);
            }

            return directory;
         }).cache();
      }
   }
}
