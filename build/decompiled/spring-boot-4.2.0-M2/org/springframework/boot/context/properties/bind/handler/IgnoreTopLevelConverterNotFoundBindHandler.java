package org.springframework.boot.context.properties.bind.handler;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.AbstractBindHandler;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.convert.ConverterNotFoundException;

public class IgnoreTopLevelConverterNotFoundBindHandler extends AbstractBindHandler {
   public IgnoreTopLevelConverterNotFoundBindHandler() {
   }

   public IgnoreTopLevelConverterNotFoundBindHandler(BindHandler parent) {
      super(parent);
   }

   @Override
   public @Nullable Object onFailure(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Exception error) throws Exception {
      if (context.getDepth() == 0 && error instanceof ConverterNotFoundException) {
         return null;
      } else {
         throw error;
      }
   }
}
