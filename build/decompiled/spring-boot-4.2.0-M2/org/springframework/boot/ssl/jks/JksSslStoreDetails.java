package org.springframework.boot.ssl.jks;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record JksSslStoreDetails(@Nullable String type, @Nullable String provider, @Nullable String location, @Nullable String password) {
   public JksSslStoreDetails withPassword(String password) {
      return new JksSslStoreDetails(this.type, this.provider, this.location, password);
   }

   boolean isEmpty() {
      return this.isEmpty(this.type) && this.isEmpty(this.provider) && this.isEmpty(this.location);
   }

   private boolean isEmpty(@Nullable String value) {
      return !StringUtils.hasText(value);
   }

   public static JksSslStoreDetails forLocation(@Nullable String location) {
      return new JksSslStoreDetails(null, null, location, null);
   }
}
