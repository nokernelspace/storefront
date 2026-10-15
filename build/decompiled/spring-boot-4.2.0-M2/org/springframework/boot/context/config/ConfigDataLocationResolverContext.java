package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;

public interface ConfigDataLocationResolverContext {
   Binder getBinder();

   @Nullable ConfigDataResource getParent();

   ConfigurableBootstrapContext getBootstrapContext();
}
