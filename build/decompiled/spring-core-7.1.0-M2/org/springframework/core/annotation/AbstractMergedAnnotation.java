package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

abstract class AbstractMergedAnnotation<A extends Annotation> implements MergedAnnotation<A> {
   private volatile @Nullable A synthesizedAnnotation;

   @Override
   public boolean isDirectlyPresent() {
      return this.isPresent() && this.getDistance() == 0;
   }

   @Override
   public boolean isMetaPresent() {
      return this.isPresent() && this.getDistance() > 0;
   }

   @Override
   public boolean hasNonDefaultValue(String attributeName) {
      return !this.hasDefaultValue(attributeName);
   }

   @Override
   public byte getByte(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Byte.class);
   }

   @Override
   public byte[] getByteArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, byte[].class);
   }

   @Override
   public boolean getBoolean(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Boolean.class);
   }

   @Override
   public boolean[] getBooleanArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, boolean[].class);
   }

   @Override
   public char getChar(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Character.class);
   }

   @Override
   public char[] getCharArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, char[].class);
   }

   @Override
   public short getShort(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Short.class);
   }

   @Override
   public short[] getShortArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, short[].class);
   }

   @Override
   public int getInt(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Integer.class);
   }

   @Override
   public int[] getIntArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, int[].class);
   }

   @Override
   public long getLong(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Long.class);
   }

   @Override
   public long[] getLongArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, long[].class);
   }

   @Override
   public double getDouble(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Double.class);
   }

   @Override
   public double[] getDoubleArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, double[].class);
   }

   @Override
   public float getFloat(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Float.class);
   }

   @Override
   public float[] getFloatArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, float[].class);
   }

   @Override
   public String getString(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, String.class);
   }

   @Override
   public String[] getStringArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, String[].class);
   }

   @Override
   public Class<?> getClass(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Class.class);
   }

   @Override
   public Class<?>[] getClassArray(String attributeName) {
      return this.getRequiredAttributeValue(attributeName, Class[].class);
   }

   @Override
   public <E extends Enum<E>> E getEnum(String attributeName, Class<E> type) {
      Assert.notNull(type, "Type must not be null");
      return this.getRequiredAttributeValue(attributeName, type);
   }

   @Override
   public <E extends Enum<E>> E[] getEnumArray(String attributeName, Class<E> type) {
      Assert.notNull(type, "Type must not be null");
      return (E[])((Enum[])this.getRequiredAttributeValue(attributeName, type.arrayType()));
   }

   @Override
   public Optional<Object> getValue(String attributeName) {
      return this.getValue(attributeName, Object.class);
   }

   @Override
   public <T> Optional<T> getValue(String attributeName, Class<T> type) {
      return Optional.ofNullable(this.getAttributeValue(attributeName, type));
   }

   @Override
   public Optional<Object> getDefaultValue(String attributeName) {
      return this.getDefaultValue(attributeName, Object.class);
   }

   @Override
   public MergedAnnotation<A> filterDefaultValues() {
      return this.filterAttributes(this::hasNonDefaultValue);
   }

   @Override
   public AnnotationAttributes asAnnotationAttributes(MergedAnnotation.Adapt... adaptations) {
      return this.asMap(mergedAnnotation -> new AnnotationAttributes((Class<? extends Annotation>)mergedAnnotation.getType()), adaptations);
   }

   @Override
   public Optional<A> synthesize(Predicate<? super MergedAnnotation<A>> condition) throws NoSuchElementException {
      return condition.test(this) ? Optional.of(this.synthesize()) : Optional.empty();
   }

   @Override
   public A synthesize() {
      if (!this.isPresent()) {
         throw new NoSuchElementException("Unable to synthesize missing annotation");
      }

      A synthesized = this.synthesizedAnnotation;
      if (synthesized == null) {
         synthesized = this.createSynthesizedAnnotation();
         this.synthesizedAnnotation = synthesized;
      }

      return synthesized;
   }

   private <T> T getRequiredAttributeValue(String attributeName, Class<T> type) {
      T value = this.getAttributeValue(attributeName, type);
      if (value == null) {
         throw new NoSuchElementException(
            "No attribute named '" + attributeName + "' present in merged annotation " + ClassUtils.getCanonicalName(this.getType())
         );
      } else {
         return value;
      }
   }

   protected abstract <T> @Nullable T getAttributeValue(String attributeName, Class<T> type);

   protected abstract A createSynthesizedAnnotation();
}
