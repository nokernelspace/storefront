package org.springframework.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class SerializationUtils {
   @Contract("null -> null")
   public static byte @Nullable [] serialize(@Nullable Object object) {
      if (object == null) {
         return null;
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

      try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
         oos.writeObject(object);
         oos.flush();
      } catch (IOException ex) {
         throw new IllegalArgumentException("Failed to serialize object of type: " + object.getClass(), ex);
      }

      return baos.toByteArray();
   }

   @Deprecated(since = "6.0")
   @Contract("null -> null")
   public static @Nullable Object deserialize(byte @Nullable [] bytes) {
      if (bytes == null) {
         return null;
      }

      try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
         return ois.readObject();
      } catch (IOException ex) {
         throw new IllegalArgumentException("Failed to deserialize object", ex);
      } catch (ClassNotFoundException ex) {
         throw new IllegalStateException("Failed to deserialize object type", ex);
      }
   }

   public static <T extends Serializable> T clone(T object) {
      Object result = deserialize(serialize(object));
      Assert.state(result != null, "Deserialized object must not be null");
      return (T)result;
   }
}
