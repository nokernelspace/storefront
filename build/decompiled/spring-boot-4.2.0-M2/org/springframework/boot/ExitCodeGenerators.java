package org.springframework.boot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;

class ExitCodeGenerators implements Iterable<ExitCodeGenerator> {
   private final List<ExitCodeGenerator> generators = new ArrayList<>();

   void addAll(Throwable exception, ExitCodeExceptionMapper... mappers) {
      Assert.notNull(exception, "'exception' must not be null");
      Assert.notNull(mappers, "'mappers' must not be null");
      this.addAll(exception, Arrays.asList(mappers));
   }

   void addAll(Throwable exception, Iterable<? extends ExitCodeExceptionMapper> mappers) {
      Assert.notNull(exception, "'exception' must not be null");
      Assert.notNull(mappers, "'mappers' must not be null");

      for (ExitCodeExceptionMapper mapper : mappers) {
         this.add(exception, mapper);
      }
   }

   void add(Throwable exception, ExitCodeExceptionMapper mapper) {
      Assert.notNull(exception, "'exception' must not be null");
      Assert.notNull(mapper, "'mapper' must not be null");
      this.add(new ExitCodeGenerators.MappedExitCodeGenerator(exception, mapper));
   }

   void addAll(ExitCodeGenerator... generators) {
      Assert.notNull(generators, "'generators' must not be null");
      this.addAll(Arrays.asList(generators));
   }

   void addAll(Iterable<? extends ExitCodeGenerator> generators) {
      Assert.notNull(generators, "'generators' must not be null");

      for (ExitCodeGenerator generator : generators) {
         this.add(generator);
      }
   }

   void add(ExitCodeGenerator generator) {
      Assert.notNull(generator, "'generator' must not be null");
      this.generators.add(generator);
      AnnotationAwareOrderComparator.sort(this.generators);
   }

   @Override
   public Iterator<ExitCodeGenerator> iterator() {
      return this.generators.iterator();
   }

   int getExitCode() {
      int exitCode = 0;

      for (ExitCodeGenerator generator : this.generators) {
         try {
            int value = generator.getExitCode();
            if (value != 0) {
               exitCode = value;
               break;
            }
         } catch (Exception ex) {
            exitCode = 1;
            ex.printStackTrace();
         }
      }

      return exitCode;
   }

   private static class MappedExitCodeGenerator implements ExitCodeGenerator {
      private final Throwable exception;
      private final ExitCodeExceptionMapper mapper;

      MappedExitCodeGenerator(Throwable exception, ExitCodeExceptionMapper mapper) {
         this.exception = exception;
         this.mapper = mapper;
      }

      @Override
      public int getExitCode() {
         return this.mapper.getExitCode(this.exception);
      }
   }
}
