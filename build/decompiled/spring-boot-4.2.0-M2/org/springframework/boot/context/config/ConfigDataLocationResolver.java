package org.springframework.boot.context.config;

import java.util.Collections;
import java.util.List;

public interface ConfigDataLocationResolver<R extends ConfigDataResource> {
   boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location);

   List<R> resolve(ConfigDataLocationResolverContext context, ConfigDataLocation location) throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException;

   default List<R> resolveProfileSpecific(ConfigDataLocationResolverContext context, ConfigDataLocation location, Profiles profiles) throws ConfigDataLocationNotFoundException {
      return Collections.emptyList();
   }
}
