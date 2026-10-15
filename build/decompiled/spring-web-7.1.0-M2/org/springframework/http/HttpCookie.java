package org.springframework.http;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class HttpCookie {
   private final String name;
   private final String value;

   public HttpCookie(String name, @Nullable String value) {
      Assert.hasLength(name, "'name' is required and must not be empty.");
      this.name = name;
      this.value = value != null ? value : "";
   }

   public String getName() {
      return this.name;
   }

   public String getValue() {
      return this.value;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof HttpCookie that && this.name.equalsIgnoreCase(that.getName());
   }

   @Override
   public int hashCode() {
      return this.name.hashCode();
   }

   @Override
   public String toString() {
      return this.name + "=" + this.value;
   }
}
