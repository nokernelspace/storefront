package org.springframework.context;

import org.springframework.beans.factory.Aware;
import org.springframework.core.metrics.ApplicationStartup;

public interface ApplicationStartupAware extends Aware {
   void setApplicationStartup(ApplicationStartup applicationStartup);
}
