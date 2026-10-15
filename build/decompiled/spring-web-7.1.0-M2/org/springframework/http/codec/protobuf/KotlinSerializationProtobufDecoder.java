package org.springframework.http.codec.protobuf;

import java.util.function.Predicate;
import kotlinx.serialization.protobuf.ProtoBuf;
import org.springframework.core.ResolvableType;
import org.springframework.http.codec.KotlinSerializationBinaryDecoder;

public class KotlinSerializationProtobufDecoder extends KotlinSerializationBinaryDecoder<ProtoBuf> {
   public KotlinSerializationProtobufDecoder() {
      this(ProtoBuf.Default);
   }

   public KotlinSerializationProtobufDecoder(Predicate<ResolvableType> typePredicate) {
      this(ProtoBuf.Default, typePredicate);
   }

   public KotlinSerializationProtobufDecoder(ProtoBuf protobuf) {
      super(protobuf, ProtobufCodecSupport.MIME_TYPES);
   }

   public KotlinSerializationProtobufDecoder(ProtoBuf protobuf, Predicate<ResolvableType> typePredicate) {
      super(protobuf, typePredicate, ProtobufCodecSupport.MIME_TYPES);
   }
}
