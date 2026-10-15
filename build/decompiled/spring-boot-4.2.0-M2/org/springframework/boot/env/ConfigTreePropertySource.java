package org.springframework.boot.env;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.OriginProvider;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

public class ConfigTreePropertySource extends EnumerablePropertySource<Path> implements PropertySourceInfo, OriginLookup<String> {
   private static final int MAX_DEPTH = 100;
   private final Map<String, ConfigTreePropertySource.PropertyFile> propertyFiles;
   private final String[] names;
   private final Set<ConfigTreePropertySource.Option> options;

   public ConfigTreePropertySource(String name, Path sourceDirectory) {
      this(name, sourceDirectory, EnumSet.noneOf(ConfigTreePropertySource.Option.class));
   }

   public ConfigTreePropertySource(String name, Path sourceDirectory, ConfigTreePropertySource.Option... options) {
      this(name, sourceDirectory, EnumSet.copyOf(Arrays.asList(options)));
   }

   private ConfigTreePropertySource(String name, Path sourceDirectory, Set<ConfigTreePropertySource.Option> options) {
      super(name, sourceDirectory);
      Assert.isTrue(Files.exists(sourceDirectory), () -> "'sourceDirectory' [%s] must exist".formatted(sourceDirectory));
      Assert.isTrue(Files.isDirectory(sourceDirectory), () -> "'sourceDirectory' [%s] must be a directory".formatted(sourceDirectory));
      this.propertyFiles = ConfigTreePropertySource.PropertyFile.findAll(sourceDirectory, options);
      this.options = options;
      this.names = StringUtils.toStringArray(this.propertyFiles.keySet());
   }

   public String[] getPropertyNames() {
      return (String[])this.names.clone();
   }

   public ConfigTreePropertySource.@Nullable Value getProperty(String name) {
      ConfigTreePropertySource.PropertyFile propertyFile = this.propertyFiles.get(name);
      return propertyFile != null ? propertyFile.getContent() : null;
   }

   public @Nullable Origin getOrigin(String name) {
      ConfigTreePropertySource.PropertyFile propertyFile = this.propertyFiles.get(name);
      return propertyFile != null ? propertyFile.getOrigin() : null;
   }

   @Override
   public boolean isImmutable() {
      return !this.options.contains(ConfigTreePropertySource.Option.ALWAYS_READ);
   }

   public enum Option {
      ALWAYS_READ,
      USE_LOWERCASE_NAMES,
      AUTO_TRIM_TRAILING_NEW_LINE;
   }

   private static final class PropertyFile {
      private static final TextResourceOrigin.Location START_OF_FILE = new TextResourceOrigin.Location(0, 0);
      private final Path path;
      private final FileSystemResource resource;
      private final Origin origin;
      private final ConfigTreePropertySource.@Nullable PropertyFileContent cachedContent;
      private final boolean autoTrimTrailingNewLine;

      private PropertyFile(Path path, Set<ConfigTreePropertySource.Option> options) {
         this.path = path;
         this.resource = new FileSystemResource(path);
         this.origin = new TextResourceOrigin(this.resource, START_OF_FILE);
         this.autoTrimTrailingNewLine = options.contains(ConfigTreePropertySource.Option.AUTO_TRIM_TRAILING_NEW_LINE);
         this.cachedContent = options.contains(ConfigTreePropertySource.Option.ALWAYS_READ)
            ? null
            : new ConfigTreePropertySource.PropertyFileContent(path, this.resource, this.origin, true, this.autoTrimTrailingNewLine);
      }

      ConfigTreePropertySource.PropertyFileContent getContent() {
         return this.cachedContent != null
            ? this.cachedContent
            : new ConfigTreePropertySource.PropertyFileContent(this.path, this.resource, this.origin, false, this.autoTrimTrailingNewLine);
      }

      Origin getOrigin() {
         return this.origin;
      }

