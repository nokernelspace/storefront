package org.springframework.cache.interceptor;

import org.jspecify.annotations.Nullable;

public class CachePutOperation extends CacheOperation {
   private final @Nullable String unless;

   public CachePutOperation(CachePutOperation.Builder b) {
      super(b);
      this.unless = b.unless;
   }

   public @Nullable String getUnless() {
      return this.unless;
   }

   public static class Builder extends CacheOperation.Builder {
      private @Nullable String unless;

      public void setUnless(String unless) {
         this.unless = unless;
      }

      @Override
      protected StringBuilder getOperationDescription() {
         StringBuilder sb = super.getOperationDescription();
         sb.append(" | unless='");
         sb.append(this.unless);
         sb.append('\'');
         return sb;
      }

      public CachePutOperation build() {
         return new CachePutOperation(this);
      }
   }
}
