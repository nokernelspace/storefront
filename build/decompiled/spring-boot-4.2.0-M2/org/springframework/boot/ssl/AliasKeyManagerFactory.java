package org.springframework.boot.ssl;

import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

final class AliasKeyManagerFactory extends KeyManagerFactory {
   AliasKeyManagerFactory(KeyManagerFactory delegate, String alias, String algorithm) {
      super(new AliasKeyManagerFactory.AliasKeyManagerFactorySpi(delegate, alias), delegate.getProvider(), algorithm);
   }

   private static final class AliasKeyManagerFactorySpi extends KeyManagerFactorySpi {
      private final KeyManagerFactory delegate;
      private final String alias;

      private AliasKeyManagerFactorySpi(KeyManagerFactory delegate, String alias) {
         this.delegate = delegate;
         this.alias = alias;
      }

      @Override
      protected void engineInit(KeyStore keyStore, char[] chars) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
         this.delegate.init(keyStore, chars);
      }

      @Override
      protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
         throw new InvalidAlgorithmParameterException("Unsupported ManagerFactoryParameters");
      }

      @Override
      protected KeyManager[] engineGetKeyManagers() {
         return Arrays.stream(this.delegate.getKeyManagers())
            .filter(X509ExtendedKeyManager.class::isInstance)
            .map(X509ExtendedKeyManager.class::cast)
            .map(this::wrap)
            .toArray(KeyManager[]::new);
      }

      private AliasKeyManagerFactory.AliasX509ExtendedKeyManager wrap(X509ExtendedKeyManager keyManager) {
         return new AliasKeyManagerFactory.AliasX509ExtendedKeyManager(keyManager, this.alias);
      }
   }

   static final class AliasX509ExtendedKeyManager extends X509ExtendedKeyManager {
      private final X509ExtendedKeyManager delegate;
      private final String alias;

      private AliasX509ExtendedKeyManager(X509ExtendedKeyManager keyManager, String alias) {
         this.delegate = keyManager;
         this.alias = alias;
      }

      @Override
      public String chooseEngineClientAlias(String[] strings, Principal[] principals, SSLEngine sslEngine) {
         return this.delegate.chooseEngineClientAlias(strings, principals, sslEngine);
      }

      @Override
      public String chooseEngineServerAlias(String s, Principal[] principals, SSLEngine sslEngine) {
         return this.alias;
      }

      @Override
      public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
         return this.delegate.chooseClientAlias(keyType, issuers, socket);
      }

      @Override
      public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
         return this.delegate.chooseServerAlias(keyType, issuers, socket);
      }

      @Override
      public X509Certificate[] getCertificateChain(String alias) {
         return this.delegate.getCertificateChain(alias);
      }

      @Override
      public String[] getClientAliases(String keyType, Principal[] issuers) {
         return this.delegate.getClientAliases(keyType, issuers);
      }

      @Override
      public PrivateKey getPrivateKey(String alias) {
         return this.delegate.getPrivateKey(alias);
      }

      @Override
      public String[] getServerAliases(String keyType, Principal[] issuers) {
         return this.delegate.getServerAliases(keyType, issuers);
      }
   }
}
