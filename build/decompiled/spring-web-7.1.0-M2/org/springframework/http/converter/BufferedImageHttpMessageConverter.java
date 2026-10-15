package org.springframework.http.converter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.FileCacheImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

public class BufferedImageHttpMessageConverter implements HttpMessageConverter<BufferedImage> {
   private final List<MediaType> readableMediaTypes = new ArrayList<>();
   private @Nullable MediaType defaultContentType;
   private @Nullable File cacheDir;

   public BufferedImageHttpMessageConverter() {
      String[] readerMediaTypes = ImageIO.getReaderMIMETypes();

      for (String mediaType : readerMediaTypes) {
         if (StringUtils.hasText(mediaType)) {
            this.readableMediaTypes.add(MediaType.parseMediaType(mediaType));
         }
      }

      String[] writerMediaTypes = ImageIO.getWriterMIMETypes();

      for (String mediaType : writerMediaTypes) {
         if (StringUtils.hasText(mediaType)) {
            this.defaultContentType = MediaType.parseMediaType(mediaType);
            break;
         }
      }
   }

   public void setDefaultContentType(@Nullable MediaType defaultContentType) {
      if (defaultContentType != null) {
         Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByMIMEType(defaultContentType.toString());
         if (!imageWriters.hasNext()) {
            throw new IllegalArgumentException("Content-Type [" + defaultContentType + "] is not supported by the Java Image I/O API");
         }
      }

      this.defaultContentType = defaultContentType;
   }

   public @Nullable MediaType getDefaultContentType() {
      return this.defaultContentType;
   }

   public void setCacheDir(File cacheDir) {
      Assert.notNull(cacheDir, "'cacheDir' must not be null");
      Assert.isTrue(cacheDir.isDirectory(), () -> "'cacheDir' is not a directory: " + cacheDir);
      this.cacheDir = cacheDir;
   }

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return BufferedImage.class == clazz && this.isReadable(mediaType);
   }

   private boolean isReadable(@Nullable MediaType mediaType) {
      if (mediaType == null) {
         return true;
      }

      Iterator<ImageReader> imageReaders = ImageIO.getImageReadersByMIMEType(mediaType.toString());
      return imageReaders.hasNext();
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      return BufferedImage.class == clazz && this.isWritable(mediaType);
   }

   private boolean isWritable(@Nullable MediaType mediaType) {
      if (mediaType != null && !MediaType.ALL.equalsTypeAndSubtype(mediaType)) {
         Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByMIMEType(mediaType.toString());
         return imageWriters.hasNext();
      } else {
         return true;
      }
   }

   @Override
   public List<MediaType> getSupportedMediaTypes() {
      return Collections.unmodifiableList(this.readableMediaTypes);
   }

   public BufferedImage read(@Nullable Class<? extends BufferedImage> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      ImageInputStream imageInputStream = null;
      ImageReader imageReader = null;

      try {
         imageInputStream = this.createImageInputStream(inputMessage.getBody());
         MediaType contentType = inputMessage.getHeaders().getContentType();
         if (contentType == null) {
            throw new HttpMessageNotReadableException("No Content-Type header", inputMessage);
         } else {
            Iterator<ImageReader> imageReaders = ImageIO.getImageReadersByMIMEType(contentType.toString());
            if (imageReaders.hasNext()) {
               imageReader = imageReaders.next();
               ImageReadParam irp = imageReader.getDefaultReadParam();
               this.process(irp);
               imageReader.setInput(imageInputStream, true);
               return imageReader.read(0, irp);
            } else {
               throw new HttpMessageNotReadableException("Could not find javax.imageio.ImageReader for Content-Type [" + contentType + "]", inputMessage);
            }
         }
      } finally {
         if (imageReader != null) {
            imageReader.dispose();
         }

         if (imageInputStream != null) {
            try {
               imageInputStream.close();
            } catch (IOException var15) {
            }
         }
      }
   }

   private ImageInputStream createImageInputStream(InputStream is) throws IOException {
      is = StreamUtils.nonClosing(is);
      return this.cacheDir != null ? new FileCacheImageInputStream(is, this.cacheDir) : new MemoryCacheImageInputStream(is);
   }

   public void write(final BufferedImage image, final @Nullable MediaType contentType, final HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      final MediaType selectedContentType = this.getContentType(contentType);
      outputMessage.getHeaders().setContentType(selectedContentType);
      if (outputMessage instanceof StreamingHttpOutputMessage streamingOutputMessage) {
         streamingOutputMessage.setBody(new StreamingHttpOutputMessage.Body() {
            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
               BufferedImageHttpMessageConverter.this.writeInternal(image, selectedContentType, outputStream);
            }

            @Override
            public boolean repeatable() {
               return true;
            }
         });
      } else {
         this.writeInternal(image, selectedContentType, outputMessage.getBody());
      }
   }

   private MediaType getContentType(@Nullable MediaType contentType) {
      if (contentType == null || contentType.isWildcardType() || contentType.isWildcardSubtype()) {
         contentType = this.getDefaultContentType();
      }

      Assert.notNull(contentType, "Could not select Content-Type. Please specify one through the 'defaultContentType' property.");
      return contentType;
   }

   private void writeInternal(BufferedImage image, MediaType contentType, OutputStream body) throws IOException, HttpMessageNotWritableException {
      ImageOutputStream imageOutputStream = null;
      ImageWriter imageWriter = null;

      try {
         Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByMIMEType(contentType.toString());
         if (!imageWriters.hasNext()) {
            throw new HttpMessageNotWritableException("Could not find javax.imageio.ImageWriter for Content-Type [" + contentType + "]");
         }

         imageWriter = imageWriters.next();
         ImageWriteParam iwp = imageWriter.getDefaultWriteParam();
         this.process(iwp);
         imageOutputStream = this.createImageOutputStream(body);
         imageWriter.setOutput(imageOutputStream);
         imageWriter.write(null, new IIOImage(image, null, null), iwp);
      } finally {
         if (imageWriter != null) {
            imageWriter.dispose();
         }

         if (imageOutputStream != null) {
            try {
               imageOutputStream.close();
            } catch (IOException var13) {
            }
         }
      }
   }

   private ImageOutputStream createImageOutputStream(OutputStream os) throws IOException {
      return this.cacheDir != null ? new FileCacheImageOutputStream(os, this.cacheDir) : new MemoryCacheImageOutputStream(os);
   }

   protected void process(ImageReadParam irp) {
   }

   protected void process(ImageWriteParam iwp) {
   }
}
