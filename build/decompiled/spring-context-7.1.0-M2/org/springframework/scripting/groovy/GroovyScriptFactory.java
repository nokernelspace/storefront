package org.springframework.scripting.groovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.Script;
import java.io.IOException;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.scripting.ScriptCompilationException;
import org.springframework.scripting.ScriptFactory;
import org.springframework.scripting.ScriptSource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

public class GroovyScriptFactory implements ScriptFactory, BeanFactoryAware, BeanClassLoaderAware {
   private final String scriptSourceLocator;
   private @Nullable GroovyObjectCustomizer groovyObjectCustomizer;
   private @Nullable CompilerConfiguration compilerConfiguration;
   private @Nullable GroovyClassLoader groovyClassLoader;
   private @Nullable Class<?> scriptClass;
   private @Nullable Class<?> scriptResultClass;
   private GroovyScriptFactory.@Nullable CachedResultHolder cachedResult;
   private final Object scriptClassMonitor = new Object();
   private boolean wasModifiedForTypeCheck = false;

   public GroovyScriptFactory(String scriptSourceLocator) {
      Assert.hasText(scriptSourceLocator, "'scriptSourceLocator' must not be empty");
      this.scriptSourceLocator = scriptSourceLocator;
   }

   public GroovyScriptFactory(String scriptSourceLocator, @Nullable GroovyObjectCustomizer groovyObjectCustomizer) {
      this(scriptSourceLocator);
      this.groovyObjectCustomizer = groovyObjectCustomizer;
   }

   public GroovyScriptFactory(String scriptSourceLocator, @Nullable CompilerConfiguration compilerConfiguration) {
      this(scriptSourceLocator);
      this.compilerConfiguration = compilerConfiguration;
   }

   public GroovyScriptFactory(String scriptSourceLocator, CompilationCustomizer... compilationCustomizers) {
      this(scriptSourceLocator);
      if (!ObjectUtils.isEmpty(compilationCustomizers)) {
         this.compilerConfiguration = new CompilerConfiguration();
         this.compilerConfiguration.addCompilationCustomizers(compilationCustomizers);
      }
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
         clbf.ignoreDependencyType(MetaClass.class);
      }
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      if (!(classLoader instanceof GroovyClassLoader gcl && (this.compilerConfiguration == null || gcl.hasCompatibleConfiguration(this.compilerConfiguration)))
         )
       {
         this.groovyClassLoader = this.buildGroovyClassLoader(classLoader);
      } else {
         this.groovyClassLoader = gcl;
      }
   }

   public GroovyClassLoader getGroovyClassLoader() {
      synchronized (this.scriptClassMonitor) {
         if (this.groovyClassLoader == null) {
            this.groovyClassLoader = this.buildGroovyClassLoader(ClassUtils.getDefaultClassLoader());
         }

         return this.groovyClassLoader;
      }
   }

   protected GroovyClassLoader buildGroovyClassLoader(@Nullable ClassLoader classLoader) {
      return this.compilerConfiguration != null ? new GroovyClassLoader(classLoader, this.compilerConfiguration) : new GroovyClassLoader(classLoader);
   }

   @Override
   public String getScriptSourceLocator() {
      return this.scriptSourceLocator;
   }

   @Override
   public Class<?> @Nullable [] getScriptInterfaces() {
      return null;
   }

   @Override
   public boolean requiresConfigInterface() {
      return false;
   }

   @Override
   public @Nullable Object getScriptedObject(ScriptSource scriptSource, Class<?> @Nullable ... actualInterfaces) throws IOException, ScriptCompilationException {
      synchronized (this.scriptClassMonitor) {
         Object var10000;
         try {
            this.wasModifiedForTypeCheck = false;
            if (this.cachedResult != null) {
               Object result = this.cachedResult.object;
               this.cachedResult = null;
               return result;
            }

            if (this.scriptClass == null || scriptSource.isModified()) {
               this.scriptClass = this.getGroovyClassLoader().parseClass(scriptSource.getScriptAsString(), scriptSource.suggestedClassName());
               if (Script.class.isAssignableFrom(this.scriptClass)) {
                  Object result = this.executeScript(scriptSource, this.scriptClass);
                  this.scriptResultClass = result != null ? result.getClass() : null;
                  return result;
               }

               this.scriptResultClass = this.scriptClass;
            }

            Class<?> scriptClassToExecute = this.scriptClass;
            var10000 = this.executeScript(scriptSource, scriptClassToExecute);
         } catch (CompilationFailedException ex) {
            this.scriptClass = null;
            this.scriptResultClass = null;
            throw new ScriptCompilationException(scriptSource, ex);
         }

         return var10000;
      }
   }

   @Override
   public @Nullable Class<?> getScriptedObjectType(ScriptSource scriptSource) throws IOException, ScriptCompilationException {
      synchronized (this.scriptClassMonitor) {
         Class var10000;
         try {
            if (this.scriptClass == null || scriptSource.isModified()) {
               this.wasModifiedForTypeCheck = true;
               this.scriptClass = this.getGroovyClassLoader().parseClass(scriptSource.getScriptAsString(), scriptSource.suggestedClassName());
               if (Script.class.isAssignableFrom(this.scriptClass)) {
                  Object result = this.executeScript(scriptSource, this.scriptClass);
                  this.scriptResultClass = result != null ? result.getClass() : null;
                  this.cachedResult = new GroovyScriptFactory.CachedResultHolder(result);
               } else {
                  this.scriptResultClass = this.scriptClass;
               }
            }

            var10000 = this.scriptResultClass;
         } catch (CompilationFailedException ex) {
            this.scriptClass = null;
            this.scriptResultClass = null;
            this.cachedResult = null;
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

   protected @Nullable Object executeScript(ScriptSource scriptSource, Class<?> scriptClass) throws ScriptCompilationException {
      try {
         GroovyObject groovyObj = (GroovyObject)ReflectionUtils.accessibleConstructor(scriptClass, new Class[0]).newInstance();
         if (this.groovyObjectCustomizer != null) {
            this.groovyObjectCustomizer.customize(groovyObj);
         }

         return groovyObj instanceof Script script ? script.run() : groovyObj;
      } catch (NoSuchMethodException ex) {
         throw new ScriptCompilationException("No default constructor on Groovy script class: " + scriptClass.getName(), ex);
      } catch (InstantiationException ex) {
         throw new ScriptCompilationException(scriptSource, "Unable to instantiate Groovy script class: " + scriptClass.getName(), ex);
      } catch (IllegalAccessException | InaccessibleObjectException ex) {
         throw new ScriptCompilationException(scriptSource, "Could not access Groovy script constructor: " + scriptClass.getName(), ex);
      } catch (InvocationTargetException ex) {
         throw new ScriptCompilationException("Failed to invoke Groovy script constructor: " + scriptClass.getName(), ex.getTargetException());
      }
   }

   @Override
   public String toString() {
      return "GroovyScriptFactory: script source locator [" + this.scriptSourceLocator + "]";
   }

   private static class CachedResultHolder {
      public final @Nullable Object object;

      public CachedResultHolder(@Nullable Object object) {
         this.object = object;
      }
   }
}
