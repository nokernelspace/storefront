package org.springframework.beans;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public abstract class PropertyMatches {
   public static final int DEFAULT_MAX_DISTANCE = 2;
   private final String propertyName;
   private final String[] possibleMatches;

   public static PropertyMatches forProperty(String propertyName, Class<?> beanClass) {
      return forProperty(propertyName, beanClass, 2);
   }

   public static PropertyMatches forProperty(String propertyName, Class<?> beanClass, int maxDistance) {
      return new PropertyMatches.BeanPropertyMatches(propertyName, beanClass, maxDistance);
   }

   public static PropertyMatches forField(String propertyName, Class<?> beanClass) {
      return forField(propertyName, beanClass, 2);
   }

   public static PropertyMatches forField(String propertyName, Class<?> beanClass, int maxDistance) {
      return new PropertyMatches.FieldPropertyMatches(propertyName, beanClass, maxDistance);
   }

   private PropertyMatches(String propertyName, String[] possibleMatches) {
      this.propertyName = propertyName;
      this.possibleMatches = possibleMatches;
   }

   public String getPropertyName() {
      return this.propertyName;
   }

   public String[] getPossibleMatches() {
      return this.possibleMatches;
   }

   public abstract String buildErrorMessage();

   protected void appendHintMessage(StringBuilder msg) {
      msg.append("Did you mean ");

      for (int i = 0; i < this.possibleMatches.length; i++) {
         msg.append('\'');
         msg.append(this.possibleMatches[i]);
         if (i < this.possibleMatches.length - 2) {
            msg.append("', ");
         } else if (i == this.possibleMatches.length - 2) {
            msg.append("', or ");
         }
      }

      msg.append("'?");
   }

   private static int calculateStringDistance(String s1, String s2) {
      if (s1.isEmpty()) {
         return s2.length();
      }

      if (s2.isEmpty()) {
         return s1.length();
      }

      int[][] d = new int[s1.length() + 1][s2.length() + 1];
      int i = 0;

      while (i <= s1.length()) {
         d[i][0] = i++;
      }

      i = 0;

      while (i <= s2.length()) {
         d[0][i] = i++;
      }

      for (int ix = 1; ix <= s1.length(); ix++) {
         char c1 = s1.charAt(ix - 1);

         for (int j = 1; j <= s2.length(); j++) {
            char c2 = s2.charAt(j - 1);
            int cost;
            if (c1 == c2) {
               cost = 0;
            } else {
               cost = 1;
            }

            d[ix][j] = Math.min(Math.min(d[ix - 1][j] + 1, d[ix][j - 1] + 1), d[ix - 1][j - 1] + cost);
         }
      }

      return d[s1.length()][s2.length()];
   }

   private static class BeanPropertyMatches extends PropertyMatches {
      public BeanPropertyMatches(String propertyName, Class<?> beanClass, int maxDistance) {
         super(propertyName, calculateMatches(propertyName, BeanUtils.getPropertyDescriptors(beanClass), maxDistance));
      }

      private static String[] calculateMatches(String name, PropertyDescriptor[] descriptors, int maxDistance) {
         List<String> candidates = new ArrayList<>();

         for (PropertyDescriptor pd : descriptors) {
            if (pd.getWriteMethod() != null) {
               String possibleAlternative = pd.getName();
               if (PropertyMatches.calculateStringDistance(name, possibleAlternative) <= maxDistance) {
                  candidates.add(possibleAlternative);
               }
            }
         }

         Collections.sort(candidates);
         return StringUtils.toStringArray(candidates);
      }

      @Override
      public String buildErrorMessage() {
         StringBuilder msg = new StringBuilder(160);
         msg.append("Bean property '").append(this.getPropertyName()).append("' is not writable or has an invalid setter method. ");
         if (!ObjectUtils.isEmpty(this.getPossibleMatches())) {
            this.appendHintMessage(msg);
         } else {
            msg.append("Does the parameter type of the setter match the return type of the getter?");
         }

         return msg.toString();
      }
   }

   private static class FieldPropertyMatches extends PropertyMatches {
      public FieldPropertyMatches(String propertyName, Class<?> beanClass, int maxDistance) {
         super(propertyName, calculateMatches(propertyName, beanClass, maxDistance));
      }

      private static String[] calculateMatches(final String name, Class<?> clazz, final int maxDistance) {
         List<String> candidates = new ArrayList<>();
         ReflectionUtils.doWithFields(clazz, field -> {
            String possibleAlternative = field.getName();
            if (PropertyMatches.calculateStringDistance(name, possibleAlternative) <= maxDistance) {
               candidates.add(possibleAlternative);
            }
         });
         Collections.sort(candidates);
         return StringUtils.toStringArray(candidates);
      }

      @Override
      public String buildErrorMessage() {
         StringBuilder msg = new StringBuilder(80);
         msg.append("Bean property '").append(this.getPropertyName()).append("' has no matching field.");
         if (!ObjectUtils.isEmpty(this.getPossibleMatches())) {
            msg.append(' ');
            this.appendHintMessage(msg);
         }

         return msg.toString();
      }
   }
}
