package org.springframework.cache.interceptor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class SimpleKey implements Serializable {
   public static final SimpleKey EMPTY = new SimpleKey();
   private final @Nullable Object[] params;
   private transient int hashCode;

   public SimpleKey(@Nullable Object... elements) {
      Assert.notNull(elements, "Elements must not be null");
      this.params = (Object[])elements.clone();
      this.hashCode = calculateHash(this.params);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof SimpleKey that && Arrays.deepEquals(this.params, that.params);
   }

   @Override
   public final int hashCode() {
      return this.hashCode;
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " " + Arrays.deepToString(this.params);
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.hashCode = calculateHash(this.params);
   }

   private static int calculateHash(@Nullable Object[] params) {
      int hash = Arrays.deepHashCode(params);
      hash = (hash ^ hash >>> 16) * -2048144789;
      hash = (hash ^ hash >>> 13) * -1028477387;
      return hash ^ hash >>> 16;
   }
}
