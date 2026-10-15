package org.springframework.context;

import org.jspecify.annotations.Nullable;

public interface HierarchicalMessageSource extends MessageSource {
   void setParentMessageSource(@Nullable MessageSource parent);

   @Nullable MessageSource getParentMessageSource();
}
