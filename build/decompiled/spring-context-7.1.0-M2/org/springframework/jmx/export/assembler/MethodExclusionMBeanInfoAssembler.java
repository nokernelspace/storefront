package org.springframework.jmx.export.assembler;

import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class MethodExclusionMBeanInfoAssembler extends AbstractConfigurableMBeanInfoAssembler {
   private @Nullable Set<String> ignoredMethods;
   private @Nullable Map<String, Set<String>> ignoredMethodMappings;

   public void setIgnoredMethods(String... ignoredMethodNames) {
      this.ignoredMethods = Set.of(ignoredMethodNames);
   }

   public void setIgnoredMethodMappings(Properties mappings) {
      this.ignoredMethodMappings = new HashMap<>();
      Enumeration<?> en = mappings.keys();

      while (en.hasMoreElements()) {
         String beanKey = (String)en.nextElement();
         String[] methodNames = StringUtils.commaDelimitedListToStringArray(mappings.getProperty(beanKey));
         this.ignoredMethodMappings.put(beanKey, Set.of(methodNames));
      }
   }

   @Override
   protected boolean includeReadAttribute(Method method, String beanKey) {
      return this.isNotIgnored(method, beanKey);
   }

   @Override
   protected boolean includeWriteAttribute(Method method, String beanKey) {
      return this.isNotIgnored(method, beanKey);
   }

   @Override
   protected boolean includeOperation(Method method, String beanKey) {
      return this.isNotIgnored(method, beanKey);
   }

   protected boolean isNotIgnored(Method method, String beanKey) {
      if (this.ignoredMethodMappings != null) {
         Set<String> methodNames = this.ignoredMethodMappings.get(beanKey);
         if (methodNames != null) {
            return !methodNames.contains(method.getName());
         }
      }

      return this.ignoredMethods != null ? !this.ignoredMethods.contains(method.getName()) : true;
   }
}
