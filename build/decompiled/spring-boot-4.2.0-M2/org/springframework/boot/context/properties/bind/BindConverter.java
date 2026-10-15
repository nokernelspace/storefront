package org.springframework.boot.context.properties.bind;

import java.beans.PropertyEditor;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.propertyeditors.CustomBooleanEditor;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.beans.propertyeditors.FileEditor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

final class BindConverter {
   private static @Nullable BindConverter sharedInstance;
   private final List<ConversionService> delegates;

   private BindConverter(@Nullable List<ConversionService> conversionServices, @Nullable Consumer<PropertyEditorRegistry> propertyEditorInitializer) {
      List<ConversionService> delegates = new ArrayList<>();
      delegates.add(new BindConverter.TypeConverterConversionService(propertyEditorInitializer));
      boolean hasApplication = false;
      if (!CollectionUtils.isEmpty(conversionServices)) {
         for (ConversionService conversionService : conversionServices) {
            delegates.add(conversionService);
            hasApplication = hasApplication || conversionService instanceof ApplicationConversionService;
         }
      }

      if (!hasApplication) {
         delegates.add(ApplicationConversionService.getSharedInstance());
      }

      this.delegates = Collections.unmodifiableList(delegates);
   }

   boolean canConvert(@Nullable Object source, ResolvableType targetType, Annotation... targetAnnotations) {
      return this.canConvert(TypeDescriptor.forObject(source), new BindConverter.ResolvableTypeDescriptor(targetType, targetAnnotations));
   }

   private boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
      for (ConversionService service : this.delegates) {
         if (service.canConvert(sourceType, targetType)) {
            return true;
         }
      }

      return false;
   }

   <T> @Nullable T convert(@Nullable Object source, Bindable<T> target) {
      return this.convert(source, target.getType(), target.getAnnotations());
   }

   <T> @Nullable T convert(@Nullable Object source, ResolvableType targetType, Annotation... targetAnnotations) {
      TypeDescriptor sourceType = source != null ? TypeDescriptor.forObject(source) : null;
      return (T)this.convert(source, sourceType, new BindConverter.ResolvableTypeDescriptor(targetType, targetAnnotations));
   }

   private @Nullable Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
      ConversionException failure = null;

      for (ConversionService delegate : this.delegates) {
         try {
            if (delegate.canConvert(sourceType, targetType)) {
               return delegate.convert(source, sourceType, targetType);
            }
         } catch (ConversionException ex) {
            if (failure == null && ex instanceof ConversionFailedException) {
               failure = ex;
            }
         }
      }

      if (source == null) {
         return null;
      } else {
         throw failure != null ? failure : new ConverterNotFoundException(sourceType, targetType);
      }
   }

   static BindConverter get(@Nullable List<ConversionService> conversionServices, @Nullable Consumer<PropertyEditorRegistry> propertyEditorInitializer) {
      boolean sharedApplicationConversionService = conversionServices == null
         || conversionServices.size() == 1 && conversionServices.get(0) == ApplicationConversionService.getSharedInstance();
      return propertyEditorInitializer == null && sharedApplicationConversionService
         ? getSharedInstance()
         : new BindConverter(conversionServices, propertyEditorInitializer);
   }

   private static BindConverter getSharedInstance() {
      if (sharedInstance == null) {
         sharedInstance = new BindConverter(null, null);
      }

      return sharedInstance;
   }

   private static class ResolvableTypeDescriptor extends TypeDescriptor {
      ResolvableTypeDescriptor(ResolvableType resolvableType, Annotation[] annotations) {
         super(resolvableType, null, annotations);
      }
   }

   private static class TypeConverterConversionService extends GenericConversionService {
      TypeConverterConversionService(@Nullable Consumer<PropertyEditorRegistry> initializer) {
         ApplicationConversionService.addDelimitedStringConverters(this);
         this.addConverter(new BindConverter.TypeConverterConverter(initializer));
      }

      public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
         return isPrimitiveArray(targetType) ? false : super.canConvert(sourceType, targetType);
      }

      private static boolean isPrimitiveArray(TypeDescriptor targetType) {
         if (!targetType.isArray()) {
            return false;
         }

         TypeDescriptor elementTypeDescriptor = targetType.getElementTypeDescriptor();
         Assert.state(elementTypeDescriptor != null, "'elementTypeDescriptor' must not be null");
         return elementTypeDescriptor.isPrimitive();
      }
   }

   private static class TypeConverterConverter implements ConditionalGenericConverter {
      private static final Set<Class<?>> EXCLUDED_EDITORS;
      private final @Nullable Consumer<PropertyEditorRegistry> initializer;
      private final SimpleTypeConverter matchesOnlyTypeConverter;

      TypeConverterConverter(@Nullable Consumer<PropertyEditorRegistry> initializer) {
         this.initializer = initializer;
         this.matchesOnlyTypeConverter = this.createTypeConverter();
      }

      public Set<ConvertiblePair> getConvertibleTypes() {
         return Set.of(
            new ConvertiblePair(String.class, Object.class),
            new ConvertiblePair(String.class, Resource[].class),
            new ConvertiblePair(String.class, Collection.class)
         );
      }

      public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
         Class<?> type = targetType.getType();
         if (type != null && type != Object.class && !Map.class.isAssignableFrom(type)) {
            if (Collection.class.isAssignableFrom(type)) {
               TypeDescriptor elementType = targetType.getElementTypeDescriptor();
               if (elementType == null || !Resource.class.isAssignableFrom(elementType.getType())) {
                  return false;
               }
            }

            PropertyEditor editor = this.matchesOnlyTypeConverter.getDefaultEditor(type);
            if (editor == null) {
               editor = this.matchesOnlyTypeConverter.findCustomEditor(type, null);
            }

            if (editor == null && String.class != type) {
               editor = BeanUtils.findEditorByConvention(type);
            }

            return editor != null && !EXCLUDED_EDITORS.contains(editor.getClass());
         } else {
            return false;
         }
      }

      public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
         return this.createTypeConverter().convertIfNecessary(source, targetType.getType(), targetType);
      }

      private SimpleTypeConverter createTypeConverter() {
         SimpleTypeConverter typeConverter = new SimpleTypeConverter();
         if (this.initializer != null) {
            this.initializer.accept(typeConverter);
         }

         return typeConverter;
      }

      static {
         Set<Class<?>> excluded = new HashSet<>();
         excluded.add(CustomNumberEditor.class);
         excluded.add(CustomBooleanEditor.class);
         excluded.add(FileEditor.class);
         EXCLUDED_EDITORS = Collections.unmodifiableSet(excluded);
      }
   }
}
