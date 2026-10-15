package org.springframework.core;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public abstract class AttributeAccessorSupport implements AttributeAccessor, Serializable {
   private final Map<String, Object> attributes = new LinkedHashMap<>();

   @Override
   public void setAttribute(String name, @Nullable Object value) {
      Assert.notNull(name, "Name must not be null");
      if (value != null) {
         this.attributes.put(name, value);
      } else {
         this.removeAttribute(name);
      }
   }

   @Override
   public @Nullable Object getAttribute(String name) {
      Assert.notNull(name, "Name must not be null");
      return this.attributes.get(name);
   }

   @Override
   public <T> T computeAttribute(String name, Function<String, T> computeFunction) {
      Assert.notNull(name, "Name must not be null");
      Assert.notNull(computeFunction, "Compute function must not be null");
      Object value = this.attributes.computeIfAbsent(name, computeFunction);
      Assert.state(value != null, () -> String.format("Compute function must not return null for attribute named '%s'", name));
      return (T)value;
   }

   @Override
   public @Nullable Object removeAttribute(String name) {
      Assert.notNull(name, "Name must not be null");
      return this.attributes.remove(name);
   }

   @Override
   public boolean hasAttribute(String name) {
      Assert.notNull(name, "Name must not be null");
      return this.attributes.containsKey(name);
   }

   @Override
   public String[] attributeNames() {
      return StringUtils.toStringArray(this.attributes.keySet());
   }

   protected void copyAttributesFrom(AttributeAccessor source) {
      Assert.notNull(source, "Source must not be null");
      String[] attributeNames = source.attributeNames();

      for (String attributeName : attributeNames) {
         this.setAttribute(attributeName, source.getAttribute(attributeName));
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof AttributeAccessorSupport that && this.attributes.equals(that.attributes);
   }

   @Override
   public int hashCode() {
      return this.attributes.hashCode();
   }
}
