package org.springframework.aop.aspectj;

import java.util.Objects;
import org.aspectj.weaver.tools.PointcutParser;
import org.aspectj.weaver.tools.TypePatternMatcher;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class TypePatternClassFilter implements ClassFilter {
   private String typePattern = "";
   private @Nullable TypePatternMatcher aspectJTypePatternMatcher;

   public TypePatternClassFilter() {
   }

   public TypePatternClassFilter(String typePattern) {
      this.setTypePattern(typePattern);
   }

   public void setTypePattern(String typePattern) {
      Assert.notNull(typePattern, "Type pattern must not be null");
      this.typePattern = typePattern;
      this.aspectJTypePatternMatcher = PointcutParser.getPointcutParserSupportingAllPrimitivesAndUsingContextClassloaderForResolution()
         .parseTypePattern(this.replaceBooleanOperators(typePattern));
   }

   public String getTypePattern() {
      return this.typePattern;
   }

   @Override
   public boolean matches(Class<?> clazz) {
      Assert.state(this.aspectJTypePatternMatcher != null, "No type pattern has been set");
      return this.aspectJTypePatternMatcher.matches(clazz);
   }

   private String replaceBooleanOperators(String pcExpr) {
      String result = StringUtils.replace(pcExpr, " and ", " && ");
      result = StringUtils.replace(result, " or ", " || ");
      return StringUtils.replace(result, " not ", " ! ");
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof TypePatternClassFilter that && ObjectUtils.nullSafeEquals(this.typePattern, that.typePattern);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(this.typePattern);
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.typePattern;
   }
}
