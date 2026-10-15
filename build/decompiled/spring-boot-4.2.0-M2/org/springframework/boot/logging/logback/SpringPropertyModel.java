package org.springframework.boot.logging.logback;

import ch.qos.logback.core.model.NamedModel;

class SpringPropertyModel extends NamedModel {
   private String scope;
   private String defaultValue;
   private String source;

   String getScope() {
      return this.scope;
   }

   void setScope(String scope) {
      this.scope = scope;
   }

   String getDefaultValue() {
      return this.defaultValue;
   }

   void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
   }

   String getSource() {
      return this.source;
   }

   void setSource(String source) {
      this.source = source;
   }
}
