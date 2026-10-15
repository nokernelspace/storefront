package org.springframework.cglib.beans;

import java.beans.PropertyDescriptor;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Type;
import org.springframework.cglib.core.AbstractClassGenerator;
import org.springframework.cglib.core.ClassEmitter;
import org.springframework.cglib.core.Constants;
import org.springframework.cglib.core.EmitUtils;
import org.springframework.cglib.core.KeyFactory;
import org.springframework.cglib.core.ReflectUtils;

public class BeanGenerator extends AbstractClassGenerator {
   private static final AbstractClassGenerator.Source SOURCE = new AbstractClassGenerator.Source(BeanGenerator.class.getName());
   private static final BeanGenerator.BeanGeneratorKey KEY_FACTORY = (BeanGenerator.BeanGeneratorKey)KeyFactory.create(BeanGenerator.BeanGeneratorKey.class);
   private Class superclass;
   private Map props = new HashMap();
   private boolean classOnly;

   public BeanGenerator() {
      super(SOURCE);
   }

   public void setSuperclass(Class superclass) {
      if (superclass != null && superclass.equals(Object.class)) {
         superclass = null;
      }

      this.superclass = superclass;
      this.setContextClass(superclass);
   }

   public void addProperty(String name, Class type) {
      if (this.props.containsKey(name)) {
         throw new IllegalArgumentException("Duplicate property name \"" + name + "\"");
      }

      this.props.put(name, Type.getType(type));
   }

   @Override
   protected ClassLoader getDefaultClassLoader() {
      return this.superclass != null ? this.superclass.getClassLoader() : null;
   }

   @Override
   protected ProtectionDomain getProtectionDomain() {
      return ReflectUtils.getProtectionDomain(this.superclass);
   }

   public Object create() {
      this.classOnly = false;
      return this.createHelper();
   }

   public Object createClass() {
      this.classOnly = true;
      return this.createHelper();
   }

   private Object createHelper() {
      if (this.superclass != null) {
         this.setNamePrefix(this.superclass.getName());
      }

      String superName = this.superclass != null ? this.superclass.getName() : "java.lang.Object";
      Object key = KEY_FACTORY.newInstance(superName, this.props);
      return super.create(key);
   }

   @Override
   public void generateClass(ClassVisitor v) throws Exception {
      int size = this.props.size();
      String[] names = this.props.keySet().toArray(new String[size]);
      Type[] types = new Type[size];

      for (int i = 0; i < size; i++) {
         types[i] = (Type)this.props.get(names[i]);
      }

      ClassEmitter ce = new ClassEmitter(v);
      ce.begin_class(52, 1, this.getClassName(), this.superclass != null ? Type.getType(this.superclass) : Constants.TYPE_OBJECT, null, null);
      EmitUtils.null_constructor(ce);
      EmitUtils.add_properties(ce, names, types);
      ce.end_class();
   }

   @Override
   protected Object firstInstance(Class type) {
      return this.classOnly ? type : ReflectUtils.newInstance(type);
   }

   @Override
   protected Object nextInstance(Object instance) {
      Class protoclass = instance instanceof Class<?> clazz ? clazz : instance.getClass();
      return this.classOnly ? protoclass : ReflectUtils.newInstance(protoclass);
   }

   public static void addProperties(BeanGenerator gen, Map props) {
      for (String name : props.keySet()) {
         gen.addProperty(name, (Class)props.get(name));
      }
   }

   public static void addProperties(BeanGenerator gen, Class type) {
      addProperties(gen, ReflectUtils.getBeanProperties(type));
   }

   public static void addProperties(BeanGenerator gen, PropertyDescriptor[] descriptors) {
      for (PropertyDescriptor descriptor : descriptors) {
         gen.addProperty(descriptor.getName(), descriptor.getPropertyType());
      }
   }

   interface BeanGeneratorKey {
      Object newInstance(String superclass, Map props);
   }
}
