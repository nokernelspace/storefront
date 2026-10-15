package org.springframework.http.converter.cbor;

import java.util.function.Predicate;
import kotlinx.serialization.cbor.Cbor;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.KotlinSerializationBinaryHttpMessageConverter;

public class KotlinSerializationCborHttpMessageConverter extends KotlinSerializationBinaryHttpMessageConverter<Cbor> {
   public KotlinSerializationCborHttpMessageConverter() {
      this(Cbor.Default);
   }

   public KotlinSerializationCborHttpMessageConverter(Predicate<ResolvableType> typePredicate) {
      this(Cbor.Default, typePredicate);
   }

   public KotlinSerializationCborHttpMessageConverter(Cbor cbor) {
      super(cbor, MediaType.APPLICATION_CBOR);
   }

   public KotlinSerializationCborHttpMessageConverter(Cbor cbor, Predicate<ResolvableType> typePredicate) {
      super(cbor, typePredicate, MediaType.APPLICATION_CBOR);
   }
}
