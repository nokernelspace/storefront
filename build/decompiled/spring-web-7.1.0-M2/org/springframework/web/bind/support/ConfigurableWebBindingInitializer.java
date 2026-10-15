package org.springframework.web.bind.support;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.core.convert.ConversionService;
import org.springframework.validation.BindingErrorProcessor;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;

public class ConfigurableWebBindingInitializer implements WebBindingInitializer {
   private boolean autoGrowNestedPaths = true;
   private boolean directFieldAccess = false;
   private @Nullable Boolean declarativeBinding;
   private @Nullable MessageCodesResolver messageCodesResolver;
   private @Nullable BindingErrorProcessor bindingErrorProcessor;
   private @Nullable Validator validator;
   private @Nullable ConversionService conversionService;
   private PropertyEditorRegistrar @Nullable [] propertyEditorRegistrars;

   public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
      this.autoGrowNestedPaths = autoGrowNestedPaths;
   }

   public boolean isAutoGrowNestedPaths() {
      return this.autoGrowNestedPaths;
   }

   public final void setDirectFieldAccess(boolean directFieldAccess) {
      this.directFieldAccess = directFieldAccess;
   }

   public boolean isDirectFieldAccess() {
      return this.directFieldAccess;
   }

   public void setDeclarativeBinding(boolean declarativeBinding) {
      this.declarativeBinding = declarativeBinding;
   }

   public boolean isDeclarativeBinding() {
      return this.declarativeBinding != null ? this.declarativeBinding : false;
   }

   public final void setMessageCodesResolver(@Nullable MessageCodesResolver messageCodesResolver) {
      this.messageCodesResolver = messageCodesResolver;
   }

   public final @Nullable MessageCodesResolver getMessageCodesResolver() {
      return this.messageCodesResolver;
   }

   public final void setBindingErrorProcessor(@Nullable BindingErrorProcessor bindingErrorProcessor) {
      this.bindingErrorProcessor = bindingErrorProcessor;
   }

   public final @Nullable BindingErrorProcessor getBindingErrorProcessor() {
      return this.bindingErrorProcessor;
   }

   public final void setValidator(@Nullable Validator validator) {
      this.validator = validator;
   }

   public final @Nullable Validator getValidator() {
      return this.validator;
   }

   public final void setConversionService(@Nullable ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   public final @Nullable ConversionService getConversionService() {
      return this.conversionService;
   }

   public final void setPropertyEditorRegistrar(PropertyEditorRegistrar propertyEditorRegistrar) {
      this.propertyEditorRegistrars = new PropertyEditorRegistrar[]{propertyEditorRegistrar};
   }

   public final void setPropertyEditorRegistrars(PropertyEditorRegistrar @Nullable [] propertyEditorRegistrars) {
      this.propertyEditorRegistrars = propertyEditorRegistrars;
   }

   public final PropertyEditorRegistrar @Nullable [] getPropertyEditorRegistrars() {
      return this.propertyEditorRegistrars;
   }

   @Override
   public void initBinder(WebDataBinder binder) {
      binder.setAutoGrowNestedPaths(this.autoGrowNestedPaths);
      if (this.directFieldAccess) {
         binder.initDirectFieldAccess();
      }

      if (this.declarativeBinding != null) {
         binder.setDeclarativeBinding(this.declarativeBinding);
      }

      if (this.messageCodesResolver != null) {
         binder.setMessageCodesResolver(this.messageCodesResolver);
      }

      if (this.bindingErrorProcessor != null) {
         binder.setBindingErrorProcessor(this.bindingErrorProcessor);
      }

      if (this.validator != null) {
         Class<?> type = getTargetType(binder);
         if (type != null && this.validator.supports(type)) {
            binder.setValidator(this.validator);
         }
      }

      if (this.conversionService != null) {
         binder.setConversionService(this.conversionService);
      }

      if (this.propertyEditorRegistrars != null) {
         for (PropertyEditorRegistrar propertyEditorRegistrar : this.propertyEditorRegistrars) {
            propertyEditorRegistrar.registerCustomEditors(binder);
         }
      }
   }

   private static @Nullable Class<?> getTargetType(WebDataBinder binder) {
      Class<?> type = null;
      if (binder.getTarget() != null) {
         type = binder.getTarget().getClass();
      } else if (binder.getTargetType() != null) {
         type = binder.getTargetType().resolve();
      }

      return type;
   }
}
