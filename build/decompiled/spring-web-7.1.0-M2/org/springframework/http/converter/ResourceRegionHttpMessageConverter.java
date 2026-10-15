package org.springframework.http.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StreamUtils;

public class ResourceRegionHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
   public ResourceRegionHttpMessageConverter() {
      super(MediaType.ALL);
   }

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return false;
   }

   @Override
   public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
      return false;
   }

   @Override
   public Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      throw new UnsupportedOperationException();
   }

   protected ResourceRegion readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canWrite(clazz, null, mediaType);
   }

   @Override
   public boolean canWrite(@Nullable Type type, @Nullable Class<?> clazz, @Nullable MediaType mediaType) {
      if (type instanceof ParameterizedType parameterizedType) {
         if (parameterizedType.getRawType() instanceof Class<?> rawType) {
            if (!Collection.class.isAssignableFrom(rawType)) {
               return false;
            } else if (parameterizedType.getActualTypeArguments().length != 1) {
               return false;
            } else {
               return parameterizedType.getActualTypeArguments()[0] instanceof Class<?> typeArgumentClass
                  ? ResourceRegion.class.isAssignableFrom(typeArgumentClass)
                  : false;
            }
         } else {
            return false;
         }
      } else {
         return type instanceof Class<?> c && ResourceRegion.class.isAssignableFrom(c);
      }
   }

   @Override
   public boolean canWriteRepeatedly(Object object, @Nullable MediaType contentType) {
      if (object instanceof ResourceRegion resourceRegion) {
         return this.supportsRepeatableWrites(resourceRegion);
      } else {
         for (ResourceRegion region : (Collection)object) {
            if (!this.supportsRepeatableWrites(region)) {
               return false;
            }
         }

         return true;
      }
   }

   @Override
   protected boolean supportsRepeatableWrites(Object object) {
      return this.canWriteRepeatedly(object, null);
   }

   private boolean supportsRepeatableWrites(ResourceRegion region) {
      return !(region.getResource() instanceof InputStreamResource);
   }

   @Override
   protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      if (object instanceof ResourceRegion resourceRegion) {
         this.writeResourceRegion(resourceRegion, outputMessage);
      } else {
         Collection<ResourceRegion> regions = (Collection<ResourceRegion>)object;
         if (regions.size() == 1) {
            this.writeResourceRegion(regions.iterator().next(), outputMessage);
         } else {
            this.writeResourceRegionCollection(regions, outputMessage);
         }
      }
   }

   @Override
   protected MediaType getDefaultContentType(Object object) {
      Resource resource = null;
      if (object instanceof ResourceRegion resourceRegion) {
         resource = resourceRegion.getResource();
      } else {
         Collection<ResourceRegion> regions = (Collection<ResourceRegion>)object;
         if (!regions.isEmpty()) {
            resource = regions.iterator().next().getResource();
         }
      }

      return MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
   }

   protected void writeResourceRegion(ResourceRegion region, HttpOutputMessage outputMessage) throws IOException {
      Assert.notNull(region, "ResourceRegion must not be null");
      HttpHeaders responseHeaders = outputMessage.getHeaders();
      long start = region.getPosition();
      long end = start + region.getCount() - 1L;
      long resourceLength = region.getResource().contentLength();
      end = Math.min(end, resourceLength - 1L);
      long rangeLength = end - start + 1L;
      responseHeaders.add("Content-Range", "bytes " + start + "-" + end + "/" + resourceLength);
      responseHeaders.setContentLength(rangeLength);
      InputStream in = region.getResource().getInputStream();

      try {
         StreamUtils.copyRange(in, outputMessage.getBody(), start, end);
      } finally {
         try {
            in.close();
         } catch (IOException var19) {
         }
      }
   }

   private void writeResourceRegionCollection(Collection<ResourceRegion> resourceRegions, HttpOutputMessage outputMessage) throws IOException {
      Assert.notNull(resourceRegions, "Collection of ResourceRegion should not be null");
      HttpHeaders responseHeaders = outputMessage.getHeaders();
      MediaType contentType = responseHeaders.getContentType();
      String boundaryString = MimeTypeUtils.generateMultipartBoundaryString();
      responseHeaders.set("Content-Type", "multipart/byteranges; boundary=" + boundaryString);
      OutputStream out = outputMessage.getBody();
      Resource resource = null;
      InputStream in = null;
      long inputStreamPosition = 0L;

      try {
         for (ResourceRegion region : resourceRegions) {
            long start = region.getPosition() - inputStreamPosition;
            if (start < 0L || resource != region.getResource()) {
               if (in != null) {
                  in.close();
               }

               resource = region.getResource();
               in = resource.getInputStream();
               inputStreamPosition = 0L;
               start = region.getPosition();
            }

            long end = start + region.getCount() - 1L;
            println(out);
            print(out, "--" + boundaryString);
            println(out);
            if (contentType != null) {
               print(out, "Content-Type: " + contentType);
               println(out);
            }

            long resourceLength = region.getResource().contentLength();
            end = Math.min(end, resourceLength - inputStreamPosition - 1L);
            print(out, "Content-Range: bytes " + region.getPosition() + "-" + (region.getPosition() + region.getCount() - 1L) + "/" + resourceLength);
            println(out);
            println(out);
            StreamUtils.copyRange(in, out, start, end);
            inputStreamPosition += end + 1L;
         }
      } finally {
         try {
            if (in != null) {
               in.close();
            }
         } catch (IOException var24) {
         }
      }

      println(out);
      print(out, "--" + boundaryString + "--");
   }

   private static void println(OutputStream os) throws IOException {
      os.write(13);
      os.write(10);
   }

   private static void print(OutputStream os, String buf) throws IOException {
      os.write(buf.getBytes(StandardCharsets.US_ASCII));
   }
}
