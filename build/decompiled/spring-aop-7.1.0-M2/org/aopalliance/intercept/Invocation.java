package org.aopalliance.intercept;

import org.jspecify.annotations.Nullable;

public interface Invocation extends Joinpoint {
   @Nullable Object[] getArguments();
}
