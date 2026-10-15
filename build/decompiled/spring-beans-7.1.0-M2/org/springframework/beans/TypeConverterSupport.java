package org.springframework.beans;

import java.lang.reflect.Field;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;

public abstract class TypeConverterSupport extends PropertyEditorRegistrySupport implements TypeConverter {
   @Nullable TypeConverterDelegate typeConverterDelegate;

   @Override
   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType) throws TypeMismatchException {
      return this.convertIfNecessary(null, value, requiredType, TypeDescriptor.valueOf(requiredType));
   }

   @Override
   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable MethodParameter methodParam) throws TypeMismatchException {
      return this.convertIfNecessary(
         methodParam != null ? methodParam.getParameterName() : null,
         value,
         requiredType,
         methodParam != null ? new TypeDescriptor(methodParam) : TypeDescriptor.valueOf(requiredType)
      );
   }

   @Override
   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable Field field) throws TypeMismatchException {
      return this.convertIfNecessary(
         field != null ? field.getName() : null, value, requiredType, field != null ? new TypeDescriptor(field) : TypeDescriptor.valueOf(requiredType)
      );
   }

   @Override
   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor) throws TypeMismatchException {
      return this.convertIfNecessary(null, value, requiredType, typeDescriptor);
   }

   private <T> @Nullable T convertIfNecessary(
      @Nullable String propertyName, @Nullable Object value, @Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor
   ) throws TypeMismatchException {
      Assert.state(this.typeConverterDelegate != null, "No TypeConverterDelegate");

      try {
         return this.typeConverterDelegate.convertIfNecessary(propertyName, null, value, requiredType, typeDescriptor);
      } catch (ConverterNotFoundException | IllegalStateException ex) {
         throw new ConversionNotSupportedException(value, requiredType, ex);
      } catch (ConversionException | IllegalArgumentException ex) {
         throw new TypeMismatchException(value, requiredType, ex);
      }
   }
}
