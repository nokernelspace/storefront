package org.springframework.boot.web.error;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.util.ObjectUtils;

public class ErrorPage {
   private final @Nullable HttpStatus status;
   private final @Nullable Class<? extends Throwable> exception;
   private final String path;

   public ErrorPage(String path) {
      this.status = null;
      this.exception = null;
      this.path = path;
   }

   public ErrorPage(HttpStatus status, String path) {
      this.status = status;
      this.exception = null;
      this.path = path;
   }

   public ErrorPage(Class<? extends Throwable> exception, String path) {
      this.status = null;
      this.exception = exception;
      this.path = path;
   }

   public String getPath() {
      return this.path;
   }

   public @Nullable Class<? extends Throwable> getException() {
      return this.exception;
   }

   public @Nullable HttpStatus getStatus() {
      return this.status;
   }

   public int getStatusCode() {
      return this.status != null ? this.status.value() : 0;
   }

   public @Nullable String getExceptionName() {
      return this.exception != null ? this.exception.getName() : null;
   }

   public boolean isGlobal() {
      return this.status == null && this.exception == null;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else {
         return !(obj instanceof ErrorPage other)
            ? false
            : ObjectUtils.nullSafeEquals(this.getExceptionName(), other.getExceptionName())
               && ObjectUtils.nullSafeEquals(this.path, other.path)
               && this.status == other.status;
      }
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = 1;
      result = 31 * result + ObjectUtils.nullSafeHashCode(this.getExceptionName());
      result = 31 * result + ObjectUtils.nullSafeHashCode(this.path);
      return 31 * result + this.getStatusCode();
   }
}
