package org.springframework.cache.interceptor;

import org.jspecify.annotations.Nullable;

public class CacheableOperation extends CacheOperation {
   private final @Nullable String unless;
   private final boolean sync;

   public CacheableOperation(CacheableOperation.Builder b) {
      super(b);
      this.unless = b.unless;
      this.sync = b.sync;
   }

   public @Nullable String getUnless() {
      return this.unless;
   }

   public boolean isSync() {
      return this.sync;
   }

   public static class Builder extends CacheOperation.Builder {
      private @Nullable String unless;
      private boolean sync;

      public void setUnless(String unless) {
         this.unless = unless;
      }

      public void setSync(boolean sync) {
         this.sync = sync;
      }

      @Override
      protected StringBuilder getOperationDescription() {
         StringBuilder sb = super.getOperationDescription();
         sb.append(" | unless='");
         sb.append(this.unless);
         sb.append('\'');
         sb.append(" | sync='");
         sb.append(this.sync);
         sb.append('\'');
         return sb;
      }

      public CacheableOperation build() {
         return new CacheableOperation(this);
      }
   }
}
