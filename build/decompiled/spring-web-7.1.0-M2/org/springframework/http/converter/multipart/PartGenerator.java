package org.springframework.http.converter.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

final class PartGenerator implements MultipartParser.PartListener {
   private static final Log logger = LogFactory.getLog(PartGenerator.class);
   private final MultiValueMap<String, Part> parts = new LinkedMultiValueMap();
   private final int maxInMemorySize;
   private final long maxDiskUsagePerPart;
   private final int maxParts;
   private final Path fileStorageDirectory;
   private int partCount;
   private PartGenerator.State state;

   PartGenerator(int maxInMemorySize, long maxDiskUsagePerPart, int maxParts, Path fileStorageDirectory) {
      this.maxInMemorySize = maxInMemorySize;
      this.maxDiskUsagePerPart = maxDiskUsagePerPart;
      this.maxParts = maxParts;
      this.fileStorageDirectory = fileStorageDirectory;
      this.state = new PartGenerator.InitialState();
   }

   public MultiValueMap<String, Part> getParts() {
      return this.parts;
   }

   @Override
   public void onHeaders(HttpHeaders headers) {
      this.state.onComplete();
      if (isFormField(headers)) {
         this.state = new PartGenerator.FormFieldState(headers);
      } else {
         this.state = new PartGenerator.InMemoryState(headers);
      }
   }

   private static boolean isFormField(HttpHeaders headers) {
      MediaType contentType = headers.getContentType();
      return (contentType == null || MediaType.TEXT_PLAIN.equalsTypeAndSubtype(contentType)) && headers.getContentDisposition().getFilename() == null;
   }

   @Override
   public void onBody(DataBuffer buffer, boolean last) {
      try {
         this.state.onBody(buffer, last);
      } catch (Throwable ex) {
         this.deleteParts();
         throw ex;
      }
   }

   void deleteParts() {
      this.state.dispose();

      for (List<Part> parts : this.parts.values()) {
         for (Part part : parts) {
            try {
               part.delete();
            } catch (IOException var6) {
            }
         }
      }
   }

   @Override
   public void onComplete() {
      this.state.onComplete();
      if (logger.isTraceEnabled()) {
         logger.trace("Finished reading " + this.partCount + " part(s)");
      }
   }

   @Override
   public void onError(Throwable ex) {
      this.deleteParts();
   }

   void addPart(Part part) {
      if (this.maxParts != -1 && this.partCount == this.maxParts) {
         throw new HttpMessageConversionException("Maximum number of parts exceeded: " + this.maxParts);
      }

      try {
         this.partCount++;
         this.parts.add(part.name(), part);
      } catch (Exception exc) {
         throw new HttpMessageConversionException("Part #" + this.partCount + " is unnamed", exc);
      }
   }

   private final class FileState implements PartGenerator.State {
      private final HttpHeaders headers;
      private final Path file;
      private final OutputStream outputStream;
      private long byteCount;
      private boolean emitted;

      public FileState(HttpHeaders headers, Path folder) {
         this.headers = headers;
         this.file = this.createFile(folder);
         this.outputStream = this.createOutputStream(this.file);
      }

      private Path createFile(Path directory) {
         try {
            Path tempFile = Files.createTempFile(directory, null, ".multipart");
            if (PartGenerator.logger.isTraceEnabled()) {
               PartGenerator.logger.trace("Storing multipart data in file " + tempFile);
            }

            return tempFile;
         } catch (IOException ex) {
            throw new UncheckedIOException("Could not create temp file in " + directory, ex);
         }
      }

      private OutputStream createOutputStream(Path file) {
         try {
            return Files.newOutputStream(file, StandardOpenOption.WRITE);
         } catch (IOException ex) {
            throw new UncheckedIOException("Could not write to temp file " + file, ex);
         }
      }

      @Override
      public void onBody(DataBuffer dataBuffer, boolean last) {
         this.byteCount = this.byteCount + dataBuffer.readableByteCount();
         if (this.isMaxDiskUsagePerPartExceeded()) {
            this.closeOutputStream();
            throw new HttpMessageConversionException("Part exceeded the disk usage limit of " + PartGenerator.this.maxDiskUsagePerPart + " bytes");
         }

         this.writeBuffer(dataBuffer);
         if (last) {
            this.onComplete();
         }
      }

      @Override
      public void onComplete() {
         if (!this.emitted) {
            this.emitted = true;
            Part part = DefaultParts.part(this.headers, this.file);
            PartGenerator.this.addPart(part);
            this.closeOutputStream();
         }
      }

      private boolean isMaxDiskUsagePerPartExceeded() {
         return PartGenerator.this.maxDiskUsagePerPart != -1L && this.byteCount > PartGenerator.this.maxDiskUsagePerPart;
      }

      private void closeOutputStream() {
         try {
            this.outputStream.close();
         } catch (IOException var2) {
         }
      }

