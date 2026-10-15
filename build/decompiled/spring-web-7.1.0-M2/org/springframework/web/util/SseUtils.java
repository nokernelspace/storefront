package org.springframework.web.util;

import org.springframework.util.Assert;

public abstract class SseUtils {
   public static void appendFieldValue(String field, String value, StringBuilder output) {
      if (value.indexOf(10) == -1 && value.indexOf(13) == -1) {
         output.append(value);
      } else {
         String lineSeparatorReplacement = "\n" + field + (field.isEmpty() ? ":" : ": ");
         int length = value.length();

         for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c == '\r') {
               if (i + 1 < length && value.charAt(i + 1) == '\n') {
                  i++;
               }

               output.append(lineSeparatorReplacement);
            } else if (c == '\n') {
               output.append(lineSeparatorReplacement);
            } else {
               output.append(c);
            }
         }
      }
   }

   public static void assertNoLineSeparator(String content) {
      Assert.isTrue(content.indexOf(10) == -1 && content.indexOf(13) == -1, "illegal character '\\n' or '\\r' in event content");
   }
}
