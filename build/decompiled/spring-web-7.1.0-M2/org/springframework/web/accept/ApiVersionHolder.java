package org.springframework.web.accept;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public final class ApiVersionHolder {
   public static final ApiVersionHolder EMPTY = fromVersion(null);
   private final @Nullable Comparable<?> version;
   private final @Nullable RuntimeException exception;

   private ApiVersionHolder(@Nullable Comparable<?> version, @Nullable RuntimeException ex) {
      this.version = version;
      this.exception = ex;
   }

   public boolean hasVersion() {
      return this.version != null;
   }

   public boolean hasError() {
      return this.exception != null;
   }

   public Comparable<?> getVersion() {
      Assert.state(this.version != null, "No version");
      return this.version;
   }

   public @Nullable Comparable<?> getVersionIfPresent() {
      return this.version;
   }

   public RuntimeException getError() {
      Assert.state(this.exception != null, "No error");
      return this.exception;
   }

   public static ApiVersionHolder fromVersion(@Nullable Comparable<?> version) {
      return new ApiVersionHolder(version, null);
   }

   public static ApiVersionHolder fromError(@Nullable RuntimeException ex) {
      return new ApiVersionHolder(null, ex);
   }
}
