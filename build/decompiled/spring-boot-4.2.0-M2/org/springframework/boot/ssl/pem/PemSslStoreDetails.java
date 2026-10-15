package org.springframework.boot.ssl.pem;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record PemSslStoreDetails(
   @Nullable String type,
   @Nullable String alias,
   @Nullable String password,
   @Nullable String certificates,
   @Nullable String privateKey,
   @Nullable String privateKeyPassword
) {
   public PemSslStoreDetails(@Nullable String type, @Nullable String certificate, @Nullable String privateKey, @Nullable String privateKeyPassword) {
      this(type, null, null, certificate, privateKey, privateKeyPassword);
   }

   public PemSslStoreDetails(@Nullable String type, @Nullable String certificate, @Nullable String privateKey) {
      this(type, certificate, privateKey, null);
   }

   public PemSslStoreDetails withAlias(@Nullable String alias) {
      return new PemSslStoreDetails(this.type, alias, this.password, this.certificates, this.privateKey, this.privateKeyPassword);
   }

   public PemSslStoreDetails withPassword(@Nullable String password) {
      return new PemSslStoreDetails(this.type, this.alias, password, this.certificates, this.privateKey, this.privateKeyPassword);
   }

   public PemSslStoreDetails withPrivateKey(@Nullable String privateKey) {
      return new PemSslStoreDetails(this.type, this.alias, this.password, this.certificates, privateKey, this.privateKeyPassword);
   }

   public PemSslStoreDetails withPrivateKeyPassword(@Nullable String privateKeyPassword) {
      return new PemSslStoreDetails(this.type, this.alias, this.password, this.certificates, this.privateKey, privateKeyPassword);
   }

   boolean isEmpty() {
      return this.isEmpty(this.type) && this.isEmpty(this.certificates) && this.isEmpty(this.privateKey);
   }

   private boolean isEmpty(@Nullable String value) {
      return !StringUtils.hasText(value);
   }

   public static PemSslStoreDetails forCertificate(@Nullable String certificate) {
      return forCertificates(certificate);
   }

   public static PemSslStoreDetails forCertificates(@Nullable String certificates) {
      return new PemSslStoreDetails(null, certificates, null);
   }
}
