package org.springframework.aot.hint;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ProxyHints {
   private final Set<JdkProxyHint> jdkProxies = new LinkedHashSet<>();

   public Stream<JdkProxyHint> jdkProxyHints() {
      return this.jdkProxies.stream();
   }

   public ProxyHints registerJdkProxy(Consumer<JdkProxyHint.Builder> jdkProxyHint) {
      JdkProxyHint.Builder builder = new JdkProxyHint.Builder();
      jdkProxyHint.accept(builder);
      this.jdkProxies.add(builder.build());
      return this;
   }

   public ProxyHints registerJdkProxy(TypeReference... proxiedInterfaces) {
      return this.registerJdkProxy(jdkProxyHint -> jdkProxyHint.proxiedInterfaces(proxiedInterfaces));
   }

   public ProxyHints registerJdkProxy(Class<?>... proxiedInterfaces) {
      return this.registerJdkProxy(jdkProxyHint -> jdkProxyHint.proxiedInterfaces(proxiedInterfaces));
   }
}
