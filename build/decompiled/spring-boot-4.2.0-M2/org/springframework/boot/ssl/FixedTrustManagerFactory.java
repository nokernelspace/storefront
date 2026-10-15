package org.springframework.boot.ssl;

import java.security.KeyStore;
import java.security.Provider;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;

final class FixedTrustManagerFactory extends TrustManagerFactory {
   private static final Provider PROVIDER = new FixedTrustManagerFactory.FixedTrustManagerFactoryProvider();

   private FixedTrustManagerFactory(FixedTrustManagerFactory.FixedTrustManagersSpi spi, String algorithm) {
      super(spi, PROVIDER, algorithm);
   }

   static FixedTrustManagerFactory of(TrustManagerFactory trustManagerFactory, TrustManager... trustManagers) {
      return new FixedTrustManagerFactory(new FixedTrustManagerFactory.FixedTrustManagersSpi(trustManagers), trustManagerFactory.getAlgorithm());
   }

   private static class FixedTrustManagerFactoryProvider extends Provider {
      FixedTrustManagerFactoryProvider() {
         super("FixedTrustManagerFactory", SpringBootProviderVersion.get(), "");
      }
   }

   private static final class FixedTrustManagersSpi extends TrustManagerFactorySpi {
      private final TrustManager[] trustManagers;

      private FixedTrustManagersSpi(TrustManager[] trustManagers) {
         this.trustManagers = trustManagers;
      }

      @Override
      protected void engineInit(KeyStore ks) {
      }

      @Override
      protected void engineInit(ManagerFactoryParameters spec) {
      }

      @Override
      protected TrustManager[] engineGetTrustManagers() {
         return this.trustManagers;
      }
   }
}
