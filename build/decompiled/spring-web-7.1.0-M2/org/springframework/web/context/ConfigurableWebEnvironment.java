package org.springframework.web.context;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;

public interface ConfigurableWebEnvironment extends ConfigurableEnvironment {
   void initPropertySources(@Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig);
}
