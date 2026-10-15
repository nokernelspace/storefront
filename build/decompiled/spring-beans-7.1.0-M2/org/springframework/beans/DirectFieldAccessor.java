package org.springframework.beans;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.ReflectionUtils;

public class DirectFieldAccessor extends AbstractNestablePropertyAccessor {
   private final Map<String, DirectFieldAccessor.FieldPropertyHandler> fieldMap = new HashMap<>();

   public DirectFieldAccessor(Object object) {
      super(object);
   }

   protected DirectFieldAccessor(Object object, String nestedPath, DirectFieldAccessor parent) {
      super(object, nestedPath, parent);
   }

   @Override
   protected AbstractNestablePropertyAccessor.@Nullable PropertyHandler getLocalPropertyHandler(String propertyName) {
      DirectFieldAccessor.FieldPropertyHandler propertyHandler = this.fieldMap.get(propertyName);
      if (propertyHandler == null) {
         Field field = ReflectionUtils.findField(this.getWrappedClass(), propertyName);
         if (field != null) {
            propertyHandler = new DirectFieldAccessor.FieldPropertyHandler(field);
            this.fieldMap.put(propertyName, propertyHandler);
         }
      }

      return propertyHandler;
   }

   protected DirectFieldAccessor newNestedPropertyAccessor(Object object, String nestedPath) {
      return new DirectFieldAccessor(object, nestedPath, this);
   }

   @Override
   protected NotWritablePropertyException createNotWritablePropertyException(String propertyName) {
      PropertyMatches matches = PropertyMatches.forField(propertyName, this.getRootClass());
      throw new NotWritablePropertyException(
         this.getRootClass(), this.getNestedPath() + propertyName, matches.buildErrorMessage(), matches.getPossibleMatches()
      );
   }

   private class FieldPropertyHandler extends AbstractNestablePropertyAccessor.PropertyHandler {
      private final Field field;
      private final ResolvableType resolvableType;

      public FieldPropertyHandler(Field field) {
         super(field.getType(), true, true);
         this.field = field;
         this.resolvableType = ResolvableType.forField(this.field);
      }

      @Override
      public TypeDescriptor toTypeDescriptor() {
         return new TypeDescriptor(this.resolvableType, this.field.getType(), this.field.getAnnotations());
      }

      @Override
      public ResolvableType getResolvableType() {
         return this.resolvableType;
      }

      @Override
      public TypeDescriptor getMapValueType(int nestingLevel) {
         return new TypeDescriptor(this.resolvableType.getNested(nestingLevel).asMap().getGeneric(new int[]{1}), null, this.field.getAnnotations());
      }

      @Override
      public TypeDescriptor getCollectionType(int nestingLevel) {
         return new TypeDescriptor(this.resolvableType.getNested(nestingLevel).asCollection().getGeneric(new int[0]), null, this.field.getAnnotations());
      }

      @Override
      public @Nullable TypeDescriptor nested(int level) {
         return TypeDescriptor.nested(this.field, level);
      }

      @Override
      public @Nullable Object getValue() throws Exception {
         try {
            ReflectionUtils.makeAccessible(this.field);
            return this.field.get(DirectFieldAccessor.this.getWrappedInstance());
         } catch (IllegalAccessException | InaccessibleObjectException ex) {
            throw new InvalidPropertyException(DirectFieldAccessor.this.getWrappedClass(), this.field.getName(), "Field is not accessible", ex);
         }
      }

      @Override
      public void setValue(@Nullable Object value) throws Exception {
         try {
            ReflectionUtils.makeAccessible(this.field);
            this.field.set(DirectFieldAccessor.this.getWrappedInstance(), value);
         } catch (IllegalAccessException | InaccessibleObjectException ex) {
            throw new InvalidPropertyException(DirectFieldAccessor.this.getWrappedClass(), this.field.getName(), "Field is not accessible", ex);
         }
      }
   }
}
