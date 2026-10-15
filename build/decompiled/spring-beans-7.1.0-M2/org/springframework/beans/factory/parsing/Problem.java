package org.springframework.beans.factory.parsing;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class Problem {
   private final String message;
   private final Location location;
   private final @Nullable ParseState parseState;
   private final @Nullable Throwable rootCause;

   public Problem(String message, Location location) {
      this(message, location, null, null);
   }

   public Problem(String message, Location location, ParseState parseState) {
      this(message, location, parseState, null);
   }

   public Problem(String message, Location location, @Nullable ParseState parseState, @Nullable Throwable rootCause) {
      Assert.notNull(message, "Message must not be null");
      Assert.notNull(location, "Location must not be null");
      this.message = message;
      this.location = location;
      this.parseState = parseState;
      this.rootCause = rootCause;
   }

   public String getMessage() {
      return this.message;
   }

   public Location getLocation() {
      return this.location;
   }

   public String getResourceDescription() {
      return this.getLocation().getResource().getDescription();
   }

   public @Nullable ParseState getParseState() {
      return this.parseState;
   }

   public @Nullable Throwable getRootCause() {
      return this.rootCause;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Configuration problem: ");
      sb.append(this.getMessage());
      sb.append("\nOffending resource: ").append(this.getResourceDescription());
      if (this.getParseState() != null) {
         sb.append('\n').append(this.getParseState());
      }

      return sb.toString();
   }
}
