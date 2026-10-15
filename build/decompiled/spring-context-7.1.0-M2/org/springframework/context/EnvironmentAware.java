package org.springframework.context;

import org.springframework.beans.factory.Aware;
import org.springframework.core.env.Environment;

public interface EnvironmentAware extends Aware {
   void setEnvironment(Environment environment);
}
