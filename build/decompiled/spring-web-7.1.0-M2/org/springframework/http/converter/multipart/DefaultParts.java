package org.springframework.http.converter.multipart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

abstract class DefaultParts {
   public static FormFieldPart formFieldPart(HttpHeaders headers, String value) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(value, "Value must not be null");
      return new DefaultParts.DefaultFormFieldPart(headers, value);
   }

   public static Part part(HttpHeaders headers, DataBuffer dataBuffer) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(dataBuffer, "DataBuffer must not be null");
      return partInternal(headers, new DefaultParts.DataBufferContent(dataBuffer));
   }

   public static Part part(HttpHeaders headers, Path file) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(file, "File must not be null");
      return partInternal(headers, new DefaultParts.FileContent(file));
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
         Assert.state(name != null, "No part name available");
         return name;
      }

      @Override
      public HttpHeaders headers() {
         return this.headers;
      }
   }

   private interface Content {
      InputStream content() throws IOException;

      void transferTo(Path dest) throws IOException;

      void delete() throws IOException;
   }

   private static final class DataBufferContent implements DefaultParts.Content {
      private final DataBuffer content;

      public DataBufferContent(DataBuffer content) {
         this.content = content;
      }

      @Override
      public InputStream content() {
         return this.content.asInputStream();
      }

      @Override
      public void transferTo(Path dest) throws IOException {
         Files.copy(this.content.asInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
      }

      @Override
      public void delete() throws IOException {
      }
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
      public void transferTo(Path dest) throws IOException {
         this.content.transferTo(dest);
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
      public InputStream content() {
         byte[] bytes = this.value.getBytes(MultipartUtils.charset(this.headers()));
         return new ByteArrayInputStream(bytes);
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
      public InputStream content() throws IOException {
         return this.content.content();
      }

      @Override
      public void delete() throws IOException {
         this.content.delete();
      }

      @Override
      public String toString() {
         String name = this.headers().getContentDisposition().getName();
         return name != null ? "DefaultPart{" + name + "}" : "DefaultPart";
      }
   }

   private static final class FileContent implements DefaultParts.Content {
      private final Path file;

      public FileContent(Path file) {
         this.file = file;
      }

      @Override
      public InputStream content() throws IOException {
         return Files.newInputStream(this.file.toAbsolutePath(), StandardOpenOption.READ);
      }

      @Override
      public void transferTo(Path dest) throws IOException {
         Files.copy(this.file, dest, StandardCopyOption.REPLACE_EXISTING);
      }

      @Override
      public void delete() throws IOException {
         Files.delete(this.file);
      }
   }
}
