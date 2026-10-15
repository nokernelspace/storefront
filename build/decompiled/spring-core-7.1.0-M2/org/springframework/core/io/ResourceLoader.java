package org.springframework.core.io;

import org.jspecify.annotations.Nullable;

public interface ResourceLoader {
   String CLASSPATH_URL_PREFIX = "classpath:";
   String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

   Resource getResource(String location);

   @Nullable ClassLoader getClassLoader();
}
