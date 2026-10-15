package org.springframework.core.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ConfigurableObjectInputStream;

public class DefaultDeserializer implements Deserializer<Object> {
   private final @Nullable ClassLoader classLoader;
   private final @Nullable ObjectInputFilter objectInputFilter;

   public DefaultDeserializer() {
      this.classLoader = null;
      this.objectInputFilter = null;
   }

   public DefaultDeserializer(@Nullable ClassLoader classLoader) {
      this.classLoader = classLoader;
      this.objectInputFilter = null;
   }

   public DefaultDeserializer(@Nullable ClassLoader classLoader, @Nullable ObjectInputFilter objectInputFilter) {
      this.classLoader = classLoader;
      this.objectInputFilter = objectInputFilter;
   }

   public final @Nullable ClassLoader getClassLoader() {
      return this.classLoader;
   }

   public final @Nullable ObjectInputFilter getObjectInputFilter() {
      return this.objectInputFilter;
   }

   @Override
   public Object deserialize(InputStream inputStream) throws IOException {
      ObjectInputStream objectInputStream = new ConfigurableObjectInputStream(inputStream, this.classLoader);
      if (this.objectInputFilter != null) {
         objectInputStream.setObjectInputFilter(this.objectInputFilter);
      }

      try {
         return objectInputStream.readObject();
      } catch (ClassNotFoundException ex) {
         throw new IOException("Failed to deserialize object type", ex);
      }
   }
}
