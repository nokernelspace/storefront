package org.springframework.beans.factory.xml;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DelegatingEntityResolver implements EntityResolver {
   public static final String DTD_SUFFIX = ".dtd";
   public static final String XSD_SUFFIX = ".xsd";
   private final EntityResolver dtdResolver;
   private final EntityResolver schemaResolver;

   public DelegatingEntityResolver(@Nullable ClassLoader classLoader) {
      this.dtdResolver = new BeansDtdResolver();
      this.schemaResolver = new PluggableSchemaResolver(classLoader);
   }

   public DelegatingEntityResolver(EntityResolver dtdResolver, EntityResolver schemaResolver) {
      Assert.notNull(dtdResolver, "'dtdResolver' is required");
      Assert.notNull(schemaResolver, "'schemaResolver' is required");
      this.dtdResolver = dtdResolver;
      this.schemaResolver = schemaResolver;
   }

   @Override
   public @Nullable InputSource resolveEntity(@Nullable String publicId, @Nullable String systemId) throws SAXException, IOException {
      if (systemId != null) {
         if (systemId.endsWith(".dtd")) {
            return this.dtdResolver.resolveEntity(publicId, systemId);
         }

         if (systemId.endsWith(".xsd")) {
            return this.schemaResolver.resolveEntity(publicId, systemId);
         }
      }

      return null;
   }

   @Override
   public String toString() {
      return "EntityResolver delegating .xsd to " + this.schemaResolver + " and .dtd to " + this.dtdResolver;
   }
}
