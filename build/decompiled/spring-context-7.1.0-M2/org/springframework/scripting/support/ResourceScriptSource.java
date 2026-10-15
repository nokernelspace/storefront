package org.springframework.scripting.support;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.scripting.ScriptSource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

public class ResourceScriptSource implements ScriptSource {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private EncodedResource resource;
   private long lastModified = -1L;
   private final Object lastModifiedMonitor = new Object();

   public ResourceScriptSource(EncodedResource resource) {
      Assert.notNull(resource, "Resource must not be null");
      this.resource = resource;
   }

   public ResourceScriptSource(Resource resource) {
      Assert.notNull(resource, "Resource must not be null");
      this.resource = new EncodedResource(resource, StandardCharsets.UTF_8);
   }

   public final Resource getResource() {
      return this.resource.getResource();
   }

   public void setEncoding(@Nullable String encoding) {
      this.resource = new EncodedResource(this.resource.getResource(), encoding);
   }

   @Override
   public String getScriptAsString() throws IOException {
      synchronized (this.lastModifiedMonitor) {
         this.lastModified = this.retrieveLastModifiedTime();
      }

      Reader reader = this.resource.getReader();
      return FileCopyUtils.copyToString(reader);
   }

   @Override
   public boolean isModified() {
      synchronized (this.lastModifiedMonitor) {
         return this.lastModified < 0L || this.retrieveLastModifiedTime() > this.lastModified;
      }
   }

   protected long retrieveLastModifiedTime() {
      try {
         return this.getResource().lastModified();
      } catch (IOException ex) {
         if (this.logger.isDebugEnabled()) {
            this.logger
               .debug(this.getResource() + " could not be resolved in the file system - current timestamp not available for script modification check", ex);
         }

         return 0L;
      }
   }

   @Override
   public @Nullable String suggestedClassName() {
      String filename = this.getResource().getFilename();
      return filename != null ? StringUtils.stripFilenameExtension(filename) : null;
   }

   @Override
   public String toString() {
      return this.resource.toString();
   }
}
