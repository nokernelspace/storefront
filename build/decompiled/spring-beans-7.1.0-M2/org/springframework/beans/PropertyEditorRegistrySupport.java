package org.springframework.beans;

import java.beans.PropertyEditor;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.propertyeditors.ByteArrayPropertyEditor;
import org.springframework.beans.propertyeditors.CharArrayPropertyEditor;
import org.springframework.beans.propertyeditors.CharacterEditor;
import org.springframework.beans.propertyeditors.CharsetEditor;
import org.springframework.beans.propertyeditors.ClassArrayEditor;
import org.springframework.beans.propertyeditors.ClassEditor;
import org.springframework.beans.propertyeditors.CurrencyEditor;
import org.springframework.beans.propertyeditors.CustomBooleanEditor;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;
import org.springframework.beans.propertyeditors.CustomMapEditor;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.beans.propertyeditors.FileEditor;
import org.springframework.beans.propertyeditors.InputSourceEditor;
import org.springframework.beans.propertyeditors.InputStreamEditor;
import org.springframework.beans.propertyeditors.LocaleEditor;
import org.springframework.beans.propertyeditors.PathEditor;
import org.springframework.beans.propertyeditors.PatternEditor;
import org.springframework.beans.propertyeditors.PropertiesEditor;
import org.springframework.beans.propertyeditors.ReaderEditor;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.beans.propertyeditors.TimeZoneEditor;
import org.springframework.beans.propertyeditors.URIEditor;
import org.springframework.beans.propertyeditors.URLEditor;
import org.springframework.beans.propertyeditors.UUIDEditor;
import org.springframework.beans.propertyeditors.ZoneIdEditor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceArrayPropertyEditor;
import org.springframework.util.ClassUtils;
import org.xml.sax.InputSource;

public class PropertyEditorRegistrySupport implements PropertyEditorRegistry {
   private static final int MAX_STRIPPED_PROPERTY_PATH_DEPTH = 8;
   private @Nullable ConversionService conversionService;
   private boolean defaultEditorsActive = false;
   private boolean configValueEditorsActive = false;
   private @Nullable PropertyEditorRegistrar defaultEditorRegistrar;
   private @Nullable Map<Class<?>, PropertyEditor> defaultEditors;
   private @Nullable Map<Class<?>, PropertyEditor> overriddenDefaultEditors;
   private @Nullable Map<Class<?>, PropertyEditor> customEditors;
   private @Nullable Map<String, PropertyEditorRegistrySupport.CustomEditorHolder> customEditorsForPath;
   private @Nullable Map<Class<?>, PropertyEditor> customEditorCache;

   public void setConversionService(@Nullable ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   public @Nullable ConversionService getConversionService() {
      return this.conversionService;
   }

   protected void registerDefaultEditors() {
      this.defaultEditorsActive = true;
   }

   public void useConfigValueEditors() {
      this.configValueEditorsActive = true;
   }

   public void setDefaultEditorRegistrar(PropertyEditorRegistrar registrar) {
      this.defaultEditorRegistrar = registrar;
   }

   public void overrideDefaultEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
      if (this.overriddenDefaultEditors == null) {
         this.overriddenDefaultEditors = new HashMap<>();
      }

      this.overriddenDefaultEditors.put(requiredType, propertyEditor);
   }

   public @Nullable PropertyEditor getDefaultEditor(Class<?> requiredType) {
      if (!this.defaultEditorsActive) {
         return null;
      }

      if (this.overriddenDefaultEditors == null && this.defaultEditorRegistrar != null) {
         this.defaultEditorRegistrar.registerCustomEditors(this);
      }

      if (this.overriddenDefaultEditors != null) {
         PropertyEditor editor = this.overriddenDefaultEditors.get(requiredType);
         if (editor != null) {
            return editor;
         }
      }

      if (this.defaultEditors == null) {
         this.defaultEditors = this.createDefaultEditors();
      }

      return this.defaultEditors.get(requiredType);
   }

