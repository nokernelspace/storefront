package org.springframework.boot.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.StringUtils;

public interface SslBundle {
   String DEFAULT_PROTOCOL = "TLS";

   SslStoreBundle getStores();

   SslBundleKey getKey();

   SslOptions getOptions();

   String getProtocol();

   SslManagerBundle getManagers();

   default SSLContext createSslContext() {
      return this.getManagers().createSslContext(this.getProtocol());
   }

   static SslBundle of(@Nullable SslStoreBundle stores) {
      return of(stores, null, null);
   }

   static SslBundle of(@Nullable SslStoreBundle stores, @Nullable SslBundleKey key) {
      return of(stores, key, null);
   }

   static SslBundle of(@Nullable SslStoreBundle stores, @Nullable SslBundleKey key, @Nullable SslOptions options) {
      return of(stores, key, options, null);
   }

   static SslBundle of(@Nullable SslStoreBundle stores, @Nullable SslBundleKey key, @Nullable SslOptions options, @Nullable String protocol) {
      return of(stores, key, options, protocol, null);
   }

   static SslBundle of(
      @Nullable SslStoreBundle stores, @Nullable SslBundleKey key, @Nullable SslOptions options, @Nullable String protocol, @Nullable SslManagerBundle managers
   ) {
      final SslManagerBundle managersToUse = managers != null ? managers : SslManagerBundle.from(stores, key);
      return new SslBundle() {
         @Override
         public SslStoreBundle getStores() {
            return stores != null ? stores : SslStoreBundle.NONE;
         }

         @Override
         public SslBundleKey getKey() {
            return key != null ? key : SslBundleKey.NONE;
         }

         @Override
         public SslOptions getOptions() {
            return options != null ? options : SslOptions.NONE;
         }

         @Override
         public String getProtocol() {
            return !StringUtils.hasText(protocol) ? "TLS" : protocol;
         }

         @Override
         public SslManagerBundle getManagers() {
            return managersToUse;
         }

         @Override
         public String toString() {
            ToStringCreator creator = new ToStringCreator(this);
            creator.append("key", this.getKey());
            creator.append("options", this.getOptions());
            creator.append("protocol", this.getProtocol());
            creator.append("stores", this.getStores());
            return creator.toString();
         }
      };
   }

   static SslBundle systemDefault() {
      try {
         final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         keyManagerFactory.init(null, null);
         final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         trustManagerFactory.init((KeyStore)null);
         final SSLContext sslContext = SSLContext.getDefault();
         return of(null, null, null, null, new SslManagerBundle() {
            @Override
            public KeyManagerFactory getKeyManagerFactory() {
               return keyManagerFactory;
            }

            @Override
            public TrustManagerFactory getTrustManagerFactory() {
               return trustManagerFactory;
            }

            @Override
            public SSLContext createSslContext(String protocol) {
               return sslContext;
            }
         });
      } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException ex) {
         throw new IllegalStateException("Could not initialize system default SslBundle: " + ex.getMessage(), ex);
      }
   }
}
