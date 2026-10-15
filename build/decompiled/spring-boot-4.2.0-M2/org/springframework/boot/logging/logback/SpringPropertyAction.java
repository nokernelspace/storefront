package org.springframework.boot.logging.logback;

import ch.qos.logback.core.joran.action.BaseModelAction;
import ch.qos.logback.core.joran.spi.SaxEventInterpretationContext;
import ch.qos.logback.core.model.Model;
import org.xml.sax.Attributes;

class SpringPropertyAction extends BaseModelAction {
   private static final String SOURCE_ATTRIBUTE = "source";
   private static final String DEFAULT_VALUE_ATTRIBUTE = "defaultValue";

   protected Model buildCurrentModel(SaxEventInterpretationContext interpretationContext, String name, Attributes attributes) {
      SpringPropertyModel model = new SpringPropertyModel();
      model.setName(attributes.getValue("name"));
      model.setSource(attributes.getValue("source"));
      model.setScope(attributes.getValue("scope"));
      model.setDefaultValue(attributes.getValue("defaultValue"));
      return model;
   }
}