      static Map<String, ConfigTreePropertySource.PropertyFile> findAll(Path sourceDirectory, Set<ConfigTreePropertySource.Option> options) {
         try {
            Map<String, ConfigTreePropertySource.PropertyFile> propertyFiles = new TreeMap<>();

            try (Stream<Path> pathStream = Files.find(sourceDirectory, 100, ConfigTreePropertySource.PropertyFile::isPropertyFile, FileVisitOption.FOLLOW_LINKS)) {
               pathStream.forEach(path -> {
                  String name = getName(sourceDirectory.relativize(path));
                  if (StringUtils.hasText(name)) {
                     if (options.contains(ConfigTreePropertySource.Option.USE_LOWERCASE_NAMES)) {
                        name = name.toLowerCase(Locale.getDefault());
                     }

                     propertyFiles.put(name, new ConfigTreePropertySource.PropertyFile(path, options));
                  }
               });
            }

            return Collections.unmodifiableMap(propertyFiles);
         } catch (IOException ex) {
            throw new IllegalStateException("Unable to find files in '" + sourceDirectory + "'", ex);
         }
      }

      private static boolean isPropertyFile(Path path, BasicFileAttributes attributes) {
         return !hasHiddenPathElement(path) && (attributes.isRegularFile() || attributes.isSymbolicLink());
      }

      private static boolean hasHiddenPathElement(Path path) {
         for (Path element : path) {
            if (element.toString().startsWith("..")) {
               return true;
            }
         }

         return false;
      }

      private static String getName(Path relativePath) {
         int nameCount = relativePath.getNameCount();
         if (nameCount == 1) {
            return relativePath.toString();
         }

         StringBuilder name = new StringBuilder();

         for (int i = 0; i < nameCount; i++) {
            name.append(i != 0 ? "." : "");
            name.append(relativePath.getName(i));
         }

         return name.toString();
      }
   }

   private static final class PropertyFileContent implements ConfigTreePropertySource.Value, OriginProvider {
      private final Path path;
      private final Lock resourceLock = new ReentrantLock();
      private final Resource resource;
      private final Origin origin;
      private final boolean cacheContent;
      private final boolean autoTrimTrailingNewLine;
      private volatile byte @Nullable [] content;

      private PropertyFileContent(Path path, Resource resource, Origin origin, boolean cacheContent, boolean autoTrimTrailingNewLine) {
         this.path = path;
         this.resource = resource;
         this.origin = origin;
         this.cacheContent = cacheContent;
         this.autoTrimTrailingNewLine = autoTrimTrailingNewLine;
      }

      @Override
      public Origin getOrigin() {
         return this.origin;
      }

      @Override
      public int length() {
         return this.toString().length();
      }

      @Override
      public char charAt(int index) {
         return this.toString().charAt(index);
      }

      @Override
      public CharSequence subSequence(int start, int end) {
         return this.toString().subSequence(start, end);
      }

      @Override
      public String toString() {
         String string = new String(this.getBytes());
         if (this.autoTrimTrailingNewLine) {
            string = this.autoTrimTrailingNewLine(string);
         }

         return string;
      }

      private String autoTrimTrailingNewLine(String string) {
         if (!string.endsWith("\n")) {
            return string;
         }

         int numberOfLines = 0;

         for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            if (ch == '\n') {
               numberOfLines++;
            }
         }

         if (numberOfLines > 1) {
            return string;
         } else {
            return string.endsWith("\r\n") ? string.substring(0, string.length() - 2) : string.substring(0, string.length() - 1);
         }
      }

      public InputStream getInputStream() throws IOException {
         if (!this.cacheContent) {
            this.assertStillExists();
            return this.resource.getInputStream();
         } else {
            return new ByteArrayInputStream(this.getBytes());
         }
      }

      private byte[] getBytes() {
         try {
            if (!this.cacheContent) {
               this.assertStillExists();
               return FileCopyUtils.copyToByteArray(this.resource.getInputStream());
            }

            byte[] content = this.content;
            if (content == null) {
               this.assertStillExists();
               this.resourceLock.lock();

               try {
                  content = this.content;
                  if (content == null) {
                     content = FileCopyUtils.copyToByteArray(this.resource.getInputStream());
                     this.content = content;
                  }
               } finally {
                  this.resourceLock.unlock();
               }
            }

            return content;
         } catch (IOException ex) {
            throw new IllegalStateException(ex);
         }
      }

      private void assertStillExists() {
         Assert.state(Files.exists(this.path), () -> "The property file '" + this.path + "' no longer exists");
      }
   }

   public interface Value extends CharSequence, InputStreamSource {
   }
}
