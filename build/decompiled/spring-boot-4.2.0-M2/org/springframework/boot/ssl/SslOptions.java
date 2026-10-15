package org.springframework.boot.ssl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.style.ToStringCreator;

public interface SslOptions {
   SslOptions NONE = of(null, (Set<String>)null);

   default boolean isSpecified() {
      return this.getCiphers() != null || this.getEnabledProtocols() != null;
   }

   String @Nullable [] getCiphers();

   String @Nullable [] getEnabledProtocols();

   static SslOptions of(String @Nullable [] ciphers, String @Nullable [] enabledProtocols) {
      return new SslOptions() {
         @Override
         public String @Nullable [] getCiphers() {
            return ciphers;
         }

         @Override
         public String @Nullable [] getEnabledProtocols() {
            return enabledProtocols;
         }

         @Override
         public String toString() {
            ToStringCreator creator = new ToStringCreator(this);
            creator.append("ciphers", ciphers);
            creator.append("enabledProtocols", enabledProtocols);
            return creator.toString();
         }
      };
   }

   static SslOptions of(@Nullable Set<String> ciphers, @Nullable Set<String> enabledProtocols) {
      return of(toArray(ciphers), toArray(enabledProtocols));
   }

   static @Nullable Set<String> asSet(String @Nullable [] array) {
      return array != null ? Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(array))) : null;
   }

   private static String @Nullable [] toArray(@Nullable Collection<String> collection) {
      return collection != null ? collection.toArray(String[]::new) : null;
   }
}
