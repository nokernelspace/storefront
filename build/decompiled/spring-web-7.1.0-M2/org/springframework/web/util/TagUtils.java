package org.springframework.web.util;

import jakarta.servlet.jsp.tagext.Tag;
import org.springframework.util.Assert;

public abstract class TagUtils {
   public static final String SCOPE_PAGE = "page";
   public static final String SCOPE_REQUEST = "request";
   public static final String SCOPE_SESSION = "session";
   public static final String SCOPE_APPLICATION = "application";

   public static int getScope(String scope) {
      Assert.notNull(scope, "Scope to search for cannot be null");

      return switch (scope) {
         case "request" -> 2;
         case "session" -> 3;
         case "application" -> 4;
         default -> 1;
      };
   }

   public static boolean hasAncestorOfType(Tag tag, Class<?> ancestorTagClass) {
      Assert.notNull(tag, "Tag cannot be null");
      Assert.notNull(ancestorTagClass, "Ancestor tag class cannot be null");
      if (!Tag.class.isAssignableFrom(ancestorTagClass)) {
         throw new IllegalArgumentException("Class '" + ancestorTagClass.getName() + "' is not a valid Tag type");
      }

      for (Tag ancestor = tag.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
         if (ancestorTagClass.isInstance(ancestor)) {
            return true;
         }
      }

      return false;
   }

   public static void assertHasAncestorOfType(Tag tag, Class<?> ancestorTagClass, String tagName, String ancestorTagName) {
      Assert.hasText(tagName, "'tagName' must not be empty");
      Assert.hasText(ancestorTagName, "'ancestorTagName' must not be empty");
      if (!hasAncestorOfType(tag, ancestorTagClass)) {
         throw new IllegalStateException("The '" + tagName + "' tag can only be used inside a valid '" + ancestorTagName + "' tag.");
      }
   }
}
