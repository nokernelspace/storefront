package org.springframework.context.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.core.type.AnnotationMetadata;

public interface DeferredImportSelector extends ImportSelector {
   default @Nullable Class<? extends DeferredImportSelector.Group> getImportGroup() {
      return null;
   }

   interface Group {
      void process(AnnotationMetadata metadata, DeferredImportSelector selector);

      Iterable<DeferredImportSelector.Group.Entry> selectImports();

      class Entry {
         private final AnnotationMetadata metadata;
         private final String importClassName;

         public Entry(AnnotationMetadata metadata, String importClassName) {
            this.metadata = metadata;
            this.importClassName = importClassName;
         }

         public AnnotationMetadata getMetadata() {
            return this.metadata;
         }

         public String getImportClassName() {
            return this.importClassName;
         }

         @Override
         public boolean equals(@Nullable Object other) {
            if (this == other) {
               return true;
            } else if (other != null && this.getClass() == other.getClass()) {
               DeferredImportSelector.Group.Entry entry = (DeferredImportSelector.Group.Entry)other;
               return this.metadata.equals(entry.metadata) && this.importClassName.equals(entry.importClassName);
            } else {
               return false;
            }
         }

         @Override
         public int hashCode() {
            return this.metadata.hashCode() * 31 + this.importClassName.hashCode();
         }

         @Override
         public String toString() {
            return this.importClassName;
         }
      }
   }
}
