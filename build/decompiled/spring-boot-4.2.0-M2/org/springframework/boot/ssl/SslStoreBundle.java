package org.springframework.boot.ssl;

import java.security.KeyStore;
import org.jspecify.annotations.Nullable;
import org.springframework.core.style.ToStringCreator;

public interface SslStoreBundle {
   SslStoreBundle NONE = of(null, null, null);

   @Nullable KeyStore getKeyStore();

   @Nullable String getKeyStorePassword();

   @Nullable KeyStore getTrustStore();

   static SslStoreBundle of(@Nullable KeyStore keyStore, @Nullable String keyStorePassword, @Nullable KeyStore trustStore) {
      return new SslStoreBundle() {
         @Override
         public @Nullable KeyStore getKeyStore() {
            return keyStore;
         }

         @Override
         public @Nullable KeyStore getTrustStore() {
            return trustStore;
         }

         @Override
         public @Nullable String getKeyStorePassword() {
            return keyStorePassword;
         }

         @Override
         public String toString() {
            ToStringCreator creator = new ToStringCreator(this);
            creator.append("keyStore.type", keyStore != null ? keyStore.getType() : "none");
            creator.append("keyStorePassword", keyStorePassword != null ? "******" : null);
            creator.append("trustStore.type", trustStore != null ? trustStore.getType() : "none");
            return creator.toString();
         }
      };
   }
}
