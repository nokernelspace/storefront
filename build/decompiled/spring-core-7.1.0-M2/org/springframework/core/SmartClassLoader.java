package org.springframework.core;

import java.security.ProtectionDomain;
import org.jspecify.annotations.Nullable;

public interface SmartClassLoader {
   default boolean isClassReloadable(Class<?> clazz) {
      return false;
   }

   default ClassLoader getOriginalClassLoader() {
      return (ClassLoader)this;
   }

   default Class<?> publicDefineClass(String name, byte[] b, @Nullable ProtectionDomain protectionDomain) {
      throw new UnsupportedOperationException();
   }
}
