package org.springframework.boot.ssl.pem;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

public interface PemSslStore {
   @Nullable String type();

   @Nullable String alias();

   @Nullable String password();

   @Nullable List<X509Certificate> certificates();

   @Nullable PrivateKey privateKey();

   default PemSslStore withAlias(@Nullable String alias) {
      List<X509Certificate> certificates = this.certificates();
      Assert.notNull(certificates, "'certificates' must not be null");
      return of(this.type(), alias, this.password(), certificates, this.privateKey());
   }

   default PemSslStore withPassword(@Nullable String password) {
      List<X509Certificate> certificates = this.certificates();
      Assert.notNull(certificates, "'certificates' must not be null");
      return of(this.type(), this.alias(), password, certificates, this.privateKey());
   }

   static @Nullable PemSslStore load(@Nullable PemSslStoreDetails details) {
      return load(details, ApplicationResourceLoader.get());
   }

   static @Nullable PemSslStore load(@Nullable PemSslStoreDetails details, ResourceLoader resourceLoader) {
      return details != null && !details.isEmpty() ? new LoadedPemSslStore(details, resourceLoader) : null;
   }

   static PemSslStore of(@Nullable String type, List<X509Certificate> certificates, @Nullable PrivateKey privateKey) {
      return of(type, null, null, certificates, privateKey);
   }

   static PemSslStore of(List<X509Certificate> certificates, @Nullable PrivateKey privateKey) {
      return of(null, null, null, certificates, privateKey);
   }

   static PemSslStore of(
      @Nullable String type, @Nullable String alias, @Nullable String password, List<X509Certificate> certificates, @Nullable PrivateKey privateKey
   ) {
      Assert.notEmpty(certificates, "'certificates' must not be empty");
      return new PemSslStore() {
         @Override
         public @Nullable String type() {
            return type;
         }

         @Override
         public @Nullable String alias() {
            return alias;
         }

         @Override
         public @Nullable String password() {
            return password;
         }

         @Override
         public List<X509Certificate> certificates() {
            return certificates;
         }

         @Override
         public @Nullable PrivateKey privateKey() {
            return privateKey;
         }
      };
   }
}
