package org.springframework.boot.ssl;

import org.jspecify.annotations.Nullable;

public class NoSuchSslBundleException extends RuntimeException {
   private final String bundleName;

   public NoSuchSslBundleException(String bundleName, String message) {
      this(bundleName, message, null);
   }

   public NoSuchSslBundleException(String bundleName, String message, @Nullable Throwable cause) {
      super(message, cause);
      this.bundleName = bundleName;
   }

   public String getBundleName() {
      return this.bundleName;
   }
}
