package org.springframework.scripting.bsh;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.Primitive;
import bsh.XThis;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

@Deprecated(since = "7.0")
public abstract class BshScriptUtils {
   public static Object createBshObject(String scriptSource) throws EvalError {
      return createBshObject(scriptSource, null, null);
   }

   public static Object createBshObject(String scriptSource, Class<?> @Nullable ... scriptInterfaces) throws EvalError {
      return createBshObject(scriptSource, scriptInterfaces, ClassUtils.getDefaultClassLoader());
   }

   public static Object createBshObject(String scriptSource, Class<?> @Nullable [] scriptInterfaces, @Nullable ClassLoader classLoader) throws EvalError {
      Object result = evaluateBshScript(scriptSource, scriptInterfaces, classLoader);
      if (result instanceof Class<?> clazz) {
         try {
            return ReflectionUtils.accessibleConstructor(clazz, new Class[0]).newInstance();
         } catch (Throwable ex) {
            throw new IllegalStateException("Could not instantiate script class: " + clazz.getName(), ex);
         }
      } else {
         return result;
      }
   }

   static @Nullable Class<?> determineBshObjectType(String scriptSource, @Nullable ClassLoader classLoader) throws EvalError {
      Assert.hasText(scriptSource, "Script source must not be empty");
      Interpreter interpreter = new Interpreter();
      if (classLoader != null) {
         interpreter.setClassLoader(classLoader);
      }

      Object result = interpreter.eval(scriptSource);
      if (result instanceof Class<?> clazz) {
         return clazz;
      } else {
         return result != null ? result.getClass() : null;
      }
   }

   static Object evaluateBshScript(String scriptSource, Class<?> @Nullable [] scriptInterfaces, @Nullable ClassLoader classLoader) throws EvalError {
      Assert.hasText(scriptSource, "Script source must not be empty");
      Interpreter interpreter = new Interpreter();
      interpreter.setClassLoader(classLoader);
      Object result = interpreter.eval(scriptSource);
      if (result != null) {
         return result;
      }

      if (ObjectUtils.isEmpty(scriptInterfaces)) {
         throw new IllegalArgumentException("Given script requires a script proxy: At least one script interface is required.\nScript: " + scriptSource);
      }

      XThis xt = (XThis)interpreter.eval("return this");
      return Proxy.newProxyInstance(classLoader, scriptInterfaces, new BshScriptUtils.BshObjectInvocationHandler(xt));
   }

   public static final class BshExecutionException extends NestedRuntimeException {
      private BshExecutionException(EvalError ex) {
         super("BeanShell script execution failed", ex);
      }
   }

   private static class BshObjectInvocationHandler implements InvocationHandler {
      private final XThis xt;

      public BshObjectInvocationHandler(XThis xt) {
         this.xt = xt;
      }

      @Override
      public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         if (ReflectionUtils.isEqualsMethod(method)) {
            return this.isProxyForSameBshObject(args[0]);
         }

         if (ReflectionUtils.isHashCodeMethod(method)) {
            return this.xt.hashCode();
         }

         if (ReflectionUtils.isToStringMethod(method)) {
            return "BeanShell object [" + this.xt + "]";
         }

         try {
            Object result = this.xt.invokeMethod(method.getName(), args);
            if (result == Primitive.NULL || result == Primitive.VOID) {
               return null;
            } else {
               return result instanceof Primitive primitive ? primitive.getValue() : result;
            }
         } catch (EvalError ex) {
            throw new BshScriptUtils.BshExecutionException(ex);
         }
      }

      private boolean isProxyForSameBshObject(Object obj) {
         return !Proxy.isProxyClass(obj.getClass())
            ? false
            : Proxy.getInvocationHandler(obj) instanceof BshScriptUtils.BshObjectInvocationHandler that && this.xt.equals(that.xt);
      }
   }
}
