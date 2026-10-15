package org.springframework.beans.factory.support;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class ManagedArray extends ManagedList<Object> {
   volatile @Nullable Class<?> resolvedElementType;

   public ManagedArray(String elementTypeName, int size) {
      super(size);
      Assert.notNull(elementTypeName, "elementTypeName must not be null");
      this.setElementTypeName(elementTypeName);
   }
}
