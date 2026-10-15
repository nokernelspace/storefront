package org.springframework.aop.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.IntroductionInfo;
import org.springframework.util.ClassUtils;

public class IntroductionInfoSupport implements IntroductionInfo, Serializable {
   protected final Set<Class<?>> publishedInterfaces = new LinkedHashSet<>();
   private transient Map<Method, Boolean> rememberedMethods = new ConcurrentHashMap<>(32);

   public void suppressInterface(Class<?> ifc) {
      this.publishedInterfaces.remove(ifc);
   }

   @Override
   public Class<?>[] getInterfaces() {
      return ClassUtils.toClassArray(this.publishedInterfaces);
   }

   public boolean implementsInterface(Class<?> ifc) {
      for (Class<?> pubIfc : this.publishedInterfaces) {
         if (ifc.isInterface() && ifc.isAssignableFrom(pubIfc)) {
            return true;
         }
      }

      return false;
   }

   protected void implementInterfacesOnObject(Object delegate) {
      this.publishedInterfaces.addAll(ClassUtils.getAllInterfacesAsSet(delegate));
   }

   protected final boolean isMethodOnIntroducedInterface(MethodInvocation mi) {
      Boolean rememberedResult = this.rememberedMethods.get(mi.getMethod());
      if (rememberedResult != null) {
         return rememberedResult;
      }

      boolean result = this.implementsInterface(mi.getMethod().getDeclaringClass());
      this.rememberedMethods.put(mi.getMethod(), result);
      return result;
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.rememberedMethods = new ConcurrentHashMap<>(32);
   }
}
