package org.springframework.boot.context.properties.bind;

import org.jspecify.annotations.Nullable;

interface DataObjectPropertyBinder {
   @Nullable Object bindProperty(String propertyName, Bindable<?> target);
}
