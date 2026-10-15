package org.springframework.boot.env;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class RandomValuePropertySource extends PropertySource<Random> {
   public static final String RANDOM_PROPERTY_SOURCE_NAME = "random";
   private static final String PREFIX = "random.";
   private static final Log logger = LogFactory.getLog(RandomValuePropertySource.class);

   public RandomValuePropertySource() {
      this("random");
   }

   public RandomValuePropertySource(String name) {
      super(name, new SecureRandom());
   }

   public @Nullable Object getProperty(String name) {
      if (!name.startsWith("random.")) {
         return null;
      }

      logger.trace(LogMessage.format("Generating random property for '%s'", name));
      return this.getRandomValue(name.substring("random.".length()));
   }

   private Object getRandomValue(String type) {
      if (type.equals("int")) {
         return ((Random)this.getSource()).nextInt();
      } else if (type.equals("long")) {
         return ((Random)this.getSource()).nextLong();
      } else {
         String range = this.getRange(type, "int");
         if (range != null) {
            return this.getNextIntInRange(RandomValuePropertySource.Range.of(range, Integer::parseInt));
         } else {
            range = this.getRange(type, "long");
            if (range != null) {
               return this.getNextLongInRange(RandomValuePropertySource.Range.of(range, Long::parseLong));
            } else {
               return type.equals("uuid") ? UUID.randomUUID().toString() : this.getRandomBytes();
            }
         }
      }
   }

   private @Nullable String getRange(String type, String prefix) {
      if (type.startsWith(prefix)) {
         int startIndex = prefix.length() + 1;
         if (type.length() > startIndex) {
            return type.substring(startIndex, type.length() - 1);
         }
      }

      return null;
   }

   private int getNextIntInRange(RandomValuePropertySource.Range<Integer> range) {
      OptionalInt first = ((Random)this.getSource()).ints(1L, range.getMin(), range.getMax()).findFirst();
      this.assertPresent(first.isPresent(), range);
      return first.getAsInt();
   }

   private long getNextLongInRange(RandomValuePropertySource.Range<Long> range) {
      OptionalLong first = ((Random)this.getSource()).longs(1L, range.getMin(), range.getMax()).findFirst();
      this.assertPresent(first.isPresent(), range);
      return first.getAsLong();
   }

   private void assertPresent(boolean present, RandomValuePropertySource.Range<?> range) {
      Assert.state(present, () -> "Could not get random number for range '" + range + "'");
   }

   private Object getRandomBytes() {
      byte[] bytes = new byte[16];
      ((Random)this.getSource()).nextBytes(bytes);
      return HexFormat.of().withLowerCase().formatHex(bytes);
   }

   public static void addToEnvironment(ConfigurableEnvironment environment) {
      addToEnvironment(environment, logger);
   }

   public static void addToEnvironment(ConfigurableEnvironment environment, Log logger) {
      MutablePropertySources sources = environment.getPropertySources();
      PropertySource<?> existing = sources.get("random");
      if (existing != null) {
         logger.trace("RandomValuePropertySource already present");
      } else {
         RandomValuePropertySource randomSource = new RandomValuePropertySource("random");
         if (sources.get("systemEnvironment") != null) {
            sources.addAfter("systemEnvironment", randomSource);
         } else {
            sources.addLast(randomSource);
         }

         logger.trace("RandomValuePropertySource add to Environment");
      }
   }

   static final class Range<T extends Number> {
      private final String value;
      private final T min;
      private final T max;

      private Range(String value, T min, T max) {
         this.value = value;
         this.min = min;
         this.max = max;
      }

      T getMin() {
         return this.min;
      }

      T getMax() {
         return this.max;
      }

      @Override
      public String toString() {
         return this.value;
      }

      static <T extends Number & Comparable<T>> RandomValuePropertySource.Range<T> of(String value, Function<String, T> parse) {
         T zero = (T)parse.apply("0");
         String[] tokens = StringUtils.commaDelimitedListToStringArray(value);
         T min = (T)parse.apply(tokens[0]);
         if (tokens.length == 1) {
            Assert.state(min.compareTo(zero) > 0, "Bound must be positive.");
            return new RandomValuePropertySource.Range<>(value, zero, min);
         } else {
            T max = (T)parse.apply(tokens[1]);
            Assert.state(min.compareTo(max) < 0, "Lower bound must be less than upper bound.");
            return new RandomValuePropertySource.Range<>(value, min, max);
         }
      }
   }
}
