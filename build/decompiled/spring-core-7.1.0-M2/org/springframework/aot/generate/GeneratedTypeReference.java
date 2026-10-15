package org.springframework.aot.generate;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.AbstractTypeReference;
import org.springframework.javapoet.ClassName;
import org.springframework.util.Assert;

public final class GeneratedTypeReference extends AbstractTypeReference {
   private final ClassName className;

   private GeneratedTypeReference(ClassName className) {
      super(className.packageName(), className.simpleName(), safeCreate(className.enclosingClassName()));
      this.className = className;
   }

   private static @Nullable GeneratedTypeReference safeCreate(@Nullable ClassName className) {
      return className != null ? new GeneratedTypeReference(className) : null;
   }

   public static GeneratedTypeReference of(ClassName className) {
      Assert.notNull(className, "ClassName must not be null");
      return new GeneratedTypeReference(className);
   }

   @Override
   public String getCanonicalName() {
      return this.className.canonicalName();
   }

   @Override
   protected boolean isPrimitive() {
      return this.className.isPrimitive();
   }
}
