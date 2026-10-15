package org.springframework.aop.config;

import org.springframework.beans.factory.parsing.ParseState.Entry;
import org.springframework.util.StringUtils;

public class AspectEntry implements Entry {
   private final String id;
   private final String ref;

   public AspectEntry(String id, String ref) {
      this.id = id;
      this.ref = ref;
   }

   @Override
   public String toString() {
      return "Aspect: " + (StringUtils.hasLength(this.id) ? "id='" + this.id + "'" : "ref='" + this.ref + "'");
   }
}
