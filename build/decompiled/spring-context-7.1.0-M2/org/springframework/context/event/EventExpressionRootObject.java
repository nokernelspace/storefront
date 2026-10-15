package org.springframework.context.event;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

record EventExpressionRootObject(ApplicationEvent event, @Nullable Object[] args) {
}
