package org.springframework.beans;

import org.jspecify.annotations.Nullable;

public interface BeanMetadataElement {
   default @Nullable Object getSource() {
      return null;
   }
}
