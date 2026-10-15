package org.springframework.http.codec.cbor;

import java.util.function.Predicate;
import kotlinx.serialization.cbor.Cbor;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.KotlinSerializationBinaryEncoder;

public class KotlinSerializationCborEncoder extends KotlinSerializationBinaryEncoder<Cbor> {
   public KotlinSerializationCborEncoder() {
      this(Cbor.Default);
   }

   public KotlinSerializationCborEncoder(Predicate<ResolvableType> typePredicate) {
      this(Cbor.Default, typePredicate);
   }

   public KotlinSerializationCborEncoder(Cbor cbor) {
      super(cbor, MediaType.APPLICATION_CBOR);
   }

   public KotlinSerializationCborEncoder(Cbor cbor, Predicate<ResolvableType> typePredicate) {
      super(cbor, typePredicate, MediaType.APPLICATION_CBOR);
   }
}
