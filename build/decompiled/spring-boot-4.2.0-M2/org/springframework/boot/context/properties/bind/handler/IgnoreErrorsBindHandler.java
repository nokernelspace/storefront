package org.springframework.boot.context.properties.bind.handler;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.AbstractBindHandler;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

public class IgnoreErrorsBindHandler extends AbstractBindHandler {
   public IgnoreErrorsBindHandler() {
   }

   public IgnoreErrorsBindHandler(BindHandler parent) {
      super(parent);
   }

   @Override
   public @Nullable Object onFailure(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Exception error) throws Exception {
      return target.getValue() != null ? target.getValue().get() : null;
   }
}
