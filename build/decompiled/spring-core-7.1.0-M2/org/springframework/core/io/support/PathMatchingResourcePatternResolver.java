package org.springframework.core.io.support;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes.Name;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NativeDetector;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.VfsResource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.PathMatcher;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

public class PathMatchingResourcePatternResolver implements ResourcePatternResolver {
   private static final Resource[] EMPTY_RESOURCE_ARRAY = new Resource[0];
   private static final Log logger = LogFactory.getLog(PathMatchingResourcePatternResolver.class);
   private static final Set<String> systemModuleNames = NativeDetector.inNativeImage()
      ? Collections.emptySet()
      : ModuleFinder.ofSystem().findAll().stream().map(moduleReference -> moduleReference.descriptor().name()).collect(Collectors.toSet());
   private static final Predicate<ResolvedModule> isNotSystemModule = resolvedModule -> !systemModuleNames.contains(resolvedModule.name());
   private static @Nullable Method equinoxResolveMethod;
   private final ResourceLoader resourceLoader;
   private PathMatcher pathMatcher = new AntPathMatcher();
   private @Nullable Boolean useCaches;
   private final Map<String, Resource[]> rootDirCache = new ConcurrentHashMap<>();
   private final Map<String, NavigableSet<String>> jarEntriesCache = new ConcurrentHashMap<>();
   private volatile @Nullable Set<PathMatchingResourcePatternResolver.ClassPathManifestEntry> manifestEntriesCache;

   public PathMatchingResourcePatternResolver() {
      this.resourceLoader = new DefaultResourceLoader();
   }

   public PathMatchingResourcePatternResolver(ResourceLoader resourceLoader) {
      Assert.notNull(resourceLoader, "ResourceLoader must not be null");
      this.resourceLoader = resourceLoader;
   }

   public PathMatchingResourcePatternResolver(@Nullable ClassLoader classLoader) {
      this.resourceLoader = new DefaultResourceLoader(classLoader);
   }

   public ResourceLoader getResourceLoader() {
      return this.resourceLoader;
   }

   @Override
   public @Nullable ClassLoader getClassLoader() {
      return this.getResourceLoader().getClassLoader();
   }

   public void setPathMatcher(PathMatcher pathMatcher) {
      Assert.notNull(pathMatcher, "PathMatcher must not be null");
      this.pathMatcher = pathMatcher;
   }

   public PathMatcher getPathMatcher() {
      return this.pathMatcher;
   }

   public void setUseCaches(boolean useCaches) {
      this.useCaches = useCaches;
   }

   @Override
   public Resource getResource(String location) {
      Resource resource = this.getResourceLoader().getResource(location);
      if (this.useCaches != null && resource instanceof UrlResource urlResource) {
         urlResource.setUseCaches(this.useCaches);
      }

      return resource;
   }

   @Override
   public Resource[] getResources(String locationPattern) throws IOException {
      Assert.notNull(locationPattern, "Location pattern must not be null");
      if (locationPattern.startsWith("classpath*:")) {
         String locationPatternWithoutPrefix = locationPattern.substring("classpath*:".length());
         Set<Resource> resources = this.findAllModulePathResources(locationPatternWithoutPrefix);
         if (this.getPathMatcher().isPattern(locationPatternWithoutPrefix)) {
            Collections.addAll(resources, this.findPathMatchingResources(locationPattern));
         } else {
            Collections.addAll(resources, this.findAllClassPathResources(locationPatternWithoutPrefix));
         }

         return resources.toArray(EMPTY_RESOURCE_ARRAY);
      } else {
         int prefixEnd = locationPattern.startsWith("war:") ? locationPattern.indexOf("*/") + 1 : locationPattern.indexOf(58) + 1;
         return this.getPathMatcher().isPattern(locationPattern.substring(prefixEnd))
            ? this.findPathMatchingResources(locationPattern)
            : new Resource[]{this.getResource(locationPattern)};
      }
   }

   public void clearCache() {
      this.rootDirCache.clear();
      this.jarEntriesCache.clear();
      this.manifestEntriesCache = null;
   }

