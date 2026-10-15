package org.springframework.web.service.registry;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotationMetadata;

class ImportHttpServiceRegistrar extends AbstractHttpServiceRegistrar {
   @Override
   protected void registerHttpServices(AbstractHttpServiceRegistrar.GroupRegistry registry, AnnotationMetadata metadata) {
      MergedAnnotation<?> groupsAnnot = metadata.getAnnotations().get(ImportHttpServices.Container.class);
      if (groupsAnnot.isPresent()) {
         for (MergedAnnotation<?> annot : groupsAnnot.getAnnotationArray("value", ImportHttpServices.class)) {
            this.processImportAnnotation(annot, registry);
         }
      }

      metadata.getAnnotations().stream(ImportHttpServices.class).forEach(annotx -> this.processImportAnnotation(annotx, registry));
   }

   private void processImportAnnotation(MergedAnnotation<?> annotation, AbstractHttpServiceRegistrar.GroupRegistry groupRegistry) {
      String groupName = annotation.getString("group");
      HttpServiceGroup.ClientType clientType = (HttpServiceGroup.ClientType)annotation.getEnum("clientType", HttpServiceGroup.ClientType.class);
      groupRegistry.forGroup(groupName, clientType)
         .register(annotation.getClassArray("types"))
         .detectInBasePackages(annotation.getStringArray("basePackages"))
         .detectInBasePackages(annotation.getClassArray("basePackageClasses"));
   }
}
