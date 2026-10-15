package org.springframework.context.annotation;

import org.springframework.instrument.classloading.LoadTimeWeaver;

public interface LoadTimeWeavingConfigurer {
   LoadTimeWeaver getLoadTimeWeaver();
}
