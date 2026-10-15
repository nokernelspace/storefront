package org.springframework.core.type;

import org.jspecify.annotations.Nullable;

public interface ClassMetadata {
   String getClassName();

   boolean isInterface();

   boolean isAnnotation();

   boolean isAbstract();

   default boolean isConcrete() {
      return !this.isInterface() && !this.isAbstract();
   }

   boolean isFinal();

   boolean isIndependent();

   default boolean hasEnclosingClass() {
      return this.getEnclosingClassName() != null;
   }

   @Nullable String getEnclosingClassName();

   default boolean hasSuperClass() {
      return this.getSuperClassName() != null;
   }

   @Nullable String getSuperClassName();

   String[] getInterfaceNames();

   String[] getMemberClassNames();
}
