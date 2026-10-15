package org.springframework.boot.env;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

public class PropertiesPropertySourceLoader implements PropertySourceLoader {
   private static final String XML_FILE_EXTENSION = ".xml";

   @Override
   public String[] getFileExtensions() {
      return new String[]{"properties", "xml"};
   }

   @Override
   public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
      return this.load(name, resource, null);
   }

   @Override
   public List<PropertySource<?>> load(String name, Resource resource, @Nullable Charset encoding) throws IOException {
      List<Map<String, ?>> properties = this.loadProperties(resource, encoding);
      if (properties.isEmpty()) {
         return Collections.emptyList();
      }

      List<PropertySource<?>> propertySources = new ArrayList<>(properties.size());

      for (int i = 0; i < properties.size(); i++) {
         String documentNumber = properties.size() != 1 ? " (document #" + i + ")" : "";
         propertySources.add(new OriginTrackedMapPropertySource(name + documentNumber, Collections.unmodifiableMap(properties.get(i)), true));
      }

      return propertySources;
   }

   private List<Map<String, ?>> loadProperties(Resource resource, @Nullable Charset encoding) throws IOException {
      String filename = resource.getFilename();
      List<Map<String, ?>> result = new ArrayList<>();
      if (filename == null || !filename.endsWith(".xml")) {
         List<OriginTrackedPropertiesLoader.Document> documents = new OriginTrackedPropertiesLoader(resource).load(encoding);
         documents.forEach(document -> result.add(document.asMap()));
      } else if (encoding == null) {
         result.add(PropertiesLoaderUtils.loadProperties(resource));
      } else {
         result.add(PropertiesLoaderUtils.loadProperties(new EncodedResource(resource, encoding)));
      }

      return result;
   }
}
