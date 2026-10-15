package org.springframework.jmx.export.assembler;

import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class MethodNameBasedMBeanInfoAssembler extends AbstractConfigurableMBeanInfoAssembler {
   private @Nullable Set<String> managedMethods;
   private @Nullable Map<String, Set<String>> methodMappings;

   public void setManagedMethods(String... methodNames) {
      this.managedMethods = Set.of(methodNames);
   }

   public void setMethodMappings(Properties mappings) {
      this.methodMappings = new HashMap<>();
      Enumeration<?> en = mappings.keys();

      while (en.hasMoreElements()) {
         String beanKey = (String)en.nextElement();
         String[] methodNames = StringUtils.commaDelimitedListToStringArray(mappings.getProperty(beanKey));
         this.methodMappings.put(beanKey, Set.of(methodNames));
      }
   }

   @Override
   protected boolean includeReadAttribute(Method method, String beanKey) {
      return this.isMatch(method, beanKey);
   }

   @Override
   protected boolean includeWriteAttribute(Method method, String beanKey) {
      return this.isMatch(method, beanKey);
   }

   @Override
   protected boolean includeOperation(Method method, String beanKey) {
      return this.isMatch(method, beanKey);
   }

   protected boolean isMatch(Method method, String beanKey) {
      if (this.methodMappings != null) {
         Set<String> methodNames = this.methodMappings.get(beanKey);
         if (methodNames != null) {
            return methodNames.contains(method.getName());
         }
      }

      return this.managedMethods != null && this.managedMethods.contains(method.getName());
   }
}