      private void writeBuffer(DataBuffer dataBuffer) {
         try (InputStream in = dataBuffer.asInputStream()) {
            in.transferTo(this.outputStream);
            this.outputStream.flush();
         } catch (IOException exc) {
            throw new UncheckedIOException("Could not write to temp file ", exc);
         } finally {
            DataBufferUtils.release(dataBuffer);
         }
      }

      @Override
      public void dispose() {
         this.closeOutputStream();

         try {
            Files.deleteIfExists(this.file);
         } catch (IOException var2) {
         }
      }

      @Override
      public String toString() {
         return "WRITE-FILE";
      }
   }

   private final class FormFieldState implements PartGenerator.State {
      private final FastByteArrayOutputStream value = new FastByteArrayOutputStream();
      private final HttpHeaders headers;
      private boolean emitted;

      public FormFieldState(HttpHeaders headers) {
         this.headers = headers;
      }

      @Override
      public void onBody(DataBuffer dataBuffer, boolean last) {
         int size = this.value.size() + dataBuffer.readableByteCount();
         if (PartGenerator.this.maxInMemorySize != -1 && size >= PartGenerator.this.maxInMemorySize) {
            DataBufferUtils.release(dataBuffer);
            throw new HttpMessageConversionException("Form field value exceeded the memory usage limit of " + PartGenerator.this.maxInMemorySize + " bytes");
         }

         this.store(dataBuffer);
         if (last) {
            this.onComplete();
         }
      }

      @Override
      public void onComplete() {
         if (!this.emitted) {
            this.emitted = true;
            byte[] bytes = this.value.toByteArrayUnsafe();
            String value = new String(bytes, MultipartUtils.charset(this.headers));
            FormFieldPart formFieldPart = DefaultParts.formFieldPart(this.headers, value);
            PartGenerator.this.addPart(formFieldPart);
         }
      }

      private void store(DataBuffer dataBuffer) {
         try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            this.value.write(bytes);
         } catch (IOException ex) {
            throw new HttpMessageConversionException("Cannot store multipart body", ex);
         } finally {
            DataBufferUtils.release(dataBuffer);
         }
      }

      @Override
      public String toString() {
         return "FORM-FIELD";
      }
   }

   private final class InMemoryState implements PartGenerator.State {
      private final Queue<DataBuffer> content = new ArrayDeque<>();
      private long byteCount;
      private final HttpHeaders headers;
      private boolean emitted;

      public InMemoryState(HttpHeaders headers) {
         this.headers = headers;
      }

      @Override
      public void onBody(DataBuffer dataBuffer, boolean last) {
         this.byteCount = this.byteCount + dataBuffer.readableByteCount();
         if (this.isMaxInMemorySizeExceeded()) {
            this.switchToFile(dataBuffer, last);
         } else {
            this.content.add(dataBuffer);
            if (last) {
               this.onComplete();
            }
         }
      }

      @Override
      public void onComplete() {
         if (!this.emitted) {
            this.emitted = true;
            this.emitMemoryPart();
         }
      }

      private boolean isMaxInMemorySizeExceeded() {
         return PartGenerator.this.maxInMemorySize != -1 && this.byteCount > PartGenerator.this.maxInMemorySize;
      }

      private void switchToFile(DataBuffer current, boolean last) {
         PartGenerator.FileState fileState = PartGenerator.this.new FileState(this.headers, PartGenerator.this.fileStorageDirectory);
         this.content.forEach(buffer -> fileState.onBody(buffer, false));
         fileState.onBody(current, last);
         PartGenerator.this.state = fileState;
      }

      private void emitMemoryPart() {
         byte[] bytes = new byte[(int)this.byteCount];
         int idx = 0;

         for (DataBuffer buffer : this.content) {
            int len = buffer.readableByteCount();
            buffer.read(bytes, idx, len);
            idx += len;
            DataBufferUtils.release(buffer);
         }

         this.content.clear();
         DefaultDataBuffer content = DefaultDataBufferFactory.sharedInstance.wrap(bytes);
         Part part = DefaultParts.part(this.headers, content);
         PartGenerator.this.addPart(part);
      }

      @Override
      public void dispose() {
         this.content.forEach(DataBufferUtils::release);
      }

      @Override
      public String toString() {
         return "IN-MEMORY";
      }
   }

   private static final class InitialState implements PartGenerator.State {
      @Override
      public void onBody(DataBuffer dataBuffer, boolean last) {
         DataBufferUtils.release(dataBuffer);
         throw new HttpMessageConversionException("Body token not expected");
      }

      @Override
      public String toString() {
         return "INITIAL";
      }
   }

   private interface State {
      void onBody(DataBuffer dataBuffer, boolean last);

      default void onComplete() {
      }

      default void dispose() {
      }
   }
}
