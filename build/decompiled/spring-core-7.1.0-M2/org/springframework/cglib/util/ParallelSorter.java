package org.springframework.cglib.util;

import java.util.Comparator;
import org.springframework.asm.ClassVisitor;
import org.springframework.cglib.core.AbstractClassGenerator;
import org.springframework.cglib.core.ClassesKey;
import org.springframework.cglib.core.ReflectUtils;

public abstract class ParallelSorter extends SorterTemplate {
   protected Object[] a;
   private ParallelSorter.Comparer comparer;

   protected ParallelSorter() {
   }

   public abstract ParallelSorter newInstance(Object[] arrays);

   public static ParallelSorter create(Object[] arrays) {
      ParallelSorter.Generator gen = new ParallelSorter.Generator();
      gen.setArrays(arrays);
      return gen.create();
   }

   private int len() {
      return ((Object[])this.a[0]).length;
   }

   public void quickSort(int index) {
      this.quickSort(index, 0, this.len(), null);
   }

   public void quickSort(int index, int lo, int hi) {
      this.quickSort(index, lo, hi, null);
   }

   public void quickSort(int index, Comparator cmp) {
      this.quickSort(index, 0, this.len(), cmp);
   }

   public void quickSort(int index, int lo, int hi, Comparator cmp) {
      this.chooseComparer(index, cmp);
      super.quickSort(lo, hi - 1);
   }

   public void mergeSort(int index) {
      this.mergeSort(index, 0, this.len(), null);
   }

   public void mergeSort(int index, int lo, int hi) {
      this.mergeSort(index, lo, hi, null);
   }

   public void mergeSort(int index, Comparator cmp) {
      this.mergeSort(index, 0, this.len(), cmp);
   }

   public void mergeSort(int index, int lo, int hi, Comparator cmp) {
      this.chooseComparer(index, cmp);
      super.mergeSort(lo, hi - 1);
   }

   private void chooseComparer(int index, Comparator cmp) {
      Object array = this.a[index];
      Class type = array.getClass().componentType();
      if (type.equals(int.class)) {
         this.comparer = new ParallelSorter.IntComparer((int[])array);
      } else if (type.equals(long.class)) {
         this.comparer = new ParallelSorter.LongComparer((long[])array);
      } else if (type.equals(double.class)) {
         this.comparer = new ParallelSorter.DoubleComparer((double[])array);
      } else if (type.equals(float.class)) {
         this.comparer = new ParallelSorter.FloatComparer((float[])array);
      } else if (type.equals(short.class)) {
         this.comparer = new ParallelSorter.ShortComparer((short[])array);
      } else if (type.equals(byte.class)) {
         this.comparer = new ParallelSorter.ByteComparer((byte[])array);
      } else if (cmp != null) {
         this.comparer = new ParallelSorter.ComparatorComparer((Object[])array, cmp);
      } else {
         this.comparer = new ParallelSorter.ObjectComparer((Object[])array);
      }
   }

   @Override
   protected int compare(int i, int j) {
      return this.comparer.compare(i, j);
   }

   static class ByteComparer implements ParallelSorter.Comparer {
      private final byte[] a;

      public ByteComparer(byte[] a) {
         this.a = a;
      }

      @Override
      public int compare(int i, int j) {
         return this.a[i] - this.a[j];
      }
   }

   static class ComparatorComparer implements ParallelSorter.Comparer {
      private final Object[] a;
      private final Comparator cmp;

      public ComparatorComparer(Object[] a, Comparator cmp) {
         this.a = a;
         this.cmp = cmp;
      }

      @Override
      public int compare(int i, int j) {
         return this.cmp.compare(this.a[i], this.a[j]);
      }
   }

   interface Comparer {
      int compare(int i, int j);
   }

   static class DoubleComparer implements ParallelSorter.Comparer {
      private final double[] a;

      public DoubleComparer(double[] a) {
         this.a = a;
      }

      @Override
      public int compare(int i, int j) {
         double vi = this.a[i];
         double vj = this.a[j];
         return Double.compare(vi, vj);
      }
   }

   static class FloatComparer implements ParallelSorter.Comparer {
      private final float[] a;

      public FloatComparer(float[] a) {
         this.a = a;
      }

      @Override
      public int compare(int i, int j) {
         float vi = this.a[i];
         float vj = this.a[j];
         return Float.compare(vi, vj);
      }
   }

   public static class Generator extends AbstractClassGenerator {
      private static final AbstractClassGenerator.Source SOURCE = new AbstractClassGenerator.Source(ParallelSorter.class.getName());
      private Object[] arrays;

      public Generator() {
         super(SOURCE);
      }

      @Override
      protected ClassLoader getDefaultClassLoader() {
         return null;
      }

      public void setArrays(Object[] arrays) {
         this.arrays = arrays;
      }

      public ParallelSorter create() {
         return (ParallelSorter)super.create(ClassesKey.create(this.arrays));
      }

      @Override
      public void generateClass(ClassVisitor v) throws Exception {
         if (this.arrays.length == 0) {
            throw new IllegalArgumentException("No arrays specified to sort");
         }

         for (Object array : this.arrays) {
            if (!array.getClass().isArray()) {
               throw new IllegalArgumentException(array.getClass() + " is not an array");
            }
         }

         new ParallelSorterEmitter(v, this.getClassName(), this.arrays);
      }

      @Override
      protected Object firstInstance(Class type) {
         return ((ParallelSorter)ReflectUtils.newInstance(type)).newInstance(this.arrays);
      }

      @Override
      protected Object nextInstance(Object instance) {
         return ((ParallelSorter)instance).newInstance(this.arrays);
      }
   }

   static class IntComparer implements ParallelSorter.Comparer {
      private final int[] a;

      public IntComparer(int[] a) {
         this.a = a;
      }

      @Override
      public int compare(int i, int j) {
         return this.a[i] - this.a[j];
      }
   }

   static class LongComparer implements ParallelSorter.Comparer {
      private final long[] a;

      public LongComparer(long[] a) {
         this.a = a;
      }

      @Override
      public int compare(int i, int j) {
         long vi = this.a[i];
         long vj = this.a[j];
         return Long.compare(vi, vj);
      }
   }

   static class ObjectComparer implements ParallelSorter.Comparer {
      private final Object[] a;

      public ObjectComparer(Object[] a) {
         this.a = a;
      }

      @Override
      public int compare(int i, int j) {
         return ((Comparable)this.a[i]).compareTo(this.a[j]);
      }
   }

   static class ShortComparer implements ParallelSorter.Comparer {
      private final short[] a;

      public ShortComparer(short[] a) {
         this.a = a;
      }

      @Override
      public int compare(int i, int j) {
         return this.a[i] - this.a[j];
      }
   }
}
