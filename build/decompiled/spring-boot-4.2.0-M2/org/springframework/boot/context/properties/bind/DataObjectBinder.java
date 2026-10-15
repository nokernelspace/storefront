package org.springframework.boot.context.properties.bind;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

interface DataObjectBinder {
   <T> @Nullable T bind(
      ConfigurationPropertyName name, Bindable<T> target, Binder.Context context, DataObjectPropertyBinder propertyBinder, boolean fallbackToDefaultValue
   );

   <T> @Nullable T create(Bindable<T> target, Binder.Context context);

   default <T> void onUnableToCreateInstance(Bindable<T> target, Binder.Context context, RuntimeException exception) {
   }
}
