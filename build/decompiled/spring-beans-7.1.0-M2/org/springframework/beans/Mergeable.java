package org.springframework.beans;

import org.jspecify.annotations.Nullable;

public interface Mergeable {
   boolean isMergeEnabled();

   Object merge(@Nullable Object parent);
}
