package org.springframework.boot.context.config;

import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;

public interface ConfigDataLoaderContext {
   ConfigurableBootstrapContext getBootstrapContext();
}
