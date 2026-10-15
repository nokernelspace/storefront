package org.springframework.beans.factory.xml;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ResourceEntityResolver extends DelegatingEntityResolver {
   private static final Log logger = LogFactory.getLog(ResourceEntityResolver.class);
   private final ResourceLoader resourceLoader;

   public ResourceEntityResolver(ResourceLoader resourceLoader) {
      super(resourceLoader.getClassLoader());
      this.resourceLoader = resourceLoader;
   }

   @Override
   public @Nullable InputSource resolveEntity(@Nullable String publicId, @Nullable String systemId) throws SAXException, IOException {
      InputSource source = super.resolveEntity(publicId, systemId);
      if (source == null && systemId != null) {
         String resourcePath = null;

         try {
            String decodedSystemId = URLDecoder.decode(systemId, StandardCharsets.UTF_8);
            String givenUrl = ResourceUtils.toURL(decodedSystemId).toString();
            String systemRootUrl = new File("").toURI().toURL().toString();
            if (givenUrl.startsWith(systemRootUrl)) {
               resourcePath = givenUrl.substring(systemRootUrl.length());
            }
         } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Could not resolve XML entity [" + systemId + "] against system root URL", ex);
            }

            resourcePath = systemId;
         }

         if (resourcePath != null) {
            if (logger.isTraceEnabled()) {
               logger.trace("Trying to locate XML entity [" + systemId + "] as resource [" + resourcePath + "]");
            }

            Resource resource = this.resourceLoader.getResource(resourcePath);
            source = new InputSource(resource.getInputStream());
            source.setPublicId(publicId);
            source.setSystemId(systemId);
            if (logger.isDebugEnabled()) {
               logger.debug("Found XML entity [" + systemId + "]: " + resource);
            }
         } else if (systemId.endsWith(".dtd") || systemId.endsWith(".xsd")) {
            source = this.resolveSchemaEntity(publicId, systemId);
         }
      }

      return source;
   }

   protected @Nullable InputSource resolveSchemaEntity(@Nullable String publicId, String systemId) {
      String url = systemId;
      if (url.startsWith("http:")) {
         url = "https:" + url.substring(5);
      }

      if (logger.isWarnEnabled()) {
         logger.warn("DTD/XSD XML entity [" + systemId + "] not found, falling back to remote https resolution");
      }

      InputSource source;
      try {
         source = new InputSource(ResourceUtils.toURL(url).openStream());
         source.setPublicId(publicId);
         source.setSystemId(systemId);
      } catch (IOException ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Could not resolve XML entity [" + systemId + "] through URL [" + url + "]", ex);
         }

         source = null;
      }

      return source;
   }
}
