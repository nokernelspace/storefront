package org.springframework.core.type.filter;

import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

public abstract class AbstractTypeHierarchyTraversingFilter implements TypeFilter {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final boolean considerInherited;
   private final boolean considerInterfaces;

   protected AbstractTypeHierarchyTraversingFilter(boolean considerInherited, boolean considerInterfaces) {
      this.considerInherited = considerInherited;
      this.considerInterfaces = considerInterfaces;
   }

   @Override
   public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
      if (this.matchSelf(metadataReader)) {
         return true;
      }

      ClassMetadata metadata = metadataReader.getClassMetadata();
      if (this.matchClassName(metadata.getClassName())) {
         return true;
      }

      if (this.considerInherited) {
         String superClassName = metadata.getSuperClassName();
         if (superClassName != null) {
            Boolean superClassMatch = this.matchSuperClass(superClassName);
            if (superClassMatch != null) {
               if (superClassMatch) {
                  return true;
               }
            } else {
               try {
                  if (this.match(superClassName, metadataReaderFactory)) {
                     return true;
                  }
               } catch (IOException ex) {
                  if (this.logger.isDebugEnabled()) {
                     this.logger.debug("Could not read superclass [" + superClassName + "] of type-filtered class [" + metadata.getClassName() + "]");
                  }
               }
            }
         }
      }

      if (this.considerInterfaces) {
         for (String ifc : metadata.getInterfaceNames()) {
            Boolean interfaceMatch = this.matchInterface(ifc);
            if (interfaceMatch != null) {
               if (interfaceMatch) {
                  return true;
               }
            } else {
               try {
                  if (this.match(ifc, metadataReaderFactory)) {
                     return true;
                  }
               } catch (IOException ex) {
                  if (this.logger.isDebugEnabled()) {
                     this.logger.debug("Could not read interface [" + ifc + "] for type-filtered class [" + metadata.getClassName() + "]");
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean match(String className, MetadataReaderFactory metadataReaderFactory) throws IOException {
      return this.match(metadataReaderFactory.getMetadataReader(className), metadataReaderFactory);
   }

   protected boolean matchSelf(MetadataReader metadataReader) {
      return false;
   }

   protected boolean matchClassName(String className) {
      return false;
   }

   protected @Nullable Boolean matchSuperClass(String superClassName) {
      return null;
   }

   protected @Nullable Boolean matchInterface(String interfaceName) {
      return null;
   }
}
