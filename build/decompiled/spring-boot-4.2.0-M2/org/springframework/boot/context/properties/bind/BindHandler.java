package org.springframework.boot.context.properties.bind;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

public interface BindHandler {
   BindHandler DEFAULT = new BindHandler() {};

   default <T> @Nullable Bindable<T> onStart(ConfigurationPropertyName name, Bindable<T> target, BindContext context) {
      return target;
   }

   default @Nullable Object onSuccess(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Object result) {
      return result;
   }

   default Object onCreate(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Object result) {
      return result;
   }

   default @Nullable Object onFailure(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Exception error) throws Exception {
      throw error;
   }

   default void onFinish(ConfigurationPropertyName name, Bindable<?> target, BindContext context, @Nullable Object result) throws Exception {
   }
}
