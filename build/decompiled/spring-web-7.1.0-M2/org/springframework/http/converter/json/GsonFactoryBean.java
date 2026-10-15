package org.springframework.http.converter.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

public class GsonFactoryBean implements FactoryBean<Gson>, InitializingBean {
   private boolean base64EncodeByteArrays = false;
   private boolean serializeNulls = false;
   private boolean prettyPrinting = false;
   private boolean disableHtmlEscaping = false;
   private @Nullable String dateFormatPattern;
   private @Nullable Gson gson;

   public void setBase64EncodeByteArrays(boolean base64EncodeByteArrays) {
      this.base64EncodeByteArrays = base64EncodeByteArrays;
   }

   public void setSerializeNulls(boolean serializeNulls) {
      this.serializeNulls = serializeNulls;
   }

   public void setPrettyPrinting(boolean prettyPrinting) {
      this.prettyPrinting = prettyPrinting;
   }

   public void setDisableHtmlEscaping(boolean disableHtmlEscaping) {
      this.disableHtmlEscaping = disableHtmlEscaping;
   }

   public void setDateFormatPattern(String dateFormatPattern) {
      this.dateFormatPattern = dateFormatPattern;
   }

   public void afterPropertiesSet() {
      GsonBuilder builder = this.base64EncodeByteArrays ? GsonBuilderUtils.gsonBuilderWithBase64EncodedByteArrays() : new GsonBuilder();
      if (this.serializeNulls) {
         builder.serializeNulls();
      }

      if (this.prettyPrinting) {
         builder.setPrettyPrinting();
      }

      if (this.disableHtmlEscaping) {
         builder.disableHtmlEscaping();
      }

      if (this.dateFormatPattern != null) {
         builder.setDateFormat(this.dateFormatPattern);
      }

      this.gson = builder.create();
   }

   public @Nullable Gson getObject() {
      return this.gson;
   }

   public Class<?> getObjectType() {
      return Gson.class;
   }

   public boolean isSingleton() {
      return true;
   }
}
