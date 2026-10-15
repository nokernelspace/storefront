package org.springframework.jmx.export.annotation;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;
import org.springframework.jmx.export.naming.MetadataNamingStrategy;

public class AnnotationMBeanExporter extends MBeanExporter {
   private final AnnotationJmxAttributeSource annotationSource = new AnnotationJmxAttributeSource();
   private final MetadataNamingStrategy metadataNamingStrategy = new MetadataNamingStrategy(this.annotationSource);
   private final MetadataMBeanInfoAssembler metadataAssembler = new MetadataMBeanInfoAssembler(this.annotationSource);

   public AnnotationMBeanExporter() {
      this.setNamingStrategy(this.metadataNamingStrategy);
      this.setAssembler(this.metadataAssembler);
      this.setAutodetect(true);
   }

   public void setDefaultDomain(String defaultDomain) {
      this.metadataNamingStrategy.setDefaultDomain(defaultDomain);
   }

   @Override
   public void setBeanFactory(BeanFactory beanFactory) {
      super.setBeanFactory(beanFactory);
      this.annotationSource.setBeanFactory(beanFactory);
   }
}
