package org.springframework.beans.factory.parsing;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

public class PassThroughSourceExtractor implements SourceExtractor {
   @Override
   public Object extractSource(Object sourceCandidate, @Nullable Resource definingResource) {
      return sourceCandidate;
   }
}
