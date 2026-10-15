package org.springframework.beans.factory.parsing;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

@FunctionalInterface
public interface SourceExtractor {
   @Nullable Object extractSource(Object sourceCandidate, @Nullable Resource definingResource);
}
