package org.springframework.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.IOConsumer;

public class DefaultResourceLoader implements ResourceLoader {
   private @Nullable ClassLoader classLoader;
   private final Set<ProtocolResolver> protocolResolvers = new LinkedHashSet<>(4);
   private final Map<Class<?>, Map<Resource, ?>> resourceCaches = new ConcurrentHashMap<>(4);

   public DefaultResourceLoader() {
   }

   public DefaultResourceLoader(@Nullable ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   public void setClassLoader(@Nullable ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   @Override
   public @Nullable ClassLoader getClassLoader() {
      return this.classLoader != null ? this.classLoader : ClassUtils.getDefaultClassLoader();
   }

   public void addProtocolResolver(ProtocolResolver resolver) {
      Assert.notNull(resolver, "ProtocolResolver must not be null");
      this.protocolResolvers.add(resolver);
   }

   public Collection<ProtocolResolver> getProtocolResolvers() {
      return this.protocolResolvers;
   }

   public <T> Map<Resource, T> getResourceCache(Class<T> valueType) {
      return (Map<Resource, T>)this.resourceCaches.computeIfAbsent(valueType, key -> new ConcurrentHashMap<>());
   }

   public void clearResourceCaches() {
      this.resourceCaches.clear();
   }

   @Override
   public Resource getResource(String location) {
      Assert.notNull(location, "Location must not be null");

      for (ProtocolResolver protocolResolver : this.getProtocolResolvers()) {
         Resource resource = protocolResolver.resolve(location, this);
         if (resource != null) {
            return resource;
         }
      }

      if (location.startsWith("/")) {
         return this.getResourceByPath(location);
      }

      if (location.startsWith("classpath:")) {
         return new ClassPathResource(location.substring("classpath:".length()), this.getClassLoader());
      }

      if (location.startsWith("classpath*:")) {
         return new DefaultResourceLoader.ClassPathAllResource(location.substring("classpath*:".length()), this.getClassLoader());
      }

      try {
         URL url = ResourceUtils.toURL(location);
         return ResourceUtils.isFileURL(url) ? new FileUrlResource(url) : new UrlResource(url);
      } catch (MalformedURLException ex) {
         return this.getResourceByPath(location);
      }
   }

   protected Resource getResourceByPath(String path) {
      return new DefaultResourceLoader.ClassPathContextResource(path, this.getClassLoader());
   }

   protected static class ClassPathAllResource extends ClassPathResource {
      public ClassPathAllResource(String path, @Nullable ClassLoader classLoader) {
         super(path, classLoader);
      }

      @Override
      public boolean isFile() {
         return false;
      }

      @Override
      public URL getURL() throws IOException {
         throw new FileNotFoundException(this.getDescription() + " cannot be resolved to single URL or File - use 'classpath:' instead");
      }

      @Override
      public long contentLength() throws IOException {
         long combinedLength = 0L;
         ClassLoader cl = this.getClassLoader();
         Enumeration<URL> urls = cl != null ? cl.getResources(this.getPath()) : ClassLoader.getSystemResources(this.getPath());

         while (urls.hasMoreElements()) {
            URLConnection con = urls.nextElement().openConnection();
            long length = con.getContentLengthLong();
            if (length < 0L) {
               return -1L;
            }

            combinedLength += length;
         }

         return combinedLength;
      }

      @Override
      public InputStream getInputStream() throws IOException {
         List<InputStream> streams = new ArrayList<>();
         ClassLoader cl = this.getClassLoader();
         Enumeration<URL> urls = cl != null ? cl.getResources(this.getPath()) : ClassLoader.getSystemResources(this.getPath());

         while (urls.hasMoreElements()) {
            try {
               streams.add(urls.nextElement().openStream());
            } catch (IOException ex) {
               streams.forEach(stream -> {
                  try {
                     stream.close();
                  } catch (IOException ex2) {
                     ex.addSuppressed(ex2);
                  }
               });
               throw ex;
            }
         }
         return switch (streams.size()) {
            case 0 -> InputStream.nullInputStream();
            case 1 -> (InputStream)streams.get(0);
            default -> new SequenceInputStream(Collections.enumeration(streams));
         };
      }

      @Override
      public void consumeContent(IOConsumer<InputStream> consumer) throws IOException {
         ClassLoader cl = this.getClassLoader();
         Enumeration<URL> urls = cl != null ? cl.getResources(this.getPath()) : ClassLoader.getSystemResources(this.getPath());

         while (urls.hasMoreElements()) {
            try (InputStream inputStream = urls.nextElement().openStream()) {
               consumer.accept(inputStream);
            }
         }
      }

      @Override
      public Resource createRelative(String relativePath) {
         String pathToUse = StringUtils.applyRelativePath(this.getPath(), relativePath);
         return new DefaultResourceLoader.ClassPathAllResource(pathToUse, this.getClassLoader());
      }

      @Override
      public String getDescription() {
         return "'classpath*:' resource [" + this.getPath() + "]";
      }
   }

   protected static class ClassPathContextResource extends ClassPathResource implements ContextResource {
      public ClassPathContextResource(String path, @Nullable ClassLoader classLoader) {
         super(path, classLoader);
      }

      @Override
      public String getPathWithinContext() {
         return this.getPath();
      }

      @Override
      public Resource createRelative(String relativePath) {
         String pathToUse = StringUtils.applyRelativePath(this.getPath(), relativePath);
         return new DefaultResourceLoader.ClassPathContextResource(pathToUse, this.getClassLoader());
      }
   }
}
