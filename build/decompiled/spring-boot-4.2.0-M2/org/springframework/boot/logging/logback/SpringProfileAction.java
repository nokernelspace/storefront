package org.springframework.boot.logging.logback;

import ch.qos.logback.core.joran.action.BaseModelAction;
import ch.qos.logback.core.joran.spi.SaxEventInterpretationContext;
import ch.qos.logback.core.model.Model;
import org.xml.sax.Attributes;

class SpringProfileAction extends BaseModelAction {
   protected Model buildCurrentModel(SaxEventInterpretationContext interpretationContext, String name, Attributes attributes) {
      SpringProfileModel model = new SpringProfileModel();
      model.setName(attributes.getValue("name"));
      return model;
   }
}
