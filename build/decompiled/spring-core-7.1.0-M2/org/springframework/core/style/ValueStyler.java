package org.springframework.core.style;

import org.jspecify.annotations.Nullable;

public interface ValueStyler {
   String style(@Nullable Object value);
}
