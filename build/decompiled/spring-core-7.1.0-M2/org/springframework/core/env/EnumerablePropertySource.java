package org.springframework.core.env;

import org.springframework.util.ObjectUtils;

public abstract class EnumerablePropertySource<T> extends PropertySource<T> {
   public EnumerablePropertySource(String name, T source) {
      super(name, source);
   }

   protected EnumerablePropertySource(String name) {
      super(name);
   }

   @Override
   public boolean containsProperty(String name) {
      return ObjectUtils.containsElement(this.getPropertyNames(), name);
   }

   public abstract String[] getPropertyNames();
}
