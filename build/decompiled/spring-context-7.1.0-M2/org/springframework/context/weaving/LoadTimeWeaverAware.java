package org.springframework.context.weaving;

import org.springframework.beans.factory.Aware;
import org.springframework.instrument.classloading.LoadTimeWeaver;

public interface LoadTimeWeaverAware extends Aware {
   void setLoadTimeWeaver(LoadTimeWeaver loadTimeWeaver);
}
