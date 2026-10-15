package org.springframework.boot.origin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class OriginTrackedResource implements Resource, OriginProvider {
   private final Resource resource;
   private final @Nullable Origin origin;

   OriginTrackedResource(Resource resource, @Nullable Origin origin) {
      Assert.notNull(resource, "'resource' must not be null");
      this.resource = resource;
      this.origin = origin;
   }

   public InputStream getInputStream() throws IOException {
      return this.getResource().getInputStream();
   }

   public boolean exists() {
      return this.getResource().exists();
   }

   public boolean isReadable() {
      return this.getResource().isReadable();
   }

   public boolean isOpen() {
      return this.getResource().isOpen();
   }

   public boolean isFile() {
      return this.getResource().isFile();
   }

   public URL getURL() throws IOException {
      return this.getResource().getURL();
   }

   public URI getURI() throws IOException {
      return this.getResource().getURI();
   }

   public File getFile() throws IOException {
      return this.getResource().getFile();
   }

   public ReadableByteChannel readableChannel() throws IOException {
      return this.getResource().readableChannel();
   }

   public long contentLength() throws IOException {
      return this.getResource().contentLength();
   }

   public long lastModified() throws IOException {
      return this.getResource().lastModified();
   }

   public Resource createRelative(String relativePath) throws IOException {
      return this.getResource().createRelative(relativePath);
   }

   public @Nullable String getFilename() {
      return this.getResource().getFilename();
   }

   public String getDescription() {
      return this.getResource().getDescription();
   }

   public Resource getResource() {
      return this.resource;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return this.origin;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         OriginTrackedResource other = (OriginTrackedResource)obj;
         return this.resource.equals(other) && ObjectUtils.nullSafeEquals(this.origin, other.origin);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = this.resource.hashCode();
      return 31 * result + ObjectUtils.nullSafeHashCode(this.origin);
   }

   @Override
   public String toString() {
      return this.resource.toString();
   }

   public static OriginTrackedResource.OriginTrackedWritableResource of(WritableResource resource, Origin origin) {
      return (OriginTrackedResource.OriginTrackedWritableResource)of((Resource)resource, origin);
   }

   public static OriginTrackedResource of(Resource resource, @Nullable Origin origin) {
      return resource instanceof WritableResource writableResource
         ? new OriginTrackedResource.OriginTrackedWritableResource(writableResource, origin)
         : new OriginTrackedResource(resource, origin);
   }

   public static class OriginTrackedWritableResource extends OriginTrackedResource implements WritableResource {
      OriginTrackedWritableResource(WritableResource resource, @Nullable Origin origin) {
         super(resource, origin);
      }

      public WritableResource getResource() {
         return (WritableResource)super.getResource();
      }

      public OutputStream getOutputStream() throws IOException {
         return this.getResource().getOutputStream();
      }
   }
}
