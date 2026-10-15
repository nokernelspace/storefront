package org.springframework.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

public class SimpleAliasRegistry implements AliasRegistry {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);
   private final List<String> aliasNames = new ArrayList<>(16);

   @Override
   public void registerAlias(String name, String alias) {
      Assert.hasText(name, "'name' must not be empty");
      Assert.hasText(alias, "'alias' must not be empty");
      synchronized (this.aliasMap) {
         if (alias.equals(name)) {
            this.aliasMap.remove(alias);
            this.aliasNames.remove(alias);
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
            }
         } else {
            String registeredName = this.aliasMap.get(alias);
            if (registeredName != null) {
               if (registeredName.equals(name)) {
                  return;
               }

               if (!this.allowAliasOverriding()) {
                  throw new IllegalStateException(
                     "Cannot define alias '" + alias + "' for name '" + name + "': It is already registered for name '" + registeredName + "'."
                  );
               }

               if (this.logger.isDebugEnabled()) {
                  this.logger
                     .debug("Overriding alias '" + alias + "' definition for registered name '" + registeredName + "' with new target name '" + name + "'");
               }
            }

            this.checkForAliasCircle(name, alias);
            this.aliasMap.put(alias, name);
            this.aliasNames.add(alias);
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
            }
         }
      }
   }

   protected boolean allowAliasOverriding() {
      return true;
   }

   public boolean hasAlias(String name, String alias) {
      String registeredName = this.aliasMap.get(alias);
      return ObjectUtils.nullSafeEquals(registeredName, name) || registeredName != null && this.hasAlias(name, registeredName);
   }

   @Override
   public void removeAlias(String alias) {
      synchronized (this.aliasMap) {
         String name = this.aliasMap.remove(alias);
         this.aliasNames.remove(alias);
         if (name == null) {
            throw new IllegalStateException("No alias '" + alias + "' registered");
         }
      }
   }

   @Override
   public boolean isAlias(String name) {
      return this.aliasMap.containsKey(name);
   }

   @Override
   public String[] getAliases(String name) {
      List<String> result = new ArrayList<>();
      synchronized (this.aliasMap) {
         this.retrieveAliases(name, result);
      }

      return StringUtils.toStringArray(result);
   }

   private void retrieveAliases(String name, List<String> result) {
      this.aliasMap.forEach((alias, registeredName) -> {
         if (registeredName.equals(name)) {
            result.add(alias);
            this.retrieveAliases(alias, result);
         }
      });
   }

   public void resolveAliases(StringValueResolver valueResolver) {
      Assert.notNull(valueResolver, "StringValueResolver must not be null");
      synchronized (this.aliasMap) {
         List<String> aliasNamesCopy = new ArrayList<>(this.aliasNames);
         aliasNamesCopy.forEach(
            alias -> {
               String registeredName = this.aliasMap.get(alias);
               if (registeredName != null) {
                  String resolvedAlias = valueResolver.resolveStringValue(alias);
                  String resolvedName = valueResolver.resolveStringValue(registeredName);
                  if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
                     this.aliasMap.remove(alias);
                     this.aliasNames.remove(alias);
                  } else if (!resolvedAlias.equals(alias)) {
                     String existingName = this.aliasMap.get(resolvedAlias);
                     if (existingName != null) {
                        if (existingName.equals(resolvedName)) {
                           this.aliasMap.remove(alias);
                           this.aliasNames.remove(alias);
                           return;
                        }

                        throw new IllegalStateException(
                           "Cannot register resolved alias '"
                              + resolvedAlias
                              + "' (original: '"
                              + alias
                              + "') for name '"
                              + resolvedName
                              + "': It is already registered for name '"
                              + existingName
                              + "'."
                        );
                     }

                     this.checkForAliasCircle(resolvedName, resolvedAlias);
                     this.aliasMap.remove(alias);
                     this.aliasNames.remove(alias);
                     this.aliasMap.put(resolvedAlias, resolvedName);
                     this.aliasNames.add(resolvedAlias);
                  } else if (!registeredName.equals(resolvedName)) {
                     this.aliasMap.put(alias, resolvedName);
                     this.aliasNames.add(alias);
                  }
               }
            }
         );
      }
   }

   protected void checkForAliasCircle(String name, String alias) {
      if (this.hasAlias(alias, name)) {
         throw new IllegalStateException(
            "Cannot register alias '"
               + alias
               + "' for name '"
               + name
               + "': Circular reference - '"
               + name
               + "' is a direct or indirect alias for '"
               + alias
               + "' already"
         );
      }
   }

   public String canonicalName(String name) {
      String canonicalName = name;

      String resolvedName;
      do {
         resolvedName = this.aliasMap.get(canonicalName);
         if (resolvedName != null) {
            canonicalName = resolvedName;
         }
      } while (resolvedName != null);

      return canonicalName;
   }
}