   private Map<Class<?>, PropertyEditor> createDefaultEditors() {
      Map<Class<?>, PropertyEditor> defaultEditors = new HashMap<>(64);
      defaultEditors.put(Charset.class, new CharsetEditor());
      defaultEditors.put(Class.class, new ClassEditor());
      defaultEditors.put(Class[].class, new ClassArrayEditor());
      defaultEditors.put(Currency.class, new CurrencyEditor());
      defaultEditors.put(File.class, new FileEditor());
      defaultEditors.put(InputStream.class, new InputStreamEditor());
      defaultEditors.put(InputSource.class, new InputSourceEditor());
      defaultEditors.put(Locale.class, new LocaleEditor());
      defaultEditors.put(Path.class, new PathEditor());
      defaultEditors.put(Pattern.class, new PatternEditor());
      defaultEditors.put(Properties.class, new PropertiesEditor());
      defaultEditors.put(Reader.class, new ReaderEditor());
      defaultEditors.put(Resource[].class, new ResourceArrayPropertyEditor());
      defaultEditors.put(TimeZone.class, new TimeZoneEditor());
      defaultEditors.put(URI.class, new URIEditor());
      defaultEditors.put(URL.class, new URLEditor());
      defaultEditors.put(UUID.class, new UUIDEditor());
      defaultEditors.put(ZoneId.class, new ZoneIdEditor());
      defaultEditors.put(Collection.class, new CustomCollectionEditor(Collection.class));
      defaultEditors.put(Set.class, new CustomCollectionEditor(Set.class));
      defaultEditors.put(SortedSet.class, new CustomCollectionEditor(SortedSet.class));
      defaultEditors.put(List.class, new CustomCollectionEditor(List.class));
      defaultEditors.put(SortedMap.class, new CustomMapEditor(SortedMap.class));
      defaultEditors.put(byte[].class, new ByteArrayPropertyEditor());
      defaultEditors.put(char[].class, new CharArrayPropertyEditor());
      defaultEditors.put(char.class, new CharacterEditor(false));
      defaultEditors.put(Character.class, new CharacterEditor(true));
      defaultEditors.put(boolean.class, new CustomBooleanEditor(false));
      defaultEditors.put(Boolean.class, new CustomBooleanEditor(true));
      defaultEditors.put(byte.class, new CustomNumberEditor(Byte.class, false));
      defaultEditors.put(Byte.class, new CustomNumberEditor(Byte.class, true));
      defaultEditors.put(short.class, new CustomNumberEditor(Short.class, false));
      defaultEditors.put(Short.class, new CustomNumberEditor(Short.class, true));
      defaultEditors.put(int.class, new CustomNumberEditor(Integer.class, false));
      defaultEditors.put(Integer.class, new CustomNumberEditor(Integer.class, true));
      defaultEditors.put(long.class, new CustomNumberEditor(Long.class, false));
      defaultEditors.put(Long.class, new CustomNumberEditor(Long.class, true));
      defaultEditors.put(float.class, new CustomNumberEditor(Float.class, false));
      defaultEditors.put(Float.class, new CustomNumberEditor(Float.class, true));
      defaultEditors.put(double.class, new CustomNumberEditor(Double.class, false));
      defaultEditors.put(Double.class, new CustomNumberEditor(Double.class, true));
      defaultEditors.put(BigDecimal.class, new CustomNumberEditor(BigDecimal.class, true));
      defaultEditors.put(BigInteger.class, new CustomNumberEditor(BigInteger.class, true));
      if (this.configValueEditorsActive) {
         StringArrayPropertyEditor sae = new StringArrayPropertyEditor();
         defaultEditors.put(String[].class, sae);
         defaultEditors.put(short[].class, sae);
         defaultEditors.put(int[].class, sae);
         defaultEditors.put(long[].class, sae);
      }

      return defaultEditors;
   }

   protected void copyDefaultEditorsTo(PropertyEditorRegistrySupport target) {
      target.defaultEditorsActive = this.defaultEditorsActive;
      target.configValueEditorsActive = this.configValueEditorsActive;
      target.defaultEditors = this.defaultEditors;
      target.overriddenDefaultEditors = this.overriddenDefaultEditors;
   }

