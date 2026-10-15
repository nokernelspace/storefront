package org.springframework.boot.info;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import javax.security.auth.x500.X500Principal;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class SslInfo {
   private final SslBundles sslBundles;
   private final Clock clock;

   public SslInfo(SslBundles sslBundles) {
      this(sslBundles, Clock.systemDefaultZone());
   }

   public SslInfo(SslBundles sslBundles, Clock clock) {
      this.sslBundles = sslBundles;
      this.clock = clock;
   }

   public List<SslInfo.BundleInfo> getBundles() {
      return this.sslBundles.getBundleNames().stream().map(name -> new SslInfo.BundleInfo(name, this.sslBundles.getBundle(name))).toList();
   }

   public SslInfo.BundleInfo getBundle(String name) {
      SslBundle bundle = this.sslBundles.getBundle(name);
      return new SslInfo.BundleInfo(name, bundle);
   }

   public final class BundleInfo {
      private final String name;
      private final List<SslInfo.CertificateChainInfo> certificateChains;
      private final List<SslInfo.CertificateChainInfo> trustStoreCertificateChains;

      private BundleInfo(String name, SslBundle sslBundle) {
         this.name = name;
         this.certificateChains = this.extractCertificateChains(sslBundle.getStores().getKeyStore());
         this.trustStoreCertificateChains = this.extractCertificateChains(sslBundle.getStores().getTrustStore());
      }

      private List<SslInfo.CertificateChainInfo> extractCertificateChains(@Nullable KeyStore keyStore) {
         if (keyStore == null) {
            return Collections.emptyList();
         }

         try {
            return Collections.list(keyStore.aliases()).stream().map(alias -> SslInfo.this.new CertificateChainInfo(keyStore, alias)).toList();
         } catch (KeyStoreException ex) {
            return Collections.emptyList();
         }
      }

      public String getName() {
         return this.name;
      }

      public List<SslInfo.CertificateChainInfo> getCertificateChains() {
         return this.certificateChains;
      }

      public List<SslInfo.CertificateChainInfo> getTrustStoreCertificateChains() {
         return this.trustStoreCertificateChains;
      }
   }

   public final class CertificateChainInfo {
      private final String alias;
      private final List<SslInfo.CertificateInfo> certificates;

      CertificateChainInfo(KeyStore keyStore, String alias) {
         this.alias = alias;
         this.certificates = this.extractCertificates(keyStore, alias);
      }

      private List<SslInfo.CertificateInfo> extractCertificates(KeyStore keyStore, String alias) {
         try {
            List<SslInfo.CertificateInfo> certificates = this.readCertificateChain(keyStore, alias);
            if (certificates != null) {
               return certificates;
            }

            List<SslInfo.CertificateInfo> certificate = this.readCertificate(keyStore, alias);
            return certificate != null ? certificate : Collections.emptyList();
         } catch (KeyStoreException ex) {
            return Collections.emptyList();
         }
      }

      private @Nullable List<SslInfo.CertificateInfo> readCertificate(KeyStore keyStore, String alias) throws KeyStoreException {
         Certificate certificate = keyStore.getCertificate(alias);
         return certificate != null ? List.of(SslInfo.this.new CertificateInfo(certificate)) : null;
      }

      private @Nullable List<SslInfo.CertificateInfo> readCertificateChain(KeyStore keyStore, String alias) throws KeyStoreException {
         Certificate[] certificates = keyStore.getCertificateChain(alias);
         return ObjectUtils.isEmpty(certificates) ? null : Arrays.stream(certificates).map(x$0 -> SslInfo.this.new CertificateInfo(x$0)).toList();
      }

      public String getAlias() {
         return this.alias;
      }

      public List<SslInfo.CertificateInfo> getCertificates() {
         return this.certificates;
      }
   }

   public final class CertificateInfo {
      private final @Nullable X509Certificate certificate;

      private CertificateInfo(Certificate certificate) {
         this.certificate = certificate instanceof X509Certificate x509Certificate ? x509Certificate : null;
      }

      public @Nullable String getSubject() {
         return this.extract(X509Certificate::getSubjectX500Principal, X500Principal::getName);
      }

      public @Nullable String getIssuer() {
         return this.extract(X509Certificate::getIssuerX500Principal, X500Principal::getName);
      }

      public @Nullable String getSerialNumber() {
         return this.extract(X509Certificate::getSerialNumber, serial -> serial.toString(16));
      }

      public @Nullable String getVersion() {
         return this.extract(certificate -> "V" + certificate.getVersion());
      }

      public @Nullable String getSignatureAlgorithmName() {
         return this.extract(X509Certificate::getSigAlgName);
      }

      public @Nullable Instant getValidityStarts() {
         return this.extract(X509Certificate::getNotBefore, Date::toInstant);
      }

      public @Nullable Instant getValidityEnds() {
         return this.extract(X509Certificate::getNotAfter, Date::toInstant);
      }

      public SslInfo.@Nullable CertificateValidityInfo getValidity() {
         return this.extract(certificate -> {
            Instant starts = this.getValidityStarts();
            Instant ends = this.getValidityEnds();
            Assert.state(starts != null, "Validity start not found");
            Assert.state(ends != null, "Validity end not found");
            SslInfo.CertificateValidityInfo.Status validity = this.checkValidity(starts, ends);

            return switch (validity) {
               case VALID -> SslInfo.CertificateValidityInfo.VALID;
               case NOT_YET_VALID -> new SslInfo.CertificateValidityInfo(SslInfo.CertificateValidityInfo.Status.NOT_YET_VALID, "Not valid before %s", starts);
               case EXPIRED -> new SslInfo.CertificateValidityInfo(SslInfo.CertificateValidityInfo.Status.EXPIRED, "Not valid after %s", ends);
            };
         });
      }

      private SslInfo.CertificateValidityInfo.Status checkValidity(Instant starts, Instant ends) {
         Instant now = SslInfo.this.clock.instant();
         if (now.isBefore(starts)) {
            return SslInfo.CertificateValidityInfo.Status.NOT_YET_VALID;
         } else {
            return now.isAfter(ends) ? SslInfo.CertificateValidityInfo.Status.EXPIRED : SslInfo.CertificateValidityInfo.Status.VALID;
         }
      }

      private <V, R> @Nullable R extract(Function<X509Certificate, V> valueExtractor, Function<V, R> resultExtractor) {
         return this.extract(valueExtractor.andThen(resultExtractor));
      }

      private <R> @Nullable R extract(Function<X509Certificate, R> extractor) {
         return this.certificate != null ? extractor.apply(this.certificate) : null;
      }
   }

   public static class CertificateValidityInfo {
      static final SslInfo.CertificateValidityInfo VALID = new SslInfo.CertificateValidityInfo(SslInfo.CertificateValidityInfo.Status.VALID, null);
      private final SslInfo.CertificateValidityInfo.Status status;
      private final @Nullable String message;

      CertificateValidityInfo(SslInfo.CertificateValidityInfo.Status status, @Nullable String message, Object... messageArgs) {
         this.status = status;
         this.message = message != null ? message.formatted(messageArgs) : null;
      }

      public SslInfo.CertificateValidityInfo.Status getStatus() {
         return this.status;
      }

      public @Nullable String getMessage() {
         return this.message;
      }

      public enum Status {
         VALID(true),
         NOT_YET_VALID(false),
         EXPIRED(false);

         private final boolean valid;

         Status(boolean valid) {
            this.valid = valid;
         }

         public boolean isValid() {
            return this.valid;
         }
      }
   }
}
