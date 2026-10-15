package org.springframework.http.codec.cbor;

import java.util.function.Predicate;
import kotlinx.serialization.cbor.Cbor;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.KotlinSerializationBinaryDecoder;

public class KotlinSerializationCborDecoder extends KotlinSerializationBinaryDecoder<Cbor> {
   public KotlinSerializationCborDecoder() {
      this(Cbor.Default);
   }

   public KotlinSerializationCborDecoder(Predicate<ResolvableType> typePredicate) {
      this(Cbor.Default, typePredicate);
   }

   public KotlinSerializationCborDecoder(Cbor cbor) {
      super(cbor, MediaType.APPLICATION_CBOR);
   }

   public KotlinSerializationCborDecoder(Cbor cbor, Predicate<ResolvableType> typePredicate) {
      super(cbor, typePredicate, MediaType.APPLICATION_CBOR);
   }
}
