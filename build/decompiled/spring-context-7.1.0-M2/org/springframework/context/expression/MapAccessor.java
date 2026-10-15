package org.springframework.context.expression;

@Deprecated(since = "7.0", forRemoval = true)
public class MapAccessor extends org.springframework.expression.spel.support.MapAccessor {
   public MapAccessor() {
      this(true);
   }

   public MapAccessor(boolean allowWrite) {
      super(allowWrite);
   }
}
