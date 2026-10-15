package org.springframework.beans.factory.parsing;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class Location {
   private final Resource resource;
   private final @Nullable Object source;

   public Location(Resource resource) {
      this(resource, null);
   }

   public Location(Resource resource, @Nullable Object source) {
      Assert.notNull(resource, "Resource must not be null");
      this.resource = resource;
      this.source = source;
   }

   public Resource getResource() {
      return this.resource;
   }

   public @Nullable Object getSource() {
      return this.source;
   }
}
