package org.aopalliance.intercept;

import java.lang.reflect.AccessibleObject;
import org.jspecify.annotations.Nullable;

public interface Joinpoint {
   @Nullable Object proceed() throws Throwable;

   @Nullable Object getThis();

   AccessibleObject getStaticPart();
}
