package org.springframework.http.converter.protobuf;

import java.util.function.Predicate;
import kotlinx.serialization.protobuf.ProtoBuf;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.KotlinSerializationBinaryHttpMessageConverter;

public class KotlinSerializationProtobufHttpMessageConverter extends KotlinSerializationBinaryHttpMessageConverter<ProtoBuf> {
   public KotlinSerializationProtobufHttpMessageConverter() {
      this(ProtoBuf.Default);
   }

   public KotlinSerializationProtobufHttpMessageConverter(Predicate<ResolvableType> typePredicate) {
      this(ProtoBuf.Default, typePredicate);
   }

   public KotlinSerializationProtobufHttpMessageConverter(ProtoBuf protobuf) {
      super(protobuf, MediaType.APPLICATION_PROTOBUF, MediaType.APPLICATION_OCTET_STREAM, new MediaType("application", "vnd.google.protobuf"));
   }

   public KotlinSerializationProtobufHttpMessageConverter(ProtoBuf protobuf, Predicate<ResolvableType> typePredicate) {
      super(protobuf, typePredicate, MediaType.APPLICATION_PROTOBUF, MediaType.APPLICATION_OCTET_STREAM, new MediaType("application", "vnd.google.protobuf"));
   }
}
