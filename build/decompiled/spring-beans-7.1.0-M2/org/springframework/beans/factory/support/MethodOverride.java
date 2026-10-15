package org.springframework.beans.factory.support;

import java.lang.reflect.Method;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public abstract class MethodOverride implements BeanMetadataElement {
   private final String methodName;
   private boolean overloaded = true;
   private @Nullable Object source;

   protected MethodOverride(String methodName) {
      Assert.notNull(methodName, "Method name must not be null");
      this.methodName = methodName;
   }

   public String getMethodName() {
      return this.methodName;
   }

   protected void setOverloaded(boolean overloaded) {
      this.overloaded = overloaded;
   }

   protected boolean isOverloaded() {
      return this.overloaded;
   }

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public abstract boolean matches(Method method);

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof MethodOverride that && this.methodName.equals(that.methodName) && ObjectUtils.nullSafeEquals(this.source, that.source);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.methodName, this.source);
   }
}
