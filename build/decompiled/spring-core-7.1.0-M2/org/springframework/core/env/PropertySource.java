package org.springframework.core.env;

import java.util.Objects;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public abstract class PropertySource<T> {
   protected final Log logger = LogFactory.getLog(this.getClass());
   protected final String name;
   protected final T source;

   public PropertySource(String name, T source) {
      Assert.hasText(name, "Property source name must contain at least one character");
      Assert.notNull(source, "Property source must not be null");
      this.name = name;
      this.source = source;
   }

   public PropertySource(String name) {
      this(name, (T)(new Object()));
   }

   public String getName() {
      return this.name;
   }

   public T getSource() {
      return this.source;
   }

   public boolean containsProperty(String name) {
      return this.getProperty(name) != null;
   }

   public abstract @Nullable Object getProperty(String name);

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof PropertySource<?> that && ObjectUtils.nullSafeEquals(this.getName(), that.getName());
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(this.getName());
   }

   @Override
   public String toString() {
      return this.logger.isDebugEnabled()
         ? this.getClass().getSimpleName() + "@" + System.identityHashCode(this) + " {name='" + this.getName() + "', properties=" + this.getSource() + "}"
         : this.getClass().getSimpleName() + " {name='" + this.getName() + "'}";
   }

   public static PropertySource<?> named(String name) {
      return new PropertySource.ComparisonPropertySource(name);
   }

   static class ComparisonPropertySource extends PropertySource.StubPropertySource {
      private static final String USAGE_ERROR = "ComparisonPropertySource instances are for use with collection comparison only";

      public ComparisonPropertySource(String name) {
         super(name);
      }

      @Override
      public Object getSource() {
         throw new UnsupportedOperationException("ComparisonPropertySource instances are for use with collection comparison only");
      }

      @Override
      public boolean containsProperty(String name) {
         throw new UnsupportedOperationException("ComparisonPropertySource instances are for use with collection comparison only");
      }

      @Override
      public @Nullable String getProperty(String name) {
         throw new UnsupportedOperationException("ComparisonPropertySource instances are for use with collection comparison only");
      }
   }

   public static class StubPropertySource extends PropertySource<Object> {
      public StubPropertySource(String name) {
         super(name);
      }

      public @Nullable String getProperty(String name) {
         return null;
      }
   }
}
