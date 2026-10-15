package org.springframework.aot.hint;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public abstract class AbstractTypeReference implements TypeReference {
   private final String packageName;
   private final String simpleName;
   private final @Nullable TypeReference enclosingType;

   protected AbstractTypeReference(String packageName, String simpleName, @Nullable TypeReference enclosingType) {
      this.packageName = packageName;
      this.simpleName = simpleName;
      this.enclosingType = enclosingType;
   }

   @Override
   public String getName() {
      TypeReference enclosingType = this.getEnclosingType();
      String simpleName = this.getSimpleName();
      return enclosingType != null ? enclosingType.getName() + "$" + simpleName : this.addPackageIfNecessary(simpleName);
   }

   @Override
   public String getPackageName() {
      return this.packageName;
   }

   @Override
   public String getSimpleName() {
      return this.simpleName;
   }

   @Override
   public @Nullable TypeReference getEnclosingType() {
      return this.enclosingType;
   }

   protected String addPackageIfNecessary(String part) {
      return !this.packageName.isEmpty() && (!this.packageName.equals("java.lang") || !this.isPrimitive()) ? this.packageName + "." + part : part;
   }

   protected abstract boolean isPrimitive();

   public int compareTo(TypeReference other) {
      return this.getCanonicalName().compareToIgnoreCase(other.getCanonicalName());
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof TypeReference that && this.getCanonicalName().equals(that.getCanonicalName());
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.getCanonicalName());
   }

   @Override
   public String toString() {
      return this.getCanonicalName();
   }
}
