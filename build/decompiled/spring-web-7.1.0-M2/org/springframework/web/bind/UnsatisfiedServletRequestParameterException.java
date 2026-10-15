package org.springframework.web.bind;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class UnsatisfiedServletRequestParameterException extends ServletRequestBindingException {
   private final List<String[]> paramConditions;
   private final Map<String, String[]> actualParams;

   public UnsatisfiedServletRequestParameterException(String[] paramConditions, Map<String, String[]> actualParams) {
      this(List.of(paramConditions), actualParams);
   }

   public UnsatisfiedServletRequestParameterException(List<String[]> paramConditions, Map<String, String[]> actualParams) {
      super("", null, new Object[]{paramsToStringList(paramConditions)});
      this.paramConditions = paramConditions;
      this.actualParams = actualParams;
      this.getBody().setDetail("Invalid request parameters.");
   }

   private static List<String> paramsToStringList(List<String[]> paramConditions) {
      Assert.notEmpty(paramConditions, "Parameter conditions must not be empty");
      return paramConditions.stream().map(condition -> "\"" + StringUtils.arrayToDelimitedString(condition, ", ") + "\"").collect(Collectors.toList());
   }

   public String getMessage() {
      StringBuilder sb = new StringBuilder("Parameter conditions ");
      sb.append(String.join(" OR ", paramsToStringList(this.paramConditions)));
      sb.append(" not met for actual request parameters: ");
      sb.append(requestParameterMapToString(this.actualParams));
      return sb.toString();
   }

   public final String[] getParamConditions() {
      return this.paramConditions.get(0);
   }

   public final List<String[]> getParamConditionGroups() {
      return this.paramConditions;
   }

   public final Map<String, String[]> getActualParams() {
      return this.actualParams;
   }

   private static String requestParameterMapToString(Map<String, String[]> actualParams) {
      StringBuilder result = new StringBuilder();
      Iterator<Entry<String, String[]>> it = actualParams.entrySet().iterator();

      while (it.hasNext()) {
         Entry<String, String[]> entry = it.next();
         result.append(entry.getKey()).append('=').append(ObjectUtils.nullSafeToString(entry.getValue()));
         if (it.hasNext()) {
            result.append(", ");
         }
      }

      return result.toString();
   }
}
