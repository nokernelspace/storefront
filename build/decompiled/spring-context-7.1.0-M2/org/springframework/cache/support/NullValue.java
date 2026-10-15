package org.springframework.cache.support;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;

public final class NullValue implements Serializable {
   public static final Object INSTANCE = new NullValue();
   private static final long serialVersionUID = 1L;

   private NullValue() {
   }

   private Object readResolve() {
      return INSTANCE;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other == null;
   }

   @Override
   public int hashCode() {
      return NullValue.class.hashCode();
   }

   @Override
   public String toString() {
      return "null";
   }
}
