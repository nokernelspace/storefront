package org.springframework.boot.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public interface SslManagerBundle {
   default KeyManager[] getKeyManagers() {
      return this.getKeyManagerFactory().getKeyManagers();
   }

   KeyManagerFactory getKeyManagerFactory();

   default TrustManager[] getTrustManagers() {
      return this.getTrustManagerFactory().getTrustManagers();
   }

   TrustManagerFactory getTrustManagerFactory();

   default SSLContext createSslContext(String protocol) {
      try {
         SSLContext sslContext = SSLContext.getInstance(protocol);
         sslContext.init(this.getKeyManagers(), this.getTrustManagers(), null);
         return sslContext;
      } catch (Exception ex) {
         throw new IllegalStateException("Could not load SSL context: " + ex.getMessage(), ex);
      }
   }

   static SslManagerBundle of(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory) {
      Assert.notNull(keyManagerFactory, "'keyManagerFactory' must not be null");
      Assert.notNull(trustManagerFactory, "'trustManagerFactory' must not be null");
      return new SslManagerBundle() {
         @Override
         public KeyManagerFactory getKeyManagerFactory() {
            return keyManagerFactory;
         }

         @Override
         public TrustManagerFactory getTrustManagerFactory() {
            return trustManagerFactory;
         }
      };
   }

   static SslManagerBundle from(@Nullable SslStoreBundle storeBundle, @Nullable SslBundleKey key) {
      return new DefaultSslManagerBundle(storeBundle, key);
   }

   static SslManagerBundle from(TrustManagerFactory trustManagerFactory) {
      Assert.notNull(trustManagerFactory, "'trustManagerFactory' must not be null");
      KeyManagerFactory defaultKeyManagerFactory = createDefaultKeyManagerFactory();
      return of(defaultKeyManagerFactory, trustManagerFactory);
   }

   static SslManagerBundle from(TrustManager... trustManagers) {
      Assert.notNull(trustManagers, "'trustManagers' must not be null");
      KeyManagerFactory defaultKeyManagerFactory = createDefaultKeyManagerFactory();
      TrustManagerFactory defaultTrustManagerFactory = createDefaultTrustManagerFactory();
      return of(defaultKeyManagerFactory, FixedTrustManagerFactory.of(defaultTrustManagerFactory, trustManagers));
   }

   private static TrustManagerFactory createDefaultTrustManagerFactory() {
      String defaultAlgorithm = TrustManagerFactory.getDefaultAlgorithm();

      try {
         TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(defaultAlgorithm);
         trustManagerFactory.init((KeyStore)null);
         return trustManagerFactory;
      } catch (NoSuchAlgorithmException | KeyStoreException ex) {
         throw new IllegalStateException("Unable to create TrustManagerFactory for default '%s' algorithm".formatted(defaultAlgorithm), ex);
      }
   }

   private static KeyManagerFactory createDefaultKeyManagerFactory() {
      String defaultAlgorithm = KeyManagerFactory.getDefaultAlgorithm();

      try {
         KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(defaultAlgorithm);
         keyManagerFactory.init(null, null);
         return keyManagerFactory;
      } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException ex) {
         throw new IllegalStateException("Unable to create KeyManagerFactory for default '%s' algorithm".formatted(defaultAlgorithm), ex);
      }
   }
}
