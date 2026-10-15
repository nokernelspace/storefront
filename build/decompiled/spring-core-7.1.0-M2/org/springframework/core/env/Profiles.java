package org.springframework.core.env;

import java.util.function.Predicate;

@FunctionalInterface
public interface Profiles {
   boolean matches(Predicate<String> isProfileActive);

   static Profiles of(String... profileExpressions) {
      return ProfilesParser.parse(profileExpressions);
   }
}
