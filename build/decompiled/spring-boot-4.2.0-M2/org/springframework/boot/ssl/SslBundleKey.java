package org.springframework.boot.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public interface SslBundleKey {
   SslBundleKey NONE = of(null, null);

   @Nullable String getPassword();

   @Nullable String getAlias();

   default void assertContainsAlias(@Nullable KeyStore keyStore) {
      String alias = this.getAlias();
      if (StringUtils.hasLength(alias) && keyStore != null) {
         try {
            Assert.state(keyStore.containsAlias(alias), () -> String.format("Keystore does not contain alias '%s'", alias));
         } catch (KeyStoreException ex) {
            throw new IllegalStateException(String.format("Could not determine if keystore contains alias '%s'", alias), ex);
         }
      }
   }

   static SslBundleKey of(String password) {
      return of(password, null);
   }

   static SslBundleKey of(@Nullable String password, @Nullable String alias) {
      return new SslBundleKey() {
         @Override
         public @Nullable String getPassword() {
            return password;
         }

         @Override
         public @Nullable String getAlias() {
            return alias;
         }

         @Override
         public String toString() {
            ToStringCreator creator = new ToStringCreator(this);
            creator.append("alias", alias);
            creator.append("password", password != null ? "******" : null);
            return creator.toString();
         }
      };
   }
}
