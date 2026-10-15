package org.springframework.http.codec.protobuf;

import java.util.function.Predicate;
import kotlinx.serialization.protobuf.ProtoBuf;
import org.springframework.core.ResolvableType;
import org.springframework.http.codec.KotlinSerializationBinaryEncoder;

public class KotlinSerializationProtobufEncoder extends KotlinSerializationBinaryEncoder<ProtoBuf> {
   public KotlinSerializationProtobufEncoder() {
      this(ProtoBuf.Default);
   }

   public KotlinSerializationProtobufEncoder(Predicate<ResolvableType> typePredicate) {
      this(ProtoBuf.Default, typePredicate);
   }

   public KotlinSerializationProtobufEncoder(ProtoBuf protobuf) {
      super(protobuf, ProtobufCodecSupport.MIME_TYPES);
   }

   public KotlinSerializationProtobufEncoder(ProtoBuf protobuf, Predicate<ResolvableType> typePredicate) {
      super(protobuf, typePredicate, ProtobufCodecSupport.MIME_TYPES);
   }
}
