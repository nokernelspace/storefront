package org.springframework.core.io.support;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;

public interface PropertySourceFactory {
   PropertySource<?> createPropertySource(@Nullable String name, EncodedResource resource) throws IOException;
}
