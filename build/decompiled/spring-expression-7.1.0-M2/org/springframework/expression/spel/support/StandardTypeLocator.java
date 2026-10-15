package org.springframework.expression.spel.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.SmartClassLoader;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.ClassUtils;

public class StandardTypeLocator implements TypeLocator {
   private final @Nullable ClassLoader classLoader;
   private final List<String> importPrefixes = new ArrayList<>(1);
   private final Map<String, Class<?>> typeCache = new ConcurrentHashMap<>();

   public StandardTypeLocator() {
      this(ClassUtils.getDefaultClassLoader());
   }

   public StandardTypeLocator(@Nullable ClassLoader classLoader) {
      this.classLoader = classLoader;
      this.registerImport("java.lang");
   }

   public void registerImport(String prefix) {
      this.importPrefixes.add(prefix);
   }

   public void removeImport(String prefix) {
      this.importPrefixes.remove(prefix);
   }

   public List<String> getImportPrefixes() {
      return Collections.unmodifiableList(this.importPrefixes);
   }

   @Override
   public Class<?> findType(String typeName) throws EvaluationException {
      Class<?> cachedType = this.typeCache.get(typeName);
      if (cachedType != null) {
         return cachedType;
      }

      Class<?> loadedType = this.loadType(typeName);
      if (loadedType == null) {
         throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
      }

      if (!(this.classLoader instanceof SmartClassLoader scl && scl.isClassReloadable(loadedType))) {
         this.typeCache.put(typeName, loadedType);
      }

      return loadedType;
   }

   private @Nullable Class<?> loadType(String typeName) {
      try {
         return ClassUtils.forName(typeName, this.classLoader);
      } catch (ClassNotFoundException var6) {
         for (String prefix : this.importPrefixes) {
            try {
               String nameToLookup = prefix + "." + typeName;
               return ClassUtils.forName(nameToLookup, this.classLoader);
            } catch (ClassNotFoundException var5) {
            }
         }

         return null;
      }
   }
}
