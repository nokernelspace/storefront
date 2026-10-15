package org.springframework.scripting.bsh;

import bsh.EvalError;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.scripting.ScriptCompilationException;
import org.springframework.scripting.ScriptFactory;
import org.springframework.scripting.ScriptSource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

@Deprecated(since = "7.0")
public class BshScriptFactory implements ScriptFactory, BeanClassLoaderAware {
   private final String scriptSourceLocator;
   private final Class<?> @Nullable [] scriptInterfaces;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable Class<?> scriptClass;
   private final Object scriptClassMonitor = new Object();
   private boolean wasModifiedForTypeCheck = false;

   public BshScriptFactory(String scriptSourceLocator) {
      Assert.hasText(scriptSourceLocator, "'scriptSourceLocator' must not be empty");
      this.scriptSourceLocator = scriptSourceLocator;
      this.scriptInterfaces = null;
   }

   public BshScriptFactory(String scriptSourceLocator, Class<?> @Nullable ... scriptInterfaces) {
      Assert.hasText(scriptSourceLocator, "'scriptSourceLocator' must not be empty");
      this.scriptSourceLocator = scriptSourceLocator;
      this.scriptInterfaces = scriptInterfaces;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   @Override
   public String getScriptSourceLocator() {
      return this.scriptSourceLocator;
   }

   @Override
   public Class<?> @Nullable [] getScriptInterfaces() {
      return this.scriptInterfaces;
   }

   @Override
   public boolean requiresConfigInterface() {
      return true;
   }

   @Override
   public @Nullable Object getScriptedObject(ScriptSource scriptSource, Class<?> @Nullable ... actualInterfaces) throws IOException, ScriptCompilationException {
      Class<?> clazz;
      try {
         synchronized (this.scriptClassMonitor) {
            boolean requiresScriptEvaluation = this.wasModifiedForTypeCheck && this.scriptClass == null;
            this.wasModifiedForTypeCheck = false;
            if (scriptSource.isModified() || requiresScriptEvaluation) {
               Object result = BshScriptUtils.evaluateBshScript(scriptSource.getScriptAsString(), actualInterfaces, this.beanClassLoader);
               if (!(result instanceof Class<?> type)) {
                  return result;
               }

               this.scriptClass = type;
            }

            clazz = this.scriptClass;
         }
      } catch (EvalError ex) {
         this.scriptClass = null;
         throw new ScriptCompilationException(scriptSource, ex);
      }

      if (clazz != null) {
         try {
            return ReflectionUtils.accessibleConstructor(clazz, new Class[0]).newInstance();
         } catch (Throwable ex) {
            throw new ScriptCompilationException(scriptSource, "Could not instantiate script class: " + clazz.getName(), ex);
         }
      } else {
         try {
            return BshScriptUtils.createBshObject(scriptSource.getScriptAsString(), actualInterfaces, this.beanClassLoader);
         } catch (EvalError ex) {
            throw new ScriptCompilationException(scriptSource, ex);
         }
      }
   }

   @Override
   public @Nullable Class<?> getScriptedObjectType(ScriptSource scriptSource) throws IOException, ScriptCompilationException {
      synchronized (this.scriptClassMonitor) {
         Class var10000;
         try {
            if (scriptSource.isModified()) {
               this.wasModifiedForTypeCheck = true;
               this.scriptClass = BshScriptUtils.determineBshObjectType(scriptSource.getScriptAsString(), this.beanClassLoader);
            }

            var10000 = this.scriptClass;
         } catch (EvalError ex) {
            this.scriptClass = null;
            throw new ScriptCompilationException(scriptSource, ex);
         }

         return var10000;
      }
   }

   @Override
   public boolean requiresScriptedObjectRefresh(ScriptSource scriptSource) {
      synchronized (this.scriptClassMonitor) {
         return scriptSource.isModified() || this.wasModifiedForTypeCheck;
      }
   }

   @Override
   public String toString() {
      return "BshScriptFactory: script source locator [" + this.scriptSourceLocator + "]";
   }
}
