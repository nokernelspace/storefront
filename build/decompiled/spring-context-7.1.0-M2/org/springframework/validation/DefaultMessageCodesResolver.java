package org.springframework.validation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class DefaultMessageCodesResolver implements MessageCodesResolver, Serializable {
   public static final String CODE_SEPARATOR = ".";
   private static final MessageCodeFormatter DEFAULT_FORMATTER = DefaultMessageCodesResolver.Format.PREFIX_ERROR_CODE;
   private String prefix = "";
   private MessageCodeFormatter formatter = DEFAULT_FORMATTER;

   public void setPrefix(@Nullable String prefix) {
      this.prefix = prefix != null ? prefix : "";
   }

   protected String getPrefix() {
      return this.prefix;
   }

   public void setMessageCodeFormatter(@Nullable MessageCodeFormatter formatter) {
      this.formatter = formatter != null ? formatter : DEFAULT_FORMATTER;
   }

   @Override
   public String[] resolveMessageCodes(String errorCode, String objectName) {
      return this.resolveMessageCodes(errorCode, objectName, "", null);
   }

   @Override
   public String[] resolveMessageCodes(String errorCode, String objectName, String field, @Nullable Class<?> fieldType) {
      Set<String> codeList = new LinkedHashSet<>();
      List<String> fieldList = new ArrayList<>();
      this.buildFieldList(field, fieldList);
      this.addCodes(codeList, errorCode, objectName, fieldList);
      int dotIndex = field.lastIndexOf(46);
      if (dotIndex != -1) {
         this.buildFieldList(field.substring(dotIndex + 1), fieldList);
      }

      this.addCodes(codeList, errorCode, null, fieldList);
      if (fieldType != null) {
         this.addCode(codeList, errorCode, null, fieldType.getName());
      }

      this.addCode(codeList, errorCode, null, null);
      return StringUtils.toStringArray(codeList);
   }

   private void addCodes(Collection<String> codeList, String errorCode, @Nullable String objectName, Iterable<String> fields) {
      for (String field : fields) {
         this.addCode(codeList, errorCode, objectName, field);
      }
   }

   private void addCode(Collection<String> codeList, String errorCode, @Nullable String objectName, @Nullable String field) {
      codeList.add(this.postProcessMessageCode(this.formatter.format(errorCode, objectName, field)));
   }

   protected void buildFieldList(String field, List<String> fieldList) {
      fieldList.add(field);
      String plainField = field;
      int keyIndex = plainField.lastIndexOf(91);

      while (keyIndex != -1) {
         int endKeyIndex = plainField.indexOf(93, keyIndex);
         if (endKeyIndex != -1) {
            plainField = plainField.substring(0, keyIndex) + plainField.substring(endKeyIndex + 1);
            fieldList.add(plainField);
            keyIndex = plainField.lastIndexOf(91);
         } else {
            keyIndex = -1;
         }
      }
   }

   protected String postProcessMessageCode(String code) {
      return this.getPrefix() + code;
   }

   public enum Format implements MessageCodeFormatter {
      PREFIX_ERROR_CODE {
         @Override
         public String format(String errorCode, @Nullable String objectName, @Nullable String field) {
            return toDelimitedString(errorCode, objectName, field);
         }
      },
      POSTFIX_ERROR_CODE {
         @Override
         public String format(String errorCode, @Nullable String objectName, @Nullable String field) {
            return toDelimitedString(objectName, field, errorCode);
         }
      };

      public static String toDelimitedString(@Nullable String... elements) {
         StringJoiner rtn = new StringJoiner(".");

         for (String element : elements) {
            if (StringUtils.hasLength(element)) {
               rtn.add(element);
            }
         }

         return rtn.toString();
      }
   }
}
