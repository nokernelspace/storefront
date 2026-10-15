package org.springframework.http.converter.xml;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.util.Assert;

public class MarshallingHttpMessageConverter extends AbstractXmlHttpMessageConverter<Object> {
   private @Nullable Marshaller marshaller;
   private @Nullable Unmarshaller unmarshaller;

   public MarshallingHttpMessageConverter() {
   }

   public MarshallingHttpMessageConverter(Marshaller marshaller) {
      Assert.notNull(marshaller, "Marshaller must not be null");
      this.marshaller = marshaller;
      if (marshaller instanceof Unmarshaller _unmarshaller) {
         this.unmarshaller = _unmarshaller;
      }
   }

   public MarshallingHttpMessageConverter(Marshaller marshaller, Unmarshaller unmarshaller) {
      Assert.notNull(marshaller, "Marshaller must not be null");
      Assert.notNull(unmarshaller, "Unmarshaller must not be null");
      this.marshaller = marshaller;
      this.unmarshaller = unmarshaller;
   }

   public void setMarshaller(Marshaller marshaller) {
      this.marshaller = marshaller;
   }

   public void setUnmarshaller(Unmarshaller unmarshaller) {
      this.unmarshaller = unmarshaller;
   }

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canRead(mediaType) && this.unmarshaller != null && this.unmarshaller.supports(clazz);
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canWrite(mediaType) && this.marshaller != null && this.marshaller.supports(clazz);
   }

   @Override
   public boolean canWriteRepeatedly(Object o, @Nullable MediaType contentType) {
      return true;
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected Object readFromSource(Class<?> clazz, HttpHeaders headers, Source source) throws Exception {
      Assert.state(this.unmarshaller != null, "Property 'unmarshaller' is required");
      Object result = this.unmarshaller.unmarshal(source);
      if (!clazz.isInstance(result)) {
         throw new TypeMismatchException(result, clazz);
      } else {
         return result;
      }
   }

   @Override
   protected void writeToResult(Object o, HttpHeaders headers, Result result) throws Exception {
      Assert.state(this.marshaller != null, "Property 'marshaller' is required");
      this.marshaller.marshal(o, result);
   }

   @Override
   protected boolean supportsRepeatableWrites(Object o) {
      return true;
   }
}
