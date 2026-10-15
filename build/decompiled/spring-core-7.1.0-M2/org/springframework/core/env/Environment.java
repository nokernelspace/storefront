package org.springframework.core.env;

public interface Environment extends PropertyResolver {
   String[] getActiveProfiles();

   String[] getDefaultProfiles();

   default boolean matchesProfiles(String... profileExpressions) {
      return this.acceptsProfiles(Profiles.of(profileExpressions));
   }

   @Deprecated(since = "5.1")
   boolean acceptsProfiles(String... profiles);

   boolean acceptsProfiles(Profiles profiles);
}
