package org.springframework.scripting.bsh;

import bsh.EvalError;
import bsh.Interpreter;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.scripting.ScriptCompilationException;
import org.springframework.scripting.ScriptEvaluator;
import org.springframework.scripting.ScriptSource;

@Deprecated(since = "7.0")
public class BshScriptEvaluator implements ScriptEvaluator, BeanClassLoaderAware {
   private @Nullable ClassLoader classLoader;

   public BshScriptEvaluator() {
   }

   public BshScriptEvaluator(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   @Override
   public @Nullable Object evaluate(ScriptSource script) {
      return this.evaluate(script, null);
   }

   @Override
   public @Nullable Object evaluate(ScriptSource script, @Nullable Map<String, Object> arguments) {
      try {
         Interpreter interpreter = new Interpreter();
         interpreter.setClassLoader(this.classLoader);
         if (arguments != null) {
            for (Entry<String, Object> entry : arguments.entrySet()) {
               interpreter.set(entry.getKey(), entry.getValue());
            }
         }

         return interpreter.eval(new StringReader(script.getScriptAsString()));
      } catch (IOException ex) {
         throw new ScriptCompilationException(script, "Cannot access BeanShell script", ex);
      } catch (EvalError ex) {
         throw new ScriptCompilationException(script, ex);
      }
   }
}
