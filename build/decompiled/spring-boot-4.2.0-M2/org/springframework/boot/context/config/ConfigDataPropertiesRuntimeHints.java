package org.springframework.boot.context.config;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

class ConfigDataPropertiesRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      BindableRuntimeHintsRegistrar.forTypes(ConfigDataProperties.class).registerHints(hints);
      Method method = ReflectionUtils.findMethod(ConfigDataLocation.class, "of", new Class[]{String.class});
      Assert.state(method != null, "'method' must not be null");
      hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
   }
}
