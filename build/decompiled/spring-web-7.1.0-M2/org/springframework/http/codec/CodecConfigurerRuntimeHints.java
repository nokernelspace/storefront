package org.springframework.http.codec;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.http.codec.support.DefaultClientCodecConfigurer;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;

class CodecConfigurerRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints.resources().registerPattern("org/springframework/http/codec/CodecConfigurer.properties");
      hints.reflection()
         .registerTypes(
            TypeReference.listOf(new Class[]{DefaultClientCodecConfigurer.class, DefaultServerCodecConfigurer.class}),
            typeHint -> typeHint.onReachableType(CodecConfigurerFactory.class).withMembers(new MemberCategory[]{MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS})
         );
   }
}
