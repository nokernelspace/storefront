package org.springframework.expression.spel;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.core.SpringProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class SpelParserConfiguration {
   public static final int DEFAULT_MAX_AUTO_GROW_SIZE = 256;
   public static final int DEFAULT_MAX_EXPRESSION_LENGTH = 10000;
   public static final int DEFAULT_MAX_OPERATIONS = 10000;
   public static final int DEFAULT_MAX_BIG_POWER_BITS = 1000000;
   public static final int DEFAULT_MAX_EXPRESSION_NESTING_DEPTH = 1000;
   public static final String SPRING_EXPRESSION_COMPILER_MODE_PROPERTY_NAME = "spring.expression.compiler.mode";
   public static final String SPRING_EXPRESSION_MAX_OPERATIONS_PROPERTY_NAME = "spring.expression.maxOperations";
   public static final String SPRING_EXPRESSION_MAX_BIG_POWER_BITS_PROPERTY_NAME = "spring.expression.maxBigPowerBits";
   private static final SpelCompilerMode defaultCompilerMode;
   private final SpelCompilerMode compilerMode;
   private final @Nullable ClassLoader compilerClassLoader;
   private final boolean autoGrowNullReferences;
   private final boolean autoGrowCollections;
   private final int maximumAutoGrowSize;
   private final int maximumExpressionLength;
   private final int maximumOperations;
   private final int maximumBigPowerBits;
   private final int maximumNestingDepth;

   public static SpelParserConfiguration withDefaults() {
      return builder().build();
   }

   public static SpelParserConfiguration.Builder builder() {
      return new SpelParserConfiguration.Builder();
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration() {
      this(null, null);
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration(@Nullable SpelCompilerMode compilerMode, @Nullable ClassLoader compilerClassLoader) {
      this(compilerMode, compilerClassLoader, false, false, 256);
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration(boolean autoGrowNullReferences, boolean autoGrowCollections) {
      this(autoGrowNullReferences, autoGrowCollections, 256);
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration(boolean autoGrowNullReferences, boolean autoGrowCollections, int maximumAutoGrowSize) {
      this(null, null, autoGrowNullReferences, autoGrowCollections, maximumAutoGrowSize);
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration(
      @Nullable SpelCompilerMode compilerMode,
      @Nullable ClassLoader compilerClassLoader,
      boolean autoGrowNullReferences,
      boolean autoGrowCollections,
      int maximumAutoGrowSize
   ) {
      this(compilerMode, compilerClassLoader, autoGrowNullReferences, autoGrowCollections, maximumAutoGrowSize, 10000);
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration(
      @Nullable SpelCompilerMode compilerMode,
      @Nullable ClassLoader compilerClassLoader,
      boolean autoGrowNullReferences,
      boolean autoGrowCollections,
      int maximumAutoGrowSize,
      int maximumExpressionLength
   ) {
      this(
         compilerMode != null ? compilerMode : defaultCompilerMode,
         compilerClassLoader,
         autoGrowNullReferences,
         autoGrowCollections,
         maximumAutoGrowSize,
         maximumExpressionLength,
         retrieveMaxOperations()
      );
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration(
      SpelCompilerMode compilerMode,
      @Nullable ClassLoader compilerClassLoader,
      boolean autoGrowNullReferences,
      boolean autoGrowCollections,
      int maximumAutoGrowSize,
      int maximumExpressionLength,
      int maximumOperations
   ) {
      this(
         compilerMode,
         compilerClassLoader,
         autoGrowNullReferences,
         autoGrowCollections,
         maximumAutoGrowSize,
         maximumExpressionLength,
         maximumOperations,
         retrieveMaxBigPowerBits()
      );
   }

   @Deprecated(since = "7.1")
   public SpelParserConfiguration(
      SpelCompilerMode compilerMode,
      @Nullable ClassLoader compilerClassLoader,
      boolean autoGrowNullReferences,
      boolean autoGrowCollections,
      int maximumAutoGrowSize,
      int maximumExpressionLength,
      int maximumOperations,
      int maximumBigPowerBits
   ) {
      this(
         compilerMode,
         compilerClassLoader,
         autoGrowNullReferences,
         autoGrowCollections,
         maximumAutoGrowSize,
         maximumExpressionLength,
         maximumOperations,
         maximumBigPowerBits,
         1000
      );
   }

   SpelParserConfiguration(
      SpelCompilerMode compilerMode,
      @Nullable ClassLoader compilerClassLoader,
      boolean autoGrowNullReferences,
      boolean autoGrowCollections,
      int maximumAutoGrowSize,
      int maximumExpressionLength,
      int maximumOperations,
      int maximumBigPowerBits,
      int maximumNestingDepth
   ) {
      Assert.notNull(compilerMode, "'compilerMode' must not be null");
      Assert.isTrue(maximumAutoGrowSize >= 0, "'maximumAutoGrowSize' must not be negative");
      Assert.isTrue(maximumExpressionLength > 0, "'maximumExpressionLength' must be a positive number");
      Assert.isTrue(maximumOperations > 0, "'maximumOperations' must be a positive number");
      Assert.isTrue(maximumBigPowerBits > 0, "'maximumBigPowerBits' must be a positive number");
      Assert.isTrue(maximumNestingDepth > 0, "'maximumNestingDepth' must be a positive number");
      this.compilerMode = compilerMode;
      this.compilerClassLoader = compilerClassLoader;
      this.autoGrowNullReferences = autoGrowNullReferences;
      this.autoGrowCollections = autoGrowCollections;
      this.maximumAutoGrowSize = maximumAutoGrowSize;
      this.maximumExpressionLength = maximumExpressionLength;
      this.maximumOperations = maximumOperations;
      this.maximumBigPowerBits = maximumBigPowerBits;
      this.maximumNestingDepth = maximumNestingDepth;
   }

   public SpelCompilerMode getCompilerMode() {
      return this.compilerMode;
   }

   public @Nullable ClassLoader getCompilerClassLoader() {
      return this.compilerClassLoader;
   }

   public boolean isAutoGrowNullReferences() {
      return this.autoGrowNullReferences;
   }

   public boolean isAutoGrowCollections() {
      return this.autoGrowCollections;
   }

   public int getMaximumAutoGrowSize() {
      return this.maximumAutoGrowSize;
   }

   public int getMaximumExpressionLength() {
      return this.maximumExpressionLength;
   }

   public int getMaximumOperations() {
      return this.maximumOperations;
   }

   public int getMaximumBigPowerBits() {
      return this.maximumBigPowerBits;
   }

   public int getMaximumNestingDepth() {
      return this.maximumNestingDepth;
   }

   private static int retrieveMaxOperations() {
      String value = SpringProperties.getProperty("spring.expression.maxOperations");
      if (!StringUtils.hasText(value)) {
         return 10000;
      }

      try {
         int maxOperations = Integer.parseInt(value.trim());
         Assert.isTrue(maxOperations > 0, () -> "Value [" + maxOperations + "] for system property [spring.expression.maxOperations] must be positive");
         return maxOperations;
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException("Failed to parse value for system property [spring.expression.maxOperations]: " + ex.getMessage(), ex);
      }
   }

   private static int retrieveMaxBigPowerBits() {
      String value = SpringProperties.getProperty("spring.expression.maxBigPowerBits");
      if (!StringUtils.hasText(value)) {
         return 1000000;
      }

      try {
         int maxBits = Integer.parseInt(value.trim());
         Assert.isTrue(maxBits > 0, () -> "Value [" + maxBits + "] for system property [spring.expression.maxBigPowerBits] must be positive");
         return maxBits;
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException("Failed to parse value for system property [spring.expression.maxBigPowerBits]: " + ex.getMessage(), ex);
      }
   }

   static {
      String compilerMode = SpringProperties.getProperty("spring.expression.compiler.mode");
      defaultCompilerMode = compilerMode != null ? SpelCompilerMode.valueOf(compilerMode.toUpperCase(Locale.ROOT)) : SpelCompilerMode.OFF;
   }

   public static final class Builder {
      private SpelCompilerMode compilerMode = SpelParserConfiguration.defaultCompilerMode;
      private @Nullable ClassLoader compilerClassLoader;
      private boolean autoGrowNullReferences = false;
      private boolean autoGrowCollections = false;
      private int maximumAutoGrowSize = 256;
      private int maximumExpressionLength = 10000;
      private @Nullable Integer maximumOperations;
      private @Nullable Integer maximumBigPowerBits;
      private int maximumNestingDepth = 1000;

      private Builder() {
      }

      public SpelParserConfiguration.Builder compilerMode(SpelCompilerMode compilerMode) {
         Assert.notNull(compilerMode, "'compilerMode' must not be null");
         this.compilerMode = compilerMode;
         return this;
      }

      public SpelParserConfiguration.Builder compilerClassLoader(@Nullable ClassLoader compilerClassLoader) {
         this.compilerClassLoader = compilerClassLoader;
         return this;
      }

      public SpelParserConfiguration.Builder autoGrowNullReferences() {
         this.autoGrowNullReferences = true;
         return this;
      }

      public SpelParserConfiguration.Builder autoGrowCollections() {
         this.autoGrowCollections = true;
         return this;
      }

      public SpelParserConfiguration.Builder maximumAutoGrowSize(int maximumAutoGrowSize) {
         Assert.isTrue(maximumAutoGrowSize >= 0, "'maximumAutoGrowSize' must not be negative");
         this.maximumAutoGrowSize = maximumAutoGrowSize;
         return this;
      }

      public SpelParserConfiguration.Builder maximumExpressionLength(int maximumExpressionLength) {
         Assert.isTrue(maximumExpressionLength > 0, "'maximumExpressionLength' must be a positive number");
         this.maximumExpressionLength = maximumExpressionLength;
         return this;
      }

      public SpelParserConfiguration.Builder maximumOperations(int maximumOperations) {
         Assert.isTrue(maximumOperations > 0, "'maximumOperations' must be a positive number");
         this.maximumOperations = maximumOperations;
         return this;
      }

      public SpelParserConfiguration.Builder maximumBigPowerBits(int maximumBigPowerBits) {
         Assert.isTrue(maximumBigPowerBits > 0, "'maximumBigPowerBits' must be a positive number");
         this.maximumBigPowerBits = maximumBigPowerBits;
         return this;
      }

      public SpelParserConfiguration.Builder maximumNestingDepth(int maximumNestingDepth) {
         Assert.isTrue(maximumNestingDepth > 0, "'maximumNestingDepth' must be a positive number");
         this.maximumNestingDepth = maximumNestingDepth;
         return this;
      }

      public SpelParserConfiguration build() {
         int maximumOperations = this.maximumOperations != null ? this.maximumOperations : SpelParserConfiguration.retrieveMaxOperations();
         int maximumBigPowerBits = this.maximumBigPowerBits != null ? this.maximumBigPowerBits : SpelParserConfiguration.retrieveMaxBigPowerBits();
         return new SpelParserConfiguration(
            this.compilerMode,
            this.compilerClassLoader,
            this.autoGrowNullReferences,
            this.autoGrowCollections,
            this.maximumAutoGrowSize,
            this.maximumExpressionLength,
            maximumOperations,
            maximumBigPowerBits,
            this.maximumNestingDepth
         );
      }
   }
}