   @Override
   public void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
      this.registerCustomEditor(requiredType, null, propertyEditor);
   }

   @Override
   public void registerCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath, PropertyEditor propertyEditor) {
      if (requiredType == null && propertyPath == null) {
         throw new IllegalArgumentException("Either requiredType or propertyPath is required");
      }

      if (propertyPath != null) {
         if (this.customEditorsForPath == null) {
            this.customEditorsForPath = new LinkedHashMap<>(16);
         }

         this.customEditorsForPath.put(propertyPath, new PropertyEditorRegistrySupport.CustomEditorHolder(propertyEditor, requiredType));
      } else {
         if (this.customEditors == null) {
            this.customEditors = new LinkedHashMap<>(16);
         }

         this.customEditors.put(requiredType, propertyEditor);
         this.customEditorCache = null;
      }
   }

   @Override
   public @Nullable PropertyEditor findCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath) {
      Class<?> requiredTypeToUse = requiredType;
      if (propertyPath != null) {
         if (this.customEditorsForPath != null) {
            PropertyEditor editor = this.getCustomEditor(propertyPath, requiredType);
            if (editor == null) {
               List<String> strippedPaths = new ArrayList<>();
               this.addStrippedPropertyPaths(strippedPaths, "", propertyPath);
               Iterator<String> it = strippedPaths.iterator();

               while (it.hasNext() && editor == null) {
                  String strippedPath = it.next();
                  editor = this.getCustomEditor(strippedPath, requiredType);
               }
            }

            if (editor != null) {
               return editor;
            }
         }

         if (requiredType == null) {
            requiredTypeToUse = this.getPropertyType(propertyPath);
         }
      }

      return this.getCustomEditor(requiredTypeToUse);
   }

   public boolean hasCustomEditorForElement(@Nullable Class<?> elementType, @Nullable String propertyPath) {
      if (propertyPath != null && this.customEditorsForPath != null) {
         for (Entry<String, PropertyEditorRegistrySupport.CustomEditorHolder> entry : this.customEditorsForPath.entrySet()) {
            if (matchesProperty(entry.getKey(), propertyPath) && entry.getValue().getPropertyEditor(elementType) != null) {
               return true;
            }
         }
      }

      return elementType != null && this.customEditors != null && this.customEditors.containsKey(elementType);
   }

   private static boolean matchesProperty(String registeredPath, String propertyPath) {
      String canonicalRegisteredPath = PropertyPath.canonicalNameOrOriginal(registeredPath);
      String canonicalPropertyPath = PropertyPath.canonicalNameOrOriginal(propertyPath);
      if (!canonicalRegisteredPath.startsWith(canonicalPropertyPath)) {
         return false;
      } else if (canonicalRegisteredPath.length() == canonicalPropertyPath.length()) {
         return true;
      } else {
         return canonicalRegisteredPath.charAt(canonicalPropertyPath.length()) != '['
            ? false
            : canonicalRegisteredPath.indexOf(93, canonicalPropertyPath.length() + 1) == canonicalRegisteredPath.length() - 1;
      }
   }

   protected @Nullable Class<?> getPropertyType(String propertyPath) {
      return null;
   }

   private @Nullable PropertyEditor getCustomEditor(String propertyName, @Nullable Class<?> requiredType) {
      PropertyEditorRegistrySupport.CustomEditorHolder holder = this.customEditorsForPath != null ? this.customEditorsForPath.get(propertyName) : null;
      return holder != null ? holder.getPropertyEditor(requiredType) : null;
   }

   private @Nullable PropertyEditor getCustomEditor(@Nullable Class<?> requiredType) {
      if (requiredType != null && this.customEditors != null) {
         PropertyEditor editor = this.customEditors.get(requiredType);
         if (editor == null) {
            if (this.customEditorCache != null) {
               editor = this.customEditorCache.get(requiredType);
            }

            if (editor == null) {
               for (Entry<Class<?>, PropertyEditor> entry : this.customEditors.entrySet()) {
                  Class<?> key = entry.getKey();
                  if (key.isAssignableFrom(requiredType)) {
                     editor = entry.getValue();
                     if (this.customEditorCache == null) {
                        this.customEditorCache = new HashMap<>();
                     }

                     this.customEditorCache.put(requiredType, editor);
                     if (editor != null) {
                        break;
                     }
                  }
               }
            }
         }

         return editor;
      } else {
         return null;
      }
   }

   protected @Nullable Class<?> guessPropertyTypeFromEditors(String propertyName) {
      if (this.customEditorsForPath != null) {
         PropertyEditorRegistrySupport.CustomEditorHolder editorHolder = this.customEditorsForPath.get(propertyName);
         if (editorHolder == null) {
            List<String> strippedPaths = new ArrayList<>();
            this.addStrippedPropertyPaths(strippedPaths, "", propertyName);
            Iterator<String> it = strippedPaths.iterator();

            while (it.hasNext() && editorHolder == null) {
               String strippedName = it.next();
               editorHolder = this.customEditorsForPath.get(strippedName);
            }
         }

         if (editorHolder != null) {
            return editorHolder.getRegisteredType();
         }
      }

      return null;
   }

   protected void copyCustomEditorsTo(PropertyEditorRegistry target, @Nullable String nestedProperty) {
      String actualPropertyName = nestedProperty != null ? actualPropertyNameOf(nestedProperty) : null;
      if (this.customEditors != null) {
         this.customEditors.forEach(target::registerCustomEditor);
      }

      if (this.customEditorsForPath != null) {
         this.customEditorsForPath.forEach((editorPath, editorHolder) -> {
            if (nestedProperty != null) {
               PropertyPath editorPropertyPath;
               try {
                  editorPropertyPath = PropertyPath.parse(editorPath);
               } catch (InvalidPropertyPathException ex) {
                  return;
               }

               List<PropertyPath.Segment> editorSegments = editorPropertyPath.segments();
               if (editorSegments.size() > 1) {
                  String editorNestedProperty = editorSegments.get(0).toCanonicalName();
                  String editorNestedPath = editorPropertyPath.subPath(1).canonicalName();
                  if (editorNestedProperty.equals(nestedProperty) || editorNestedProperty.equals(actualPropertyName)) {
                     target.registerCustomEditor(editorHolder.getRegisteredType(), editorNestedPath, editorHolder.getPropertyEditor());
                  }
               }
            } else {
               target.registerCustomEditor(editorHolder.getRegisteredType(), editorPath, editorHolder.getPropertyEditor());
            }
         });
      }
   }

   private static @Nullable String actualPropertyNameOf(String nestedProperty) {
      try {
         List<PropertyPath.Segment> segments = PropertyPath.parse(nestedProperty).segments();
         return segments.size() == 1 ? segments.get(0).name() : null;
      } catch (InvalidPropertyPathException ex) {
         return null;
      }
   }

   private void addStrippedPropertyPaths(List<String> strippedPaths, String nestedPath, String propertyPath) {
      this.addStrippedPropertyPaths(strippedPaths, nestedPath, propertyPath, 0);
   }

   private void addStrippedPropertyPaths(List<String> strippedPaths, String nestedPath, String propertyPath, int depth) {
      if (depth < 8) {
         int startIndex = propertyPath.indexOf(91);
         if (startIndex != -1) {
            int endIndex = propertyPath.indexOf(93);
            if (endIndex != -1) {
               String prefix = propertyPath.substring(0, startIndex);
               String key = propertyPath.substring(startIndex, endIndex + 1);
               String suffix = propertyPath.substring(endIndex + 1);
               strippedPaths.add(nestedPath + prefix + suffix);
               this.addStrippedPropertyPaths(strippedPaths, nestedPath + prefix, suffix, depth + 1);
               this.addStrippedPropertyPaths(strippedPaths, nestedPath + prefix + key, suffix, depth + 1);
            }
         }
      }
   }

   private static final class CustomEditorHolder {
      private final PropertyEditor propertyEditor;
      private final @Nullable Class<?> registeredType;

      private CustomEditorHolder(PropertyEditor propertyEditor, @Nullable Class<?> registeredType) {
         this.propertyEditor = propertyEditor;
         this.registeredType = registeredType;
      }

      private PropertyEditor getPropertyEditor() {
         return this.propertyEditor;
      }

      private @Nullable Class<?> getRegisteredType() {
         return this.registeredType;
      }

      private @Nullable PropertyEditor getPropertyEditor(@Nullable Class<?> requiredType) {
         return this.registeredType != null
               && (
                  requiredType == null
                     || !ClassUtils.isAssignable(this.registeredType, requiredType) && !ClassUtils.isAssignable(requiredType, this.registeredType)
               )
               && (requiredType != null || Collection.class.isAssignableFrom(this.registeredType) || this.registeredType.isArray())
            ? null
            : this.propertyEditor;
      }
   }
}
