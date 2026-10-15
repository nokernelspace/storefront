package org.springframework.boot.support;

import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class AnsiOutputApplicationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
   public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
      ConfigurableEnvironment environment = event.getEnvironment();
      Binder.get(environment).bind("spring.output.ansi.enabled", AnsiOutput.Enabled.class).ifBound(AnsiOutput::setEnabled);
      AnsiOutput.setConsoleAvailable((Boolean)environment.getProperty("spring.output.ansi.console-available", Boolean.class));
   }

   public int getOrder() {
      return -2147483637;
   }
}
