package org.springframework.boot.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ContextResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ApplicationResourceLoader extends DefaultResourceLoader {
   protected Resource getResourceByPath(String path) {
      return new ApplicationResourceLoader.ApplicationResource(path);
   }

   public static ResourceLoader get() {
      return get((ClassLoader)null);
   }

   public static ResourceLoader get(@Nullable ClassLoader classLoader) {
      return get(classLoader, SpringFactoriesLoader.forDefaultResourceLocation(classLoader));
   }

   public static ResourceLoader get(@Nullable ClassLoader classLoader, SpringFactoriesLoader springFactoriesLoader) {
      return get(classLoader, springFactoriesLoader, null);
   }

   public static ResourceLoader get(@Nullable ClassLoader classLoader, SpringFactoriesLoader springFactoriesLoader, @Nullable Path workingDirectory) {
      return get(ApplicationResourceLoader.ApplicationFileSystemResourceLoader.get(classLoader, workingDirectory), springFactoriesLoader);
   }

   public static ResourceLoader get(ResourceLoader resourceLoader) {
      return get(resourceLoader, false);
   }

   public static ResourceLoader get(ResourceLoader resourceLoader, boolean preferFileResolution) {
      Assert.notNull(resourceLoader, "'resourceLoader' must not be null");
      return get(resourceLoader, SpringFactoriesLoader.forDefaultResourceLocation(resourceLoader.getClassLoader()), preferFileResolution);
   }

   public static ResourceLoader get(ResourceLoader resourceLoader, SpringFactoriesLoader springFactoriesLoader) {
      return get(resourceLoader, springFactoriesLoader, false);
   }

   private static ResourceLoader get(ResourceLoader resourceLoader, SpringFactoriesLoader springFactoriesLoader, boolean preferFileResolution) {
      Assert.notNull(resourceLoader, "'resourceLoader' must not be null");
      Assert.notNull(springFactoriesLoader, "'springFactoriesLoader' must not be null");
      List<ProtocolResolver> protocolResolvers = springFactoriesLoader.load(ProtocolResolver.class);
      List<ApplicationResourceLoader.FilePathResolver> filePathResolvers = preferFileResolution
         ? springFactoriesLoader.load(ApplicationResourceLoader.FilePathResolver.class)
         : Collections.emptyList();
      return new ApplicationResourceLoader.ProtocolResolvingResourceLoader(resourceLoader, protocolResolvers, filePathResolvers);
   }

   private static final class ApplicationFileSystemResourceLoader extends DefaultResourceLoader {
      private static final ResourceLoader shared = new ApplicationResourceLoader.ApplicationFileSystemResourceLoader(null, null);
      private final @Nullable Path workingDirectory;

      private ApplicationFileSystemResourceLoader(@Nullable ClassLoader classLoader, @Nullable Path workingDirectory) {
         super(classLoader);
         this.workingDirectory = workingDirectory;
      }

      public Resource getResource(String location) {
         Resource resource = super.getResource(location);
         if (this.workingDirectory == null) {
            return resource;
         } else {
            return !resource.isFile() ? resource : this.resolveFile(resource, this.workingDirectory);
         }
      }

      private Resource resolveFile(Resource resource, Path workingDirectory) {
         try {
            File file = resource.getFile();
            return new ApplicationResourceLoader.ApplicationResource(workingDirectory.resolve(file.toPath()));
         } catch (FileNotFoundException ex) {
            return resource;
         } catch (IOException ex) {
            throw new UncheckedIOException(ex);
         }
      }

      protected Resource getResourceByPath(String path) {
         return new ApplicationResourceLoader.ApplicationResource(path);
      }

      static ResourceLoader get(@Nullable ClassLoader classLoader, @Nullable Path workingDirectory) {
         if (classLoader == null && workingDirectory != null) {
            throw new IllegalArgumentException("It's not possible to use null as 'classLoader' but specify a 'workingDirectory'");
         } else {
            return (ResourceLoader)(classLoader != null
               ? new ApplicationResourceLoader.ApplicationFileSystemResourceLoader(classLoader, workingDirectory)
               : shared);
         }
      }
   }

   private static final class ApplicationResource extends FileSystemResource implements ContextResource {
      ApplicationResource(String path) {
         super(path);
      }

      ApplicationResource(Path path) {
         super(path);
      }

      public String getPathWithinContext() {
         return this.getPath();
      }
   }

   public interface FilePathResolver {
      @Nullable String resolveFilePath(String location, Resource resource);
   }

   private static class ProtocolResolvingResourceLoader implements ResourceLoader {
      private final ResourceLoader resourceLoader;
      private final List<ProtocolResolver> protocolResolvers;
      private final List<ApplicationResourceLoader.FilePathResolver> filePathResolvers;

      ProtocolResolvingResourceLoader(
         ResourceLoader resourceLoader, List<ProtocolResolver> protocolResolvers, List<ApplicationResourceLoader.FilePathResolver> filePathResolvers
      ) {
         this.resourceLoader = resourceLoader;
         this.protocolResolvers = protocolResolvers;
         this.filePathResolvers = filePathResolvers;
      }

      public @Nullable ClassLoader getClassLoader() {
         return this.resourceLoader.getClassLoader();
      }

      public Resource getResource(String location) {
         if (StringUtils.hasLength(location)) {
            for (ProtocolResolver protocolResolver : this.protocolResolvers) {
               Resource resource = protocolResolver.resolve(location, this);
               if (resource != null) {
                  return resource;
               }
            }
         }

         Resource resource = this.resourceLoader.getResource(location);
         String filePath = this.getFilePath(location, resource);
         return (Resource)(filePath != null ? new ApplicationResourceLoader.ApplicationResource(filePath) : resource);
      }

      private @Nullable String getFilePath(String location, Resource resource) {
         for (ApplicationResourceLoader.FilePathResolver filePathResolver : this.filePathResolvers) {
            String filePath = filePathResolver.resolveFilePath(location, resource);
            if (filePath != null) {
               return filePath;
            }
         }

         return null;
      }
   }
}