   protected Resource[] findAllClassPathResources(String location) throws IOException {
      String path = stripLeadingSlash(location);
      Set<Resource> result = this.doFindAllClassPathResources(path);
      if (logger.isTraceEnabled()) {
         logger.trace("Resolved class path location [" + path + "] to resources " + result);
      }

      return result.toArray(EMPTY_RESOURCE_ARRAY);
   }

   protected Set<Resource> doFindAllClassPathResources(String path) throws IOException {
      Set<Resource> result = new LinkedHashSet<>(16);
      ClassLoader cl = this.getClassLoader();
      Enumeration<URL> resourceUrls = cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path);

      while (resourceUrls.hasMoreElements()) {
         URL url = resourceUrls.nextElement();
         result.add(this.convertClassLoaderURL(url));
      }

      if (!StringUtils.hasLength(path)) {
         this.addAllClassLoaderJarRoots(cl, result);
      }

      return result;
   }

   protected Resource convertClassLoaderURL(URL url) {
      if ("file".equals(url.getProtocol())) {
         try {
            return new FileSystemResource(ResourceUtils.toURI(url).getSchemeSpecificPart());
         } catch (URISyntaxException ex) {
            return new FileSystemResource(url.getFile());
         }
      } else {
         UrlResource resource = null;
         String urlString = url.toString();
         String cleanedPath = StringUtils.cleanPath(urlString);
         if (!cleanedPath.equals(urlString)) {
            try {
               resource = new UrlResource(new URL(url, cleanedPath));
            } catch (MalformedURLException var7) {
            }
         }

         if (resource == null) {
            resource = new UrlResource(url);
         }

         if (this.useCaches != null) {
            resource.setUseCaches(this.useCaches);
         }

         return resource;
      }
   }

   protected void addAllClassLoaderJarRoots(@Nullable ClassLoader classLoader, Set<Resource> result) {
      if (classLoader instanceof URLClassLoader urlClassLoader) {
         try {
            for (URL url : urlClassLoader.getURLs()) {
               try {
                  UrlResource jarResource = "jar".equals(url.getProtocol()) ? new UrlResource(url) : new UrlResource("jar:" + url + "!/");
                  if (this.useCaches != null) {
                     jarResource.setUseCaches(this.useCaches);
                  }

                  if (jarResource.exists()) {
                     result.add(jarResource);
                  }
               } catch (MalformedURLException ex) {
                  if (logger.isDebugEnabled()) {
                     logger.debug(
                        "Cannot search for matching files underneath [" + url + "] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage()
                     );
                  }
               }
            }
         } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot introspect jar files since ClassLoader [" + classLoader + "] does not support 'getURLs()': " + ex);
            }
         }
      }

      if (classLoader == ClassLoader.getSystemClassLoader()) {
         this.addClassPathManifestEntries(result);
      }

      if (classLoader != null) {
         try {
            this.addAllClassLoaderJarRoots(classLoader.getParent(), result);
         } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot introspect jar files in parent ClassLoader since [" + classLoader + "] does not support 'getParent()': " + ex);
            }
         }
      }
   }

   protected void addClassPathManifestEntries(Set<Resource> result) {
      Set<PathMatchingResourcePatternResolver.ClassPathManifestEntry> entries = this.manifestEntriesCache;
      if (entries == null) {
         entries = this.getClassPathManifestEntries();
         if (this.useCaches == null || this.useCaches) {
            this.manifestEntriesCache = entries;
         }
      }

      for (PathMatchingResourcePatternResolver.ClassPathManifestEntry entry : entries) {
         if (!result.contains(entry.resource()) && entry.alternative() != null && !result.contains(entry.alternative())) {
            result.add(entry.resource());
         }
      }
   }

   private Set<PathMatchingResourcePatternResolver.ClassPathManifestEntry> getClassPathManifestEntries() {
      Set<PathMatchingResourcePatternResolver.ClassPathManifestEntry> manifestEntries = new LinkedHashSet<>();
      Set<File> seen = new HashSet<>();

      try {
         String paths = System.getProperty("java.class.path");

         for (String path : StringUtils.delimitedListToStringArray(paths, File.pathSeparator)) {
            try {
               File jar = new File(path).getAbsoluteFile();
               if (jar.isFile() && seen.add(jar)) {
                  manifestEntries.add(PathMatchingResourcePatternResolver.ClassPathManifestEntry.of(jar, this.useCaches));
                  manifestEntries.addAll(this.getClassPathManifestEntriesFromJar(jar));
               }
            } catch (MalformedURLException ex) {
               if (logger.isDebugEnabled()) {
                  logger.debug(
                     "Cannot search for matching files underneath [" + path + "] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage()
                  );
               }
            }
         }

         return Collections.unmodifiableSet(manifestEntries);
      } catch (Exception ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to evaluate 'java.class.path' manifest entries: " + ex);
         }

         return Collections.emptySet();
      }
   }

   private Set<PathMatchingResourcePatternResolver.ClassPathManifestEntry> getClassPathManifestEntriesFromJar(File jar) throws IOException {
      URL base = jar.toURI().toURL();
      File parent = jar.getAbsoluteFile().getParentFile();

      try (JarFile jarFile = new JarFile(jar)) {
         Manifest manifest = jarFile.getManifest();
         Attributes attributes = manifest != null ? manifest.getMainAttributes() : null;
         String classPath = attributes != null ? attributes.getValue(Name.CLASS_PATH) : null;
         Set<PathMatchingResourcePatternResolver.ClassPathManifestEntry> manifestEntries = new LinkedHashSet<>();
         if (StringUtils.hasLength(classPath)) {
            StringTokenizer tokenizer = new StringTokenizer(classPath);

            while (tokenizer.hasMoreTokens()) {
               String path = tokenizer.nextToken();
               if (path.indexOf(58) < 0 || "file".equalsIgnoreCase(new URL(base, path).getProtocol())) {
                  File pathFile = null;
                  boolean absolute = false;
                  if (path.startsWith("file:")) {
                     try {
                        pathFile = new File(ResourceUtils.toURI(path));
                        absolute = true;
                     } catch (URISyntaxException | IllegalArgumentException var15) {
                     }
                  }

                  if (pathFile == null) {
                     pathFile = new File(path);
                     absolute = pathFile.isAbsolute();
                  }

                  File candidate = absolute ? pathFile : new File(parent, path);
                  if (candidate.isFile() && (absolute || candidate.getCanonicalPath().contains(parent.getCanonicalPath()))) {
                     manifestEntries.add(PathMatchingResourcePatternResolver.ClassPathManifestEntry.of(candidate, this.useCaches));
                  }
               }
            }
         }

         return Collections.unmodifiableSet(manifestEntries);
      } catch (Exception ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to load manifest entries from jar file '" + jar + "': " + ex);
         }

         return Collections.emptySet();
      }
   }

   protected Resource[] findPathMatchingResources(String locationPattern) throws IOException {
      String rootDirPath = this.determineRootDir(locationPattern);
      String subPattern = locationPattern.substring(rootDirPath.length());
      Resource[] rootDirResources = this.rootDirCache.get(rootDirPath);
      String actualRootPath = null;
      if (rootDirResources == null) {
         String commonPrefix = null;
         String existingPath = null;
         boolean commonUnique = true;

         for (String path : this.rootDirCache.keySet()) {
            String currentPrefix = null;

            for (int i = 0; i < path.length(); i++) {
               if (i == rootDirPath.length() || path.charAt(i) != rootDirPath.charAt(i)) {
                  currentPrefix = path.substring(0, path.lastIndexOf(47, i - 1) + 1);
                  break;
               }
            }

            if (currentPrefix != null) {
               if (checkPathWithinPackage(path.substring(currentPrefix.length()))) {
                  if (commonPrefix == null || !commonUnique || currentPrefix.length() > commonPrefix.length()) {
                     commonPrefix = currentPrefix;
                     existingPath = path;
                  } else if (currentPrefix.equals(commonPrefix)) {
                     commonUnique = false;
                  }
               }
            } else if (actualRootPath == null || path.length() > actualRootPath.length()) {
               rootDirResources = this.rootDirCache.get(path);
               actualRootPath = path;
            }
         }

         if (rootDirResources == null && StringUtils.hasLength(commonPrefix)) {
            rootDirResources = this.getResources(commonPrefix);
            Resource[] existingResources = this.rootDirCache.get(existingPath);
            if (existingResources != null && rootDirResources.length == existingResources.length) {
               this.rootDirCache.remove(existingPath);
               this.rootDirCache.put(commonPrefix, rootDirResources);
               actualRootPath = commonPrefix;
            } else if (commonPrefix.equals(rootDirPath)) {
               this.rootDirCache.put(rootDirPath, rootDirResources);
            } else {
               rootDirResources = null;
            }
         }

         if (rootDirResources == null) {
            rootDirResources = this.getResources(rootDirPath);
            if (this.useCaches == null || this.useCaches) {
               this.rootDirCache.put(rootDirPath, rootDirResources);
            }
         }
      }

      Set<Resource> result = new LinkedHashSet<>(64);

      for (Resource rootDirResource : rootDirResources) {
         if (actualRootPath != null && actualRootPath.length() < rootDirPath.length()) {
            rootDirResource = rootDirResource.createRelative(rootDirPath.substring(actualRootPath.length()));
         }

         rootDirResource = this.resolveRootDirResource(rootDirResource);
         URL rootDirUrl = rootDirResource.getURL();
         if (equinoxResolveMethod != null && rootDirUrl.getProtocol().startsWith("bundle")) {
            URL resolvedUrl = (URL)ReflectionUtils.invokeMethod(equinoxResolveMethod, null, rootDirUrl);
            if (resolvedUrl != null) {
               rootDirUrl = resolvedUrl;
            }

            UrlResource urlResource = new UrlResource(rootDirUrl);
            if (this.useCaches != null) {
               urlResource.setUseCaches(this.useCaches);
            }

            rootDirResource = urlResource;
         }

         if (rootDirUrl.getProtocol().startsWith("vfs")) {
            result.addAll(PathMatchingResourcePatternResolver.VfsResourceMatchingDelegate.findMatchingResources(rootDirUrl, subPattern, this.getPathMatcher()));
         } else if (!ResourceUtils.isJarURL(rootDirUrl) && !this.isJarResource(rootDirResource)) {
            result.addAll(this.doFindPathMatchingFileResources(rootDirResource, subPattern));
         } else {
            result.addAll(this.doFindPathMatchingJarResources(rootDirResource, rootDirUrl, subPattern));
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Resolved location pattern [" + locationPattern + "] to resources " + result);
      }

      return result.toArray(EMPTY_RESOURCE_ARRAY);
   }

   protected String determineRootDir(String location) {
      int prefixEnd = location.indexOf(58) + 1;
      int rootDirEnd = location.length();

      while (rootDirEnd > prefixEnd && this.getPathMatcher().isPattern(location.substring(prefixEnd, rootDirEnd))) {
         rootDirEnd = location.lastIndexOf(47, rootDirEnd - 2) + 1;
      }

      if (rootDirEnd == 0) {
         rootDirEnd = prefixEnd;
      }

      return location.substring(0, rootDirEnd);
   }

   protected Resource resolveRootDirResource(Resource original) throws IOException {
      return original;
   }

   protected boolean isJarResource(Resource resource) throws IOException {
      return false;
   }

   protected Set<Resource> doFindPathMatchingJarResources(Resource rootDirResource, URL rootDirUrl, String subPattern) throws IOException {
      String jarFileUrl = null;
      String rootEntryPath = "";
      String urlFile = rootDirUrl.getFile();
      int separatorIndex = urlFile.indexOf("*/");
      if (separatorIndex == -1) {
         separatorIndex = urlFile.indexOf("!/");
      }

      if (separatorIndex >= 0) {
         jarFileUrl = urlFile.substring(0, separatorIndex);
         rootEntryPath = urlFile.substring(separatorIndex + 2);
         NavigableSet<String> entriesCache = this.jarEntriesCache.get(jarFileUrl);
         if (entriesCache != null) {
            Set<Resource> result = new LinkedHashSet<>(64);
            rootEntryPath = rootEntryPath.replace("!/", "/");
            boolean rootEntryPathFound = false;

            for (String entryPath : entriesCache.tailSet(rootEntryPath, false)) {
               if (!entryPath.startsWith(rootEntryPath)) {
                  break;
               }

               rootEntryPathFound = true;
               String relativePath = entryPath.substring(rootEntryPath.length());
               if (this.getPathMatcher().match(subPattern, relativePath)) {
                  result.add(rootDirResource.createRelative(relativePath));
               }
            }

            if (rootEntryPathFound) {
               return result;
            }
         }
      }

      JarFile jarFile;
      boolean closeJarFile;
      if (rootDirUrl.openConnection() instanceof JarURLConnection jarCon) {
         if (this.useCaches != null) {
            jarCon.setUseCaches(this.useCaches);
         }

         try {
            jarFile = jarCon.getJarFile();
            jarFileUrl = jarCon.getJarFileURL().toExternalForm();
            JarEntry jarEntry = jarCon.getJarEntry();
            rootEntryPath = jarEntry != null ? jarEntry.getName() : "";
            closeJarFile = !jarCon.getUseCaches();
         } catch (ZipException | FileNotFoundException | NoSuchFileException ex) {
            return Collections.emptySet();
         }
      } else {
         try {
            if (jarFileUrl != null) {
               jarFile = this.getJarFile(jarFileUrl);
            } else {
               jarFile = new JarFile(urlFile);
               jarFileUrl = urlFile;
               rootEntryPath = "";
            }

            closeJarFile = true;
         } catch (ZipException ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Skipping invalid jar class path entry [" + urlFile + "]");
            }

            return Collections.emptySet();
         }
      }

      try {
         if (logger.isTraceEnabled()) {
            logger.trace("Looking for matching resources in jar file [" + jarFileUrl + "]");
         }

         if (StringUtils.hasLength(rootEntryPath) && !rootEntryPath.endsWith("/")) {
            rootEntryPath = rootEntryPath + "/";
         }

         Set<Resource> result = new LinkedHashSet<>(64);
         NavigableSet<String> entriesCache = new TreeSet<>();
         Enumeration<JarEntry> entries = jarFile.entries();

         while (entries.hasMoreElements()) {
            entriesCache.add(entries.nextElement().getName());
         }

         for (String entryPath : entriesCache) {
            if (entryPath.startsWith(rootEntryPath)) {
               String relativePath = entryPath.substring(rootEntryPath.length());
               if (this.getPathMatcher().match(subPattern, relativePath)) {
                  result.add(rootDirResource.createRelative(relativePath));
               }
            }
         }

         if (this.useCaches == null || this.useCaches) {
            this.jarEntriesCache.put(jarFileUrl, entriesCache);
         }

         return result;
      } finally {
         if (closeJarFile) {
            jarFile.close();
         }
      }
   }

   protected JarFile getJarFile(String jarFileUrl) throws IOException {
      if (jarFileUrl.startsWith("file:")) {
         try {
            return new JarFile(ResourceUtils.toURI(jarFileUrl).getSchemeSpecificPart());
         } catch (URISyntaxException ex) {
            return new JarFile(jarFileUrl.substring("file:".length()));
         }
      } else {
         return new JarFile(jarFileUrl);
      }
   }

   protected Set<Resource> doFindPathMatchingFileResources(Resource rootDirResource, String subPattern) throws IOException {
      Set<Resource> result = new LinkedHashSet<>(64);

      URI rootDirUri;
      try {
         rootDirUri = rootDirResource.getURI();
      } catch (Exception ex) {
         if (logger.isWarnEnabled()) {
            logger.warn("Failed to resolve directory [%s] as URI: %s".formatted(rootDirResource, ex));
         }

         return result;
      }

      Path rootPath = null;
      if (rootDirUri.isAbsolute() && !rootDirUri.isOpaque()) {
         try {
            try {
               rootPath = Path.of(rootDirUri);
            } catch (FileSystemNotFoundException ex) {
               FileSystems.newFileSystem(rootDirUri, Map.of(), ClassUtils.getDefaultClassLoader());
               rootPath = Path.of(rootDirUri);
            }
         } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Failed to resolve %s in file system: %s".formatted(rootDirUri, ex));
            }
         }
      }

      if (rootPath == null) {
         try {
            rootPath = Path.of(rootDirResource.getFile().getAbsolutePath());
         } catch (FileNotFoundException ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot search for matching files underneath " + rootDirResource + " in the file system: " + ex.getMessage());
            }

            return result;
         } catch (Exception ex) {
            if (logger.isInfoEnabled()) {
               logger.info("Failed to resolve " + rootDirResource + " in the file system: " + ex);
            }

            return result;
         }
      }

      if (!Files.exists(rootPath)) {
         if (logger.isDebugEnabled()) {
            logger.debug("Skipping search for files matching pattern [%s]: directory [%s] does not exist".formatted(subPattern, rootPath.toAbsolutePath()));
         }

         return result;
      } else {
         String rootDir = StringUtils.cleanPath(rootPath.toString());
         if (!rootDir.endsWith("/")) {
            rootDir = rootDir + "/";
         }

         Path rootPathForPattern = rootPath;
         String resourcePattern = rootDir + StringUtils.cleanPath(subPattern);
         Predicate<Path> isMatchingFile = path -> !path.equals(rootPathForPattern)
            && this.getPathMatcher().match(resourcePattern, StringUtils.cleanPath(path.toString()));
         if (logger.isTraceEnabled()) {
            logger.trace("Searching directory [%s] for files matching pattern [%s]".formatted(rootPath.toAbsolutePath(), subPattern));
         }

         try (Stream<Path> files = Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)) {
            files.filter(isMatchingFile).sorted().map(FileSystemResource::new).forEach(result::add);
         } catch (Exception ex) {
            if (logger.isWarnEnabled()) {
               logger.warn("Failed to search in directory [%s] for files matching pattern [%s]: %s".formatted(rootPath.toAbsolutePath(), subPattern, ex));
            }
         }

         return result;
      }
   }

   protected Set<Resource> findAllModulePathResources(String locationPattern) throws IOException {
      Set<Resource> result = new LinkedHashSet<>(64);
      if (NativeDetector.inNativeImage()) {
         return result;
      }

      String resourcePattern = stripLeadingSlash(locationPattern);
      Predicate<String> resourcePatternMatches = this.getPathMatcher().isPattern(resourcePattern)
         ? path -> this.getPathMatcher().match(resourcePattern, path)
         : resourcePattern::equals;

      try {
         ModuleLayer.boot().configuration().modules().stream().filter(isNotSystemModule).forEach(resolvedModule -> {
            try {
               try (
                  ModuleReader moduleReader = resolvedModule.reference().open();
                  Stream<String> names = moduleReader.list();
               ) {
                  names.filter(resourcePatternMatches).map(name -> this.findResource(moduleReader, name)).filter(Objects::nonNull).forEach(result::add);
               }
            } catch (IOException ex) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Failed to read contents of module [%s]".formatted(resolvedModule), ex);
               }

               throw new UncheckedIOException(ex);
            }
         });
      } catch (UncheckedIOException ex) {
         throw ex.getCause();
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Resolved module-path location pattern [%s] to resources %s".formatted(resourcePattern, result));
      }

      return result;
   }

   private @Nullable Resource findResource(ModuleReader moduleReader, String name) {
      try {
         return moduleReader.find(name).map(this::convertModuleSystemURI).orElse(null);
      } catch (Exception ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to find resource [%s] in module path".formatted(name), ex);
         }

         return null;
      }
   }

   private Resource convertModuleSystemURI(URI uri) {
      return "file".equals(uri.getScheme()) ? new FileSystemResource(uri.getPath()) : UrlResource.from(uri);
   }

   private static String stripLeadingSlash(String path) {
      return path.startsWith("/") ? path.substring(1) : path;
   }

   private static boolean checkPathWithinPackage(String path) {
      return path.contains("/") && !path.contains("!/");
   }

   static {
      try {
         Class<?> fileLocatorClass = ClassUtils.forName("org.eclipse.core.runtime.FileLocator", PathMatchingResourcePatternResolver.class.getClassLoader());
         equinoxResolveMethod = fileLocatorClass.getMethod("resolve", URL.class);
         logger.trace("Found Equinox FileLocator for OSGi bundle URL resolution");
      } catch (Throwable ex) {
         equinoxResolveMethod = null;
      }
   }

   private record ClassPathManifestEntry(Resource resource, @Nullable Resource alternative) {
      private static final String JARFILE_URL_PREFIX = "jar:file:";

      static PathMatchingResourcePatternResolver.ClassPathManifestEntry of(File file, @Nullable Boolean useCaches) throws MalformedURLException {
         String path = fixPath(file.getAbsolutePath());
         Resource resource = asJarFileResource(path, useCaches);
         if (!resource.exists()) {
            String encodedPath = fixPath(StringUtils.replace(file.getAbsolutePath(), "%", "%25"));
            if (!encodedPath.equals(path)) {
               Resource encodedResource = asJarFileResource(encodedPath, useCaches);
               if (encodedResource.exists()) {
                  path = encodedPath;
                  resource = encodedResource;
               }
            }
         }

         Resource alternative = createAlternative(path, useCaches);
         return new PathMatchingResourcePatternResolver.ClassPathManifestEntry(resource, alternative);
      }

      private static String fixPath(String path) {
         int prefixIndex = path.indexOf(58);
         if (prefixIndex == 1) {
            path = "/" + StringUtils.capitalize(path);
         }

         return StringUtils.replace(path, "#", "%23");
      }

      private static @Nullable Resource createAlternative(String path, @Nullable Boolean useCaches) {
         try {
            String alternativePath = path.startsWith("/") ? path.substring(1) : "/" + path;
            return asJarFileResource(alternativePath, useCaches);
         } catch (MalformedURLException ex) {
            return null;
         }
      }

      private static Resource asJarFileResource(String path, @Nullable Boolean useCaches) throws MalformedURLException {
         UrlResource resource = new UrlResource("jar:file:" + path + "!/");
         if (useCaches != null) {
            resource.setUseCaches(useCaches);
         }

         return resource;
      }
   }

   private static class PatternVirtualFileVisitor implements InvocationHandler {
      private final String subPattern;
      private final PathMatcher pathMatcher;
      private final String rootPath;
      private final Set<Resource> resources = new LinkedHashSet<>(64);

      public PatternVirtualFileVisitor(String rootPath, String subPattern, PathMatcher pathMatcher) {
         this.subPattern = subPattern;
         this.pathMatcher = pathMatcher;
         this.rootPath = !rootPath.isEmpty() && !rootPath.endsWith("/") ? rootPath + "/" : rootPath;
      }

      @Override
      public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         String methodName = method.getName();
         if (Object.class == method.getDeclaringClass()) {
            switch (methodName) {
               case "equals":
                  return proxy == args[0];
               case "hashCode":
                  return System.identityHashCode(proxy);
            }
         }
         return switch (methodName) {
            case "getAttributes" -> this.getAttributes();
            case "visit" -> {
               this.visit(args[0]);
               yield null;
            }
            case "toString" -> this.toString();
            default -> throw new IllegalStateException("Unexpected method invocation: " + method);
         };
      }

      public void visit(Object vfsResource) {
         if (this.pathMatcher.match(this.subPattern, VfsPatternUtils.getPath(vfsResource).substring(this.rootPath.length()))) {
            this.resources.add(new VfsResource(vfsResource));
         }
      }

      public @Nullable Object getAttributes() {
         return VfsPatternUtils.getVisitorAttributes();
      }

      public Set<Resource> getResources() {
         return this.resources;
      }

      public int size() {
         return this.resources.size();
      }

      @Override
      public String toString() {
         return "sub-pattern: " + this.subPattern + ", resources: " + this.resources;
      }
   }

   private static class VfsResourceMatchingDelegate {
      public static Set<Resource> findMatchingResources(URL rootDirUrl, String locationPattern, PathMatcher pathMatcher) throws IOException {
         Object root = VfsPatternUtils.findRoot(rootDirUrl);
         PathMatchingResourcePatternResolver.PatternVirtualFileVisitor visitor = new PathMatchingResourcePatternResolver.PatternVirtualFileVisitor(
            VfsPatternUtils.getPath(root), locationPattern, pathMatcher
         );
         VfsPatternUtils.visit(root, visitor);
         return visitor.getResources();
      }
   }
}
