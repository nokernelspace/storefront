package org.springframework.http.codec.multipart;

import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

abstract class DefaultParts {
   public static FormFieldPart formFieldPart(HttpHeaders headers, String value) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(value, "Value must not be null");
      return new DefaultParts.DefaultFormFieldPart(headers, value);
   }

   public static Part part(HttpHeaders headers, Flux<DataBuffer> dataBuffers) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(dataBuffers, "DataBuffers must not be null");
      return partInternal(headers, new DefaultParts.FluxContent(dataBuffers));
   }

   public static Part part(HttpHeaders headers, Path file, Scheduler scheduler) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(file, "File must not be null");
      Assert.notNull(scheduler, "Scheduler must not be null");
      return partInternal(headers, new DefaultParts.FileContent(file, scheduler));
   }

   private static Part partInternal(HttpHeaders headers, DefaultParts.Content content) {
      String filename = headers.getContentDisposition().getFilename();
      return filename != null ? new DefaultParts.DefaultFilePart(headers, content) : new DefaultParts.DefaultPart(headers, content);
   }

   private abstract static class AbstractPart implements Part {
      private final HttpHeaders headers;

      protected AbstractPart(HttpHeaders headers) {
         Assert.notNull(headers, "HttpHeaders is required");
         this.headers = headers;
      }

      @Override
      public String name() {
         String name = this.headers().getContentDisposition().getName();
         Assert.state(name != null, "No name available");
         return name;
      }

      @Override
      public HttpHeaders headers() {
         return this.headers;
      }
   }

   private interface Content {
      Flux<DataBuffer> content();

      Mono<Void> transferTo(Path dest);

      Mono<Void> delete();
   }

   private static final class DefaultFilePart extends DefaultParts.DefaultPart implements FilePart {
      public DefaultFilePart(HttpHeaders headers, DefaultParts.Content content) {
         super(headers, content);
      }

      @Override
      public String filename() {
         String filename = this.headers().getContentDisposition().getFilename();
         Assert.state(filename != null, "No filename found");
         return filename;
      }

      @Override
      public Mono<Void> transferTo(Path dest) {
         return this.content.transferTo(dest);
      }

      @Override
      public String toString() {
         ContentDisposition contentDisposition = this.headers().getContentDisposition();
         String name = contentDisposition.getName();
         String filename = contentDisposition.getFilename();
         return name != null ? "DefaultFilePart{" + name + " (" + filename + ")}" : "DefaultFilePart{(" + filename + ")}";
      }
   }

   private static class DefaultFormFieldPart extends DefaultParts.AbstractPart implements FormFieldPart {
      private final String value;

      public DefaultFormFieldPart(HttpHeaders headers, String value) {
         super(headers);
         this.value = value;
      }

      @Override
      public Flux<DataBuffer> content() {
         return Flux.defer(() -> {
            byte[] bytes = this.value.getBytes(MultipartUtils.charset(this.headers()));
            return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes));
         });
      }

      @Override
      public String value() {
         return this.value;
      }

      @Override
      public String toString() {
         String name = this.headers().getContentDisposition().getName();
         return name != null ? "DefaultFormFieldPart{" + this.name() + "}" : "DefaultFormFieldPart";
      }
   }

   private static class DefaultPart extends DefaultParts.AbstractPart {
      protected final DefaultParts.Content content;

      public DefaultPart(HttpHeaders headers, DefaultParts.Content content) {
         super(headers);
         this.content = content;
      }

      @Override
      public Flux<DataBuffer> content() {
         return this.content.content();
      }

      @Override
      public Mono<Void> delete() {
         return this.content.delete();
      }

      @Override
      public String toString() {
         String name = this.headers().getContentDisposition().getName();
         return name != null ? "DefaultPart{" + name + "}" : "DefaultPart";
      }
   }

   private static final class FileContent implements DefaultParts.Content {
      private final Path file;
      private final Scheduler scheduler;

      public FileContent(Path file, Scheduler scheduler) {
         this.file = file;
         this.scheduler = scheduler;
      }

      @Override
      public Flux<DataBuffer> content() {
         return DataBufferUtils.readByteChannel(() -> Files.newByteChannel(this.file, StandardOpenOption.READ), DefaultDataBufferFactory.sharedInstance, 1024)
            .subscribeOn(this.scheduler);
      }

      @Override
      public Mono<Void> transferTo(Path dest) {
         return this.blockingOperation(() -> Files.copy(this.file, dest, StandardCopyOption.REPLACE_EXISTING));
      }

      @Override
      public Mono<Void> delete() {
         return this.blockingOperation(() -> {
            Files.delete(this.file);
            return null;
         });
      }

      private Mono<Void> blockingOperation(Callable<?> callable) {
         return Mono.create(sink -> {
            try {
               callable.call();
               sink.success();
            } catch (Exception ex) {
               sink.error(ex);
            }
         }).subscribeOn(this.scheduler);
      }
   }

   private static final class FluxContent implements DefaultParts.Content {
      private final Flux<DataBuffer> content;

      public FluxContent(Flux<DataBuffer> content) {
         this.content = content;
      }

      @Override
      public Flux<DataBuffer> content() {
         return this.content;
      }

      @Override
      public Mono<Void> transferTo(Path dest) {
         return DataBufferUtils.write(this.content, dest, new OpenOption[0]);
      }

      @Override
      public Mono<Void> delete() {
         return Mono.empty();
      }
   }
}
