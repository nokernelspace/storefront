package org.springframework.web.context.support;

import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

public class ServletContextResourcePatternResolver extends PathMatchingResourcePatternResolver {
   private static final Log logger = LogFactory.getLog(ServletContextResourcePatternResolver.class);

   public ServletContextResourcePatternResolver(ServletContext servletContext) {
      super(new ServletContextResourceLoader(servletContext));
   }

   public ServletContextResourcePatternResolver(ResourceLoader resourceLoader) {
      super(resourceLoader);
   }

   protected Set<Resource> doFindPathMatchingFileResources(Resource rootDirResource, String subPattern) throws IOException {
      if (rootDirResource instanceof ServletContextResource scResource) {
         ServletContext sc = scResource.getServletContext();
         String fullPattern = scResource.getPath() + subPattern;
         Set<Resource> result = new LinkedHashSet<>(8);
         this.doRetrieveMatchingServletContextResources(sc, fullPattern, scResource.getPath(), result);
         return result;
      } else {
         return super.doFindPathMatchingFileResources(rootDirResource, subPattern);
      }
   }

   protected void doRetrieveMatchingServletContextResources(ServletContext servletContext, String fullPattern, String dir, Set<Resource> result) throws IOException {
      Set<String> candidates = servletContext.getResourcePaths(dir);
      if (candidates != null) {
         boolean dirDepthNotFixed = fullPattern.contains("**");
         int jarFileSep = fullPattern.indexOf("!/");
         String jarFilePath = null;
         String pathInJarFile = null;
         if (jarFileSep > 0 && jarFileSep + "!/".length() < fullPattern.length()) {
            jarFilePath = fullPattern.substring(0, jarFileSep);
            pathInJarFile = fullPattern.substring(jarFileSep + "!/".length());
         }

         for (String currPath : candidates) {
            if (!currPath.startsWith(dir)) {
               int dirIndex = currPath.indexOf(dir);
               if (dirIndex != -1) {
                  currPath = currPath.substring(dirIndex);
               }
            }

            if (currPath.endsWith("/")
               && (dirDepthNotFixed || StringUtils.countOccurrencesOf(currPath, "/") <= StringUtils.countOccurrencesOf(fullPattern, "/"))) {
               this.doRetrieveMatchingServletContextResources(servletContext, fullPattern, currPath, result);
            }

            if (jarFilePath != null && this.getPathMatcher().match(jarFilePath, currPath)) {
               String absoluteJarPath = servletContext.getRealPath(currPath);
               if (absoluteJarPath != null) {
                  this.doRetrieveMatchingJarEntries(absoluteJarPath, pathInJarFile, result);
               }
            }

            if (this.getPathMatcher().match(fullPattern, currPath)) {
               result.add(new ServletContextResource(servletContext, currPath));
            }
         }
      }
   }

   private void doRetrieveMatchingJarEntries(String jarFilePath, String entryPattern, Set<Resource> result) {
      if (logger.isDebugEnabled()) {
         logger.debug("Searching jar file [" + jarFilePath + "] for entries matching [" + entryPattern + "]");
      }

      try (JarFile jarFile = new JarFile(jarFilePath)) {
         Enumeration<JarEntry> entries = jarFile.entries();

         while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryPath = entry.getName();
            if (this.getPathMatcher().match(entryPattern, entryPath)) {
               result.add(new UrlResource("jar", "file:" + jarFilePath + "!/" + entryPath));
            }
         }
      } catch (IOException ex) {
         if (logger.isWarnEnabled()) {
            logger.warn("Cannot search for matching resources in jar file [" + jarFilePath + "] because the jar cannot be opened through the file system", ex);
         }
      }
   }
}
