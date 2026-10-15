package org.springframework.cglib.core;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MethodWrapper {
   private MethodWrapper() {
   }

   public static Object create(Method method) {
      return new MethodWrapper.MethodWrapperKey(
         method.getName(), Arrays.asList(ReflectUtils.getNames(method.getParameterTypes())), method.getReturnType().getName()
      );
   }

   public static Set createSet(Collection methods) {
      Set set = new HashSet();
      Iterator it = methods.iterator();

      while (it.hasNext()) {
         set.add(create((Method)it.next()));
      }

      return set;
   }

   private record MethodWrapperKey(String name, List<String> parameterTypes, String returnType) {
   }
}
