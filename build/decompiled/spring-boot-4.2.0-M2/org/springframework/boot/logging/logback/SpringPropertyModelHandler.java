package org.springframework.boot.logging.logback;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.action.ActionUtil;
import ch.qos.logback.core.joran.action.ActionUtil.Scope;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.processor.ModelHandlerBase;
import ch.qos.logback.core.model.processor.ModelHandlerException;
import ch.qos.logback.core.model.processor.ModelInterpretationContext;
import ch.qos.logback.core.model.util.PropertyModelHandlerHelper;
import ch.qos.logback.core.util.OptionHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;

class SpringPropertyModelHandler extends ModelHandlerBase {
   private final @Nullable Environment environment;

   SpringPropertyModelHandler(Context context, @Nullable Environment environment) {
      super(context);
      this.environment = environment;
   }

   public void handle(ModelInterpretationContext intercon, Model model) throws ModelHandlerException {
      SpringPropertyModel propertyModel = (SpringPropertyModel)model;
      Scope scope = ActionUtil.stringToScope(propertyModel.getScope());
      String defaultValue = propertyModel.getDefaultValue();
      String source = propertyModel.getSource();
      if (OptionHelper.isNullOrEmpty(propertyModel.getName()) || OptionHelper.isNullOrEmpty(source)) {
         this.addError("The \"name\" and \"source\" attributes of <springProperty> must be set");
      }

      PropertyModelHandlerHelper.setProperty(intercon, propertyModel.getName(), this.getValue(source, defaultValue), scope);
   }

   private String getValue(String source, String defaultValue) {
      if (this.environment == null) {
         this.addWarn("No Spring Environment available to resolve " + source);
         return defaultValue;
      } else {
         return this.environment.getProperty(source, defaultValue);
      }
   }
}
