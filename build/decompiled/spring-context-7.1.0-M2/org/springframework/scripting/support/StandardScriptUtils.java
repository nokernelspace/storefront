package org.springframework.scripting.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

public abstract class StandardScriptUtils {
   public static ScriptEngine retrieveEngineByName(ScriptEngineManager scriptEngineManager, String engineName) {
      ScriptEngine engine = scriptEngineManager.getEngineByName(engineName);
      if (engine == null) {
         Set<String> engineNames = new LinkedHashSet<>();

         for (ScriptEngineFactory engineFactory : scriptEngineManager.getEngineFactories()) {
            List<String> factoryNames = engineFactory.getNames();
            if (factoryNames.contains(engineName)) {
               try {
                  engine = engineFactory.getScriptEngine();
                  engine.setBindings(scriptEngineManager.getBindings(), 200);
               } catch (Throwable ex) {
                  throw new IllegalStateException("Script engine with name '" + engineName + "' failed to initialize", ex);
               }
            }

            engineNames.addAll(factoryNames);
         }

         throw new IllegalArgumentException("Script engine with name '" + engineName + "' not found; registered engine names: " + engineNames);
      } else {
         return engine;
      }
   }

   static Bindings getBindings(Map<String, Object> bindings) {
      return bindings instanceof Bindings b ? b : new SimpleBindings(bindings);
   }
}
