package org.springframework.beans.factory.parsing;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class ImportDefinition implements BeanMetadataElement {
   private final String importedResource;
   private final Resource @Nullable [] actualResources;
   private final @Nullable Object source;

   public ImportDefinition(String importedResource) {
      this(importedResource, null, null);
   }

   public ImportDefinition(String importedResource, @Nullable Object source) {
      this(importedResource, null, source);
   }

   public ImportDefinition(String importedResource, Resource @Nullable [] actualResources, @Nullable Object source) {
      Assert.notNull(importedResource, "Imported resource must not be null");
      this.importedResource = importedResource;
      this.actualResources = actualResources;
      this.source = source;
   }

   public final String getImportedResource() {
      return this.importedResource;
   }

   public final Resource @Nullable [] getActualResources() {
      return this.actualResources;
   }

   @Override
   public final @Nullable Object getSource() {
      return this.source;
   }
}
