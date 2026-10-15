package org.springframework.beans.support;

import java.beans.PropertyEditor;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.propertyeditors.ClassArrayEditor;
import org.springframework.beans.propertyeditors.ClassEditor;
import org.springframework.beans.propertyeditors.FileEditor;
import org.springframework.beans.propertyeditors.InputSourceEditor;
import org.springframework.beans.propertyeditors.InputStreamEditor;
import org.springframework.beans.propertyeditors.PathEditor;
import org.springframework.beans.propertyeditors.ReaderEditor;
import org.springframework.beans.propertyeditors.URIEditor;
import org.springframework.beans.propertyeditors.URLEditor;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.ContextResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.WritableResource;
import org.springframework.core.io.support.ResourceArrayPropertyEditor;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.xml.sax.InputSource;

public class ResourceEditorRegistrar implements PropertyEditorRegistrar {
   private final PropertyResolver propertyResolver;
   private final ResourceLoader resourceLoader;

   public ResourceEditorRegistrar(ResourceLoader resourceLoader, PropertyResolver propertyResolver) {
      this.resourceLoader = resourceLoader;
      this.propertyResolver = propertyResolver;
   }

   @Override
   public void registerCustomEditors(PropertyEditorRegistry registry) {
      ResourceEditor baseEditor = new ResourceEditor(this.resourceLoader, this.propertyResolver);
      this.doRegisterEditor(registry, Resource.class, baseEditor);
      this.doRegisterEditor(registry, ContextResource.class, baseEditor);
      this.doRegisterEditor(registry, WritableResource.class, baseEditor);
      this.doRegisterEditor(registry, InputStream.class, new InputStreamEditor(baseEditor));
      this.doRegisterEditor(registry, InputSource.class, new InputSourceEditor(baseEditor));
      this.doRegisterEditor(registry, File.class, new FileEditor(baseEditor));
      this.doRegisterEditor(registry, Path.class, new PathEditor(baseEditor));
      this.doRegisterEditor(registry, Reader.class, new ReaderEditor(baseEditor));
      this.doRegisterEditor(registry, URL.class, new URLEditor(baseEditor));
      ClassLoader classLoader = this.resourceLoader.getClassLoader();
      this.doRegisterEditor(registry, URI.class, new URIEditor(classLoader));
      this.doRegisterEditor(registry, Class.class, new ClassEditor(classLoader));
      this.doRegisterEditor(registry, Class[].class, new ClassArrayEditor(classLoader));
      if (this.resourceLoader instanceof ResourcePatternResolver resourcePatternResolver) {
         this.doRegisterEditor(registry, Resource[].class, new ResourceArrayPropertyEditor(resourcePatternResolver, this.propertyResolver));
      }
   }

   private void doRegisterEditor(PropertyEditorRegistry registry, Class<?> requiredType, PropertyEditor editor) {
      if (registry instanceof PropertyEditorRegistrySupport registrySupport) {
         registrySupport.overrideDefaultEditor(requiredType, editor);
      } else {
         registry.registerCustomEditor(requiredType, editor);
      }
   }

   @Override
   public boolean overridesDefaultEditors() {
      return true;
   }
}
