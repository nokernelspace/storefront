package org.springframework.boot.env;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

public interface PropertySourceLoader {
   String[] getFileExtensions();

   List<PropertySource<?>> load(String name, Resource resource) throws IOException;

   default List<PropertySource<?>> load(String name, Resource resource, @Nullable Charset encoding) throws IOException {
      return this.load(name, resource);
   }
}
