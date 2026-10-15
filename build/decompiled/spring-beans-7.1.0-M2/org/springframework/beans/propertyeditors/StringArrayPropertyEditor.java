package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class StringArrayPropertyEditor extends PropertyEditorSupport {
   public static final String DEFAULT_SEPARATOR = ",";
   private final String separator;
   private final @Nullable String charsToDelete;
   private final boolean emptyArrayAsNull;
   private final boolean trimValues;

   public StringArrayPropertyEditor() {
      this(",", null, false);
   }

   public StringArrayPropertyEditor(String separator) {
      this(separator, null, false);
   }

   public StringArrayPropertyEditor(String separator, boolean emptyArrayAsNull) {
      this(separator, null, emptyArrayAsNull);
   }

   public StringArrayPropertyEditor(String separator, boolean emptyArrayAsNull, boolean trimValues) {
      this(separator, null, emptyArrayAsNull, trimValues);
   }

   public StringArrayPropertyEditor(String separator, @Nullable String charsToDelete, boolean emptyArrayAsNull) {
      this(separator, charsToDelete, emptyArrayAsNull, true);
   }

   public StringArrayPropertyEditor(String separator, @Nullable String charsToDelete, boolean emptyArrayAsNull, boolean trimValues) {
      this.separator = separator;
      this.charsToDelete = charsToDelete;
      this.emptyArrayAsNull = emptyArrayAsNull;
      this.trimValues = trimValues;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      String[] array = StringUtils.delimitedListToStringArray(text, this.separator, this.charsToDelete);
      if (this.emptyArrayAsNull && array.length == 0) {
         this.setValue(null);
      } else {
         if (this.trimValues) {
            array = StringUtils.trimArrayElements(array);
         }

         this.setValue(array);
      }
   }

   @Override
   public String getAsText() {
      return StringUtils.arrayToDelimitedString(ObjectUtils.toObjectArray(this.getValue()), this.separator);
   }
}
