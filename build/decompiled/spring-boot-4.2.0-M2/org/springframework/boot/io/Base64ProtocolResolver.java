package org.springframework.boot.io;

import java.util.Base64;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class Base64ProtocolResolver implements ProtocolResolver {
   private static final String BASE64_PREFIX = "base64:";

   public @Nullable Resource resolve(String location, ResourceLoader resourceLoader) {
      if (location.startsWith("base64:")) {
         String value = location.substring("base64:".length());
         return new ByteArrayResource(decode(value));
      } else {
         return null;
      }
   }

   private static byte[] decode(String location) {
      return Base64.getDecoder().decode(location);
   }
}
