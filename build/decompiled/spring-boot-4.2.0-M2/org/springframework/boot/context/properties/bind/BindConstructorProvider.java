package org.springframework.boot.context.properties.bind;

import java.lang.reflect.Constructor;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface BindConstructorProvider {
   BindConstructorProvider DEFAULT = new DefaultBindConstructorProvider();

   default @Nullable Constructor<?> getBindConstructor(Class<?> type, boolean isNestedConstructorBinding) {
      return this.getBindConstructor(Bindable.of(type), isNestedConstructorBinding);
   }

   @Nullable Constructor<?> getBindConstructor(Bindable<?> bindable, boolean isNestedConstructorBinding);
}
