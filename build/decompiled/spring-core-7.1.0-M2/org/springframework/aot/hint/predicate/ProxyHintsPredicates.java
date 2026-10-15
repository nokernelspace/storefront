package org.springframework.aot.hint.predicate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.Assert;

public class ProxyHintsPredicates {
   ProxyHintsPredicates() {
   }

   public Predicate<RuntimeHints> forInterfaces(Class<?>... interfaces) {
      Assert.notEmpty(interfaces, "'interfaces' should not be empty");
      return this.forInterfaces(Arrays.stream(interfaces).map(TypeReference::of).toArray(TypeReference[]::new));
   }

   public Predicate<RuntimeHints> forInterfaces(TypeReference... interfaces) {
      Assert.notEmpty(interfaces, "'interfaces' should not be empty");
      List<TypeReference> interfaceList = Arrays.asList(interfaces);
      return hints -> hints.proxies().jdkProxyHints().anyMatch(proxyHint -> proxyHint.getProxiedInterfaces().equals(interfaceList));
   }
}
