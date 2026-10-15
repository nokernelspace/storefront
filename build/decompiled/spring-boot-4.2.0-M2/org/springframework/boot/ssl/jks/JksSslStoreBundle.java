package org.springframework.boot.ssl.jks;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

public class JksSslStoreBundle implements SslStoreBundle {
   private final @Nullable JksSslStoreDetails keyStoreDetails;
   private final ResourceLoader resourceLoader;
   private final SingletonSupplier<KeyStore> keyStore;
   private final SingletonSupplier<KeyStore> trustStore;

   public JksSslStoreBundle(@Nullable JksSslStoreDetails keyStoreDetails, @Nullable JksSslStoreDetails trustStoreDetails) {
      this(keyStoreDetails, trustStoreDetails, ApplicationResourceLoader.get());
   }

   public JksSslStoreBundle(@Nullable JksSslStoreDetails keyStoreDetails, @Nullable JksSslStoreDetails trustStoreDetails, ResourceLoader resourceLoader) {
      Assert.notNull(resourceLoader, "'resourceLoader' must not be null");
      this.keyStoreDetails = keyStoreDetails;
      this.resourceLoader = resourceLoader;
      this.keyStore = SingletonSupplier.of(() -> this.createKeyStore("key", keyStoreDetails));
      this.trustStore = SingletonSupplier.of(() -> this.createKeyStore("trust", trustStoreDetails));
   }

   @Override
   public @Nullable KeyStore getKeyStore() {
      return (KeyStore)this.keyStore.get();
   }

   @Override
   public @Nullable String getKeyStorePassword() {
      return this.keyStoreDetails != null ? this.keyStoreDetails.password() : null;
   }

   @Override
   public @Nullable KeyStore getTrustStore() {
      return (KeyStore)this.trustStore.get();
   }

   private @Nullable KeyStore createKeyStore(String name, @Nullable JksSslStoreDetails details) {
      if (details != null && !details.isEmpty()) {
         try {
            String type = !StringUtils.hasText(details.type()) ? KeyStore.getDefaultType() : details.type();
            char[] password = details.password() != null ? details.password().toCharArray() : null;
            String location = details.location();
            KeyStore store = this.getKeyStoreInstance(type, details.provider());
            if (this.isHardwareKeystoreType(type)) {
               this.loadHardwareKeyStore(store, location, password);
            } else {
               this.loadKeyStore(store, location, password);
            }

            return store;
         } catch (Exception ex) {
            throw new IllegalStateException("Unable to create %s store: %s".formatted(name, ex.getMessage()), ex);
         }
      } else {
         return null;
      }
   }

   private KeyStore getKeyStoreInstance(String type, @Nullable String provider) throws KeyStoreException, NoSuchProviderException {
      return !StringUtils.hasText(provider) ? KeyStore.getInstance(type) : KeyStore.getInstance(type, provider);
   }

   private boolean isHardwareKeystoreType(String type) {
      return type.equalsIgnoreCase("PKCS11");
   }

   private void loadHardwareKeyStore(KeyStore store, @Nullable String location, char @Nullable [] password) throws IOException, NoSuchAlgorithmException, CertificateException {
      Assert.state(!StringUtils.hasText(location), () -> "Location is '%s', but must be empty or null for PKCS11 hardware key stores".formatted(location));
      store.load(null, password);
   }

   private void loadKeyStore(KeyStore store, @Nullable String location, char @Nullable [] password) {
      Assert.state(StringUtils.hasText(location), () -> "Location must not be empty or null");

      try (InputStream stream = this.resourceLoader.getResource(location).getInputStream()) {
         store.load(stream, password);
      } catch (Exception ex) {
         throw new IllegalStateException("Could not load store from '" + location + "'", ex);
      }
   }

   @Override
   public String toString() {
      ToStringCreator creator = new ToStringCreator(this);
      KeyStore keyStore = (KeyStore)this.keyStore.get();
      creator.append("keyStore.type", keyStore != null ? keyStore.getType() : "none");
      String keyStorePassword = this.getKeyStorePassword();
      creator.append("keyStorePassword", keyStorePassword != null ? "******" : null);
      KeyStore trustStore = (KeyStore)this.trustStore.get();
      creator.append("trustStore.type", trustStore != null ? trustStore.getType() : "none");
      return creator.toString();
   }
}
