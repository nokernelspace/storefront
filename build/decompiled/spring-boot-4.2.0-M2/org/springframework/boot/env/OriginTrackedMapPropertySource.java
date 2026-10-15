package org.springframework.boot.env;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.core.env.MapPropertySource;

public final class OriginTrackedMapPropertySource extends MapPropertySource implements PropertySourceInfo, OriginLookup<String> {
   private final boolean immutable;

   public OriginTrackedMapPropertySource(String name, Map source) {
      this(name, source, false);
   }

   public OriginTrackedMapPropertySource(String name, Map source, boolean immutable) {
      super(name, source);
      this.immutable = immutable;
   }

   public @Nullable Object getProperty(String name) {
      Object value = super.getProperty(name);
      return value instanceof OriginTrackedValue originTrackedValue ? originTrackedValue.getValue() : value;
   }

   public @Nullable Origin getOrigin(String name) {
      return super.getProperty(name) instanceof OriginTrackedValue originTrackedValue ? originTrackedValue.getOrigin() : null;
   }

   @Override
   public boolean isImmutable() {
      return this.immutable;
   }
}
