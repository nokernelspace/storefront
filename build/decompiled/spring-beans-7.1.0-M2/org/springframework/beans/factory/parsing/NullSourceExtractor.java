package org.springframework.beans.factory.parsing;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

public class NullSourceExtractor implements SourceExtractor {
   @Override
   public @Nullable Object extractSource(Object sourceCandidate, @Nullable Resource definitionResource) {
      return null;
   }
}
