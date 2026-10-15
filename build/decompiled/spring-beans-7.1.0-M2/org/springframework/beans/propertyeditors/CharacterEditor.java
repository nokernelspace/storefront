package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class CharacterEditor extends PropertyEditorSupport {
   private static final String UNICODE_PREFIX = "\\u";
   private static final int UNICODE_LENGTH = 6;
   private final boolean allowEmpty;

   public CharacterEditor(boolean allowEmpty) {
      this.allowEmpty = allowEmpty;
   }

   @Override
   public void setAsText(@Nullable String text) throws IllegalArgumentException {
      if (this.allowEmpty && !StringUtils.hasLength(text)) {
         this.setValue(null);
      } else {
         if (text == null) {
            throw new IllegalArgumentException("null String cannot be converted to char type");
         }

         if (isUnicodeCharacterSequence(text)) {
            this.setAsUnicode(text);
         } else {
            if (text.length() != 1) {
               throw new IllegalArgumentException(
                  "String [" + text + "] with length " + text.length() + " cannot be converted to char type: neither Unicode nor single character"
               );
            }

            this.setValue(text.charAt(0));
         }
      }
   }

   @Override
   public String getAsText() {
      Object value = this.getValue();
      return value != null ? value.toString() : "";
   }

   private static boolean isUnicodeCharacterSequence(String sequence) {
      return sequence.startsWith("\\u") && sequence.length() == 6;
   }

   private void setAsUnicode(String text) {
      int code = HexFormat.fromHexDigits(text, "\\u".length(), text.length());
      this.setValue((char)code);
   }
}
