package org.springframework.beans.factory.support;

import org.jspecify.annotations.Nullable;

final class NullBean {
   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other == null;
   }

   @Override
   public int hashCode() {
      return NullBean.class.hashCode();
   }

   @Override
   public String toString() {
      return "null";
   }
}
