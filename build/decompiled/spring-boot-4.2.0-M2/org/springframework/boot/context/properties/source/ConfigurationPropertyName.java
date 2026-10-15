package org.springframework.boot.context.properties.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public final class ConfigurationPropertyName implements Comparable<ConfigurationPropertyName> {
   private static final String EMPTY_STRING = "";
   public static final ConfigurationPropertyName EMPTY = new ConfigurationPropertyName(ConfigurationPropertyName.Elements.EMPTY);
   private final ConfigurationPropertyName.Elements elements;
   private final @Nullable CharSequence[] uniformElements;
   private int hashCode;
   private @Nullable String[] string = new String[ConfigurationPropertyName.ToStringFormat.values().length];
   private @Nullable Boolean hasDashedElement;
   private @Nullable ConfigurationPropertyName systemEnvironmentLegacyName;

   private ConfigurationPropertyName(ConfigurationPropertyName.Elements elements) {
      this.elements = elements;
      this.uniformElements = new CharSequence[elements.getSize()];
   }

   public boolean isEmpty() {
      return this.elements.getSize() == 0;
   }

   public boolean isLastElementIndexed() {
      int size = this.getNumberOfElements();
      return size > 0 && this.isIndexed(size - 1);
   }

   public boolean hasIndexedElement() {
      for (int i = 0; i < this.getNumberOfElements(); i++) {
         if (this.isIndexed(i)) {
            return true;
         }
      }

      return false;
   }

   boolean isIndexed(int elementIndex) {
      return this.elements.getType(elementIndex).isIndexed();
   }

   public boolean isNumericIndex(int elementIndex) {
      return this.elements.getType(elementIndex) == ConfigurationPropertyName.ElementType.NUMERICALLY_INDEXED;
   }

   public String getLastElement(ConfigurationPropertyName.Form form) {
      int size = this.getNumberOfElements();
      return size != 0 ? this.getElement(size - 1, form) : "";
   }

   public String getElement(int elementIndex, ConfigurationPropertyName.Form form) {
      CharSequence element = this.elements.get(elementIndex);
      ConfigurationPropertyName.ElementType type = this.elements.getType(elementIndex);
      if (type.isIndexed()) {
         return element.toString();
      }

      if (form == ConfigurationPropertyName.Form.ORIGINAL) {
         return type != ConfigurationPropertyName.ElementType.NON_UNIFORM ? element.toString() : this.convertToOriginalForm(element).toString();
      }

      if (form == ConfigurationPropertyName.Form.DASHED) {
         return type != ConfigurationPropertyName.ElementType.UNIFORM && type != ConfigurationPropertyName.ElementType.DASHED
            ? this.convertToDashedElement(element).toString()
            : element.toString();
      }

      CharSequence uniformElement = this.uniformElements[elementIndex];
      if (uniformElement == null) {
         uniformElement = type != ConfigurationPropertyName.ElementType.UNIFORM ? this.convertToUniformElement(element) : element;
         this.uniformElements[elementIndex] = uniformElement.toString();
      }

      return uniformElement.toString();
   }

   private CharSequence convertToOriginalForm(CharSequence element) {
      return this.convertElement(element, false, (ch, i) -> ch == '_' || ConfigurationPropertyName.ElementsParser.isValidChar(Character.toLowerCase(ch), i));
   }

   private CharSequence convertToDashedElement(CharSequence element) {
      return this.convertElement(element, true, ConfigurationPropertyName.ElementsParser::isValidChar);
   }

   private CharSequence convertToUniformElement(CharSequence element) {
      return this.convertElement(element, true, (ch, i) -> ConfigurationPropertyName.ElementsParser.isAlphaNumeric(ch));
   }

   private CharSequence convertElement(CharSequence element, boolean lowercase, ConfigurationPropertyName.ElementCharPredicate filter) {
      StringBuilder result = new StringBuilder(element.length());

      for (int i = 0; i < element.length(); i++) {
         char ch = lowercase ? Character.toLowerCase(element.charAt(i)) : element.charAt(i);
         if (filter.test(ch, i)) {
            result.append(ch);
         }
      }

      return result;
   }

   public int getNumberOfElements() {
      return this.elements.getSize();
   }

   public ConfigurationPropertyName append(@Nullable String suffix) {
      if (!StringUtils.hasLength(suffix)) {
         return this;
      }

      ConfigurationPropertyName.Elements additionalElements = probablySingleElementOf(suffix);
      return new ConfigurationPropertyName(this.elements.append(additionalElements));
   }

   public ConfigurationPropertyName append(@Nullable ConfigurationPropertyName suffix) {
      return suffix == null ? this : new ConfigurationPropertyName(this.elements.append(suffix.elements));
   }

   public ConfigurationPropertyName getParent() {
      int numberOfElements = this.getNumberOfElements();
      return numberOfElements <= 1 ? EMPTY : this.chop(numberOfElements - 1);
   }

   public ConfigurationPropertyName chop(int size) {
      return size >= this.getNumberOfElements() ? this : new ConfigurationPropertyName(this.elements.chop(size));
   }

   public ConfigurationPropertyName subName(int offset) {
      if (offset == 0) {
         return this;
      } else if (offset == this.getNumberOfElements()) {
         return EMPTY;
      } else if (offset >= 0 && offset <= this.getNumberOfElements()) {
         return new ConfigurationPropertyName(this.elements.subElements(offset));
      } else {
         throw new IndexOutOfBoundsException("Offset: " + offset + ", NumberOfElements: " + this.getNumberOfElements());
      }
   }

   public boolean isParentOf(ConfigurationPropertyName name) {
      Assert.notNull(name, "'name' must not be null");
      return this.getNumberOfElements() != name.getNumberOfElements() - 1 ? false : this.isAncestorOf(name);
   }

   public boolean isAncestorOf(ConfigurationPropertyName name) {
      Assert.notNull(name, "'name' must not be null");
      return this.getNumberOfElements() >= name.getNumberOfElements() ? false : this.endsWithElementsEqualTo(name);
   }

   public int compareTo(ConfigurationPropertyName other) {
      return this.compare(this, other);
   }

   private int compare(ConfigurationPropertyName n1, ConfigurationPropertyName n2) {
      int l1 = n1.getNumberOfElements();
      int l2 = n2.getNumberOfElements();
      int i1 = 0;
      int i2 = 0;

      while (i1 < l1 || i2 < l2) {
         try {
            ConfigurationPropertyName.ElementType type1 = i1 < l1 ? n1.elements.getType(i1) : null;
            ConfigurationPropertyName.ElementType type2 = i2 < l2 ? n2.elements.getType(i2) : null;
            String e1 = i1 < l1 ? n1.getElement(i1++, ConfigurationPropertyName.Form.UNIFORM) : null;
            String e2 = i2 < l2 ? n2.getElement(i2++, ConfigurationPropertyName.Form.UNIFORM) : null;
            int result = this.compare(e1, type1, e2, type2);
            if (result != 0) {
               return result;
            }
         } catch (ArrayIndexOutOfBoundsException ex) {
            throw new RuntimeException(ex);
         }
      }

      return 0;
   }

   private int compare(
      @Nullable String e1, ConfigurationPropertyName.@Nullable ElementType type1, @Nullable String e2, ConfigurationPropertyName.@Nullable ElementType type2
   ) {
      if (e1 == null) {
         return -1;
      } else if (e2 == null) {
         return 1;
      } else {
         Assert.state(type1 != null, "'type1' must not be null");
         Assert.state(type2 != null, "'type2' must not be null");
         int result = Boolean.compare(type2.isIndexed(), type1.isIndexed());
         if (result != 0) {
            return result;
         } else if (type1 == ConfigurationPropertyName.ElementType.NUMERICALLY_INDEXED && type2 == ConfigurationPropertyName.ElementType.NUMERICALLY_INDEXED) {
            long v1 = Long.parseLong(e1);
            long v2 = Long.parseLong(e2);
            return Long.compare(v1, v2);
         } else {
            return e1.compareTo(e2);
         }
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) {
         return true;
      }

      if (obj != null && obj.getClass() == this.getClass()) {
         ConfigurationPropertyName other = (ConfigurationPropertyName)obj;
         if (this.getNumberOfElements() != other.getNumberOfElements()) {
            return false;
         } else if (this.elements.canShortcutWithSource(ConfigurationPropertyName.ElementType.UNIFORM)
            && other.elements.canShortcutWithSource(ConfigurationPropertyName.ElementType.UNIFORM)) {
            return this.toString().equals(other.toString());
         } else if (this.hashCode() != other.hashCode()) {
            return false;
         } else {
            return this.toStringMatches(this.toString(), other.toString()) ? true : this.endsWithElementsEqualTo(other);
         }
      } else {
         return false;
      }
   }

   private boolean toStringMatches(String s1, String s2) {
      return s1.hashCode() == s2.hashCode() && s1.equals(s2);
   }

   private boolean endsWithElementsEqualTo(ConfigurationPropertyName name) {
      for (int i = this.elements.getSize() - 1; i >= 0; i--) {
         if (this.elementDiffers(this.elements, name.elements, i)) {
            return false;
         }
      }

      return true;
   }

   private boolean elementDiffers(ConfigurationPropertyName.Elements e1, ConfigurationPropertyName.Elements e2, int i) {
      ConfigurationPropertyName.ElementType type1 = e1.getType(i);
      ConfigurationPropertyName.ElementType type2 = e2.getType(i);
      if (type1.allowsFastEqualityCheck() && type2.allowsFastEqualityCheck()) {
         return !this.fastElementEquals(e1, e2, i);
      } else {
         return type1.allowsDashIgnoringEqualityCheck() && type2.allowsDashIgnoringEqualityCheck()
            ? !this.dashIgnoringElementEquals(e1, e2, i)
            : !this.defaultElementEquals(e1, e2, i);
      }
   }

   private boolean fastElementEquals(ConfigurationPropertyName.Elements e1, ConfigurationPropertyName.Elements e2, int i) {
      int length1 = e1.getLength(i);
      int length2 = e2.getLength(i);
      if (length1 == length2) {
         for (int i1 = 0; length1-- != 0; i1++) {
            char ch1 = e1.charAt(i, i1);
            char ch2 = e2.charAt(i, i1);
            if (ch1 != ch2) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean dashIgnoringElementEquals(ConfigurationPropertyName.Elements e1, ConfigurationPropertyName.Elements e2, int i) {
      int l1 = e1.getLength(i);
      int l2 = e2.getLength(i);
      int i1 = 0;
      int i2 = 0;

      while (i1 < l1) {
         if (i2 >= l2) {
            return this.remainderIsDashes(e1, i, i1);
         }

         char ch1 = e1.charAt(i, i1);
         char ch2 = e2.charAt(i, i2);
         if (ch1 == '-') {
            i1++;
         } else if (ch2 == '-') {
            i2++;
         } else {
            if (ch1 != ch2) {
               return false;
            }

            i1++;
            i2++;
         }
      }

      if (i2 < l2) {
         if (e2.getType(i).isIndexed()) {
            return false;
         }

         do {
            char ch2 = e2.charAt(i, i2++);
            if (ch2 != '-') {
               return false;
            }
         } while (i2 < l2);
      }

      return true;
   }

   private boolean defaultElementEquals(ConfigurationPropertyName.Elements e1, ConfigurationPropertyName.Elements e2, int i) {
      int l1 = e1.getLength(i);
      int l2 = e2.getLength(i);
      boolean indexed1 = e1.getType(i).isIndexed();
      boolean indexed2 = e2.getType(i).isIndexed();
      int i1 = 0;
      int i2 = 0;

      while (i1 < l1) {
         if (i2 >= l2) {
            return this.remainderIsNotAlphanumeric(e1, i, i1);
         }

         char ch1 = indexed1 ? e1.charAt(i, i1) : Character.toLowerCase(e1.charAt(i, i1));
         char ch2 = indexed2 ? e2.charAt(i, i2) : Character.toLowerCase(e2.charAt(i, i2));
         if (!indexed1 && !ConfigurationPropertyName.ElementsParser.isAlphaNumeric(ch1)) {
            i1++;
         } else if (!indexed2 && !ConfigurationPropertyName.ElementsParser.isAlphaNumeric(ch2)) {
            i2++;
         } else {
            if (ch1 != ch2) {
               return false;
            }

            i1++;
            i2++;
         }
      }

      return i2 < l2 ? this.remainderIsNotAlphanumeric(e2, i, i2) : true;
   }

   private boolean remainderIsNotAlphanumeric(ConfigurationPropertyName.Elements elements, int element, int index) {
      if (elements.getType(element).isIndexed()) {
         return false;
      }

      int length = elements.getLength(element);

      do {
         char c = Character.toLowerCase(elements.charAt(element, index++));
         if (ConfigurationPropertyName.ElementsParser.isAlphaNumeric(c)) {
            return false;
         }
      } while (index < length);

      return true;
   }

   private boolean remainderIsDashes(ConfigurationPropertyName.Elements elements, int element, int index) {
      if (elements.getType(element).isIndexed()) {
         return false;
      }

      int length = elements.getLength(element);

      do {
         char c = elements.charAt(element, index++);
         if (c != '-') {
            return false;
         }
      } while (index < length);

      return true;
   }

   @Override
   public int hashCode() {
      int hashCode = this.hashCode;
      ConfigurationPropertyName.Elements elements = this.elements;
      if (hashCode == 0 && elements.getSize() != 0) {
         for (int elementIndex = 0; elementIndex < elements.getSize(); elementIndex++) {
            hashCode = 31 * hashCode + elements.hashCode(elementIndex);
         }

         this.hashCode = hashCode;
      }

      return hashCode;
   }

   @Nullable ConfigurationPropertyName asSystemEnvironmentLegacyName() {
      ConfigurationPropertyName name = this.systemEnvironmentLegacyName;
      if (name == null) {
         name = ofIfValid(this.buildSimpleToString('.', i -> this.getElement(i, ConfigurationPropertyName.Form.DASHED).replace('-', '.')));
         this.systemEnvironmentLegacyName = name != null ? name : EMPTY;
      }

      return name != EMPTY ? name : null;
   }

   @Override
   public String toString() {
      return this.toString(ConfigurationPropertyName.ToStringFormat.DEFAULT, false);
   }

   String toString(ConfigurationPropertyName.ToStringFormat format, boolean upperCase) {
      String string = this.string[format.ordinal()];
      if (string == null) {
         string = this.buildToString(format);
         this.string[format.ordinal()] = string;
      }

      return !upperCase ? string : string.toUpperCase(Locale.ENGLISH);
   }

   private String buildToString(ConfigurationPropertyName.ToStringFormat format) {
      return switch (format) {
         case DEFAULT -> this.buildDefaultToString();
         case SYSTEM_ENVIRONMENT -> this.buildSimpleToString('_', i -> this.getElement(i, ConfigurationPropertyName.Form.UNIFORM));
         case LEGACY_SYSTEM_ENVIRONMENT -> this.buildSimpleToString('_', i -> this.getElement(i, ConfigurationPropertyName.Form.ORIGINAL).replace('-', '_'));
      };
   }

   private String buildDefaultToString() {
      if (this.elements.canShortcutWithSource(ConfigurationPropertyName.ElementType.UNIFORM, ConfigurationPropertyName.ElementType.DASHED)) {
         return this.elements.getSource().toString();
      }

      int elements = this.getNumberOfElements();
      StringBuilder result = new StringBuilder(elements * 8);

      for (int i = 0; i < elements; i++) {
         boolean indexed = this.isIndexed(i);
         if (!result.isEmpty() && !indexed) {
            result.append('.');
         }

         if (indexed) {
            result.append('[');
            result.append(this.getElement(i, ConfigurationPropertyName.Form.ORIGINAL));
            result.append(']');
         } else {
            result.append(this.getElement(i, ConfigurationPropertyName.Form.DASHED));
         }
      }

      return result.toString();
   }

   private String buildSimpleToString(char joinChar, IntFunction<String> elementConverter) {
      StringBuilder result = new StringBuilder();

      for (int i = 0; i < this.getNumberOfElements(); i++) {
         if (!result.isEmpty()) {
            result.append(joinChar);
         }

         result.append(elementConverter.apply(i));
      }

      return result.toString();
   }

   boolean hasDashedElement() {
      Boolean hasDashedElement = this.hasDashedElement;
      if (hasDashedElement != null) {
         return hasDashedElement;
      }

      for (int i = 0; i < this.getNumberOfElements(); i++) {
         if (this.getElement(i, ConfigurationPropertyName.Form.DASHED).indexOf(45) != -1) {
            this.hasDashedElement = true;
            return true;
         }
      }

      this.hasDashedElement = false;
      return false;
   }

   public static boolean isValid(@Nullable CharSequence name) {
      return of(name, true) != null;
   }

   public static ConfigurationPropertyName of(@Nullable CharSequence name) {
      return of(name, false);
   }

   public static @Nullable ConfigurationPropertyName ofIfValid(@Nullable CharSequence name) {
      return of(name, true);
   }

   @Contract("_, false -> !null")
   static @Nullable ConfigurationPropertyName of(@Nullable CharSequence name, boolean returnNullIfInvalid) {
      ConfigurationPropertyName.Elements elements = elementsOf(name, returnNullIfInvalid, 6);
      return elements != null ? new ConfigurationPropertyName(elements) : null;
   }

   private static ConfigurationPropertyName.Elements probablySingleElementOf(CharSequence name) {
      return elementsOf(name, false, 1);
   }

   @Contract("_, false, _ -> !null")
   private static ConfigurationPropertyName.@Nullable Elements elementsOf(@Nullable CharSequence name, boolean returnNullIfInvalid, int parserCapacity) {
      if (name == null) {
         Assert.isTrue(returnNullIfInvalid, "'name' must not be null");
         return null;
      }

      if (name.isEmpty()) {
         return ConfigurationPropertyName.Elements.EMPTY;
      }

      if (name.charAt(0) != '.' && name.charAt(name.length() - 1) != '.') {
         ConfigurationPropertyName.Elements elements = new ConfigurationPropertyName.ElementsParser(name, '.', parserCapacity).parse();

         for (int i = 0; i < elements.getSize(); i++) {
            if (elements.getType(i) == ConfigurationPropertyName.ElementType.NON_UNIFORM) {
               if (returnNullIfInvalid) {
                  return null;
               }

               throw new InvalidConfigurationPropertyNameException(name, getInvalidChars(elements, i));
            }
         }

         return elements;
      } else if (returnNullIfInvalid) {
         return null;
      } else {
         throw new InvalidConfigurationPropertyNameException(name, Collections.singletonList('.'));
      }
   }

   private static List<Character> getInvalidChars(ConfigurationPropertyName.Elements elements, int index) {
      List<Character> invalidChars = new ArrayList<>();

      for (int charIndex = 0; charIndex < elements.getLength(index); charIndex++) {
         char ch = elements.charAt(index, charIndex);
         if (!ConfigurationPropertyName.ElementsParser.isValidChar(ch, charIndex)) {
            invalidChars.add(ch);
         }
      }

      return invalidChars;
   }

   public static ConfigurationPropertyName adapt(CharSequence name, char separator) {
      return adapt(name, separator, null);
   }

   static ConfigurationPropertyName adapt(CharSequence name, char separator, @Nullable Function<CharSequence, CharSequence> elementValueProcessor) {
      Assert.notNull(name, "Name must not be null");
      if (name.isEmpty()) {
         return EMPTY;
      }

      ConfigurationPropertyName.Elements elements = new ConfigurationPropertyName.ElementsParser(name, separator).parse(elementValueProcessor);
      return elements.getSize() == 0 ? EMPTY : new ConfigurationPropertyName(elements);
   }

   private interface ElementCharPredicate {
      boolean test(char ch, int index);
   }

   private enum ElementType {
      EMPTY(false),
      UNIFORM(false),
      DASHED(false),
      NON_UNIFORM(false),
      INDEXED(true),
      NUMERICALLY_INDEXED(true);

      private final boolean indexed;

      ElementType(boolean indexed) {
         this.indexed = indexed;
      }

      public boolean isIndexed() {
         return this.indexed;
      }

      public boolean allowsFastEqualityCheck() {
         return this == UNIFORM || this == NUMERICALLY_INDEXED;
      }

      public boolean allowsDashIgnoringEqualityCheck() {
         return this.allowsFastEqualityCheck() || this == DASHED;
      }
   }

   private static class Elements {
      private static final int[] NO_POSITION = new int[0];
      private static final ConfigurationPropertyName.ElementType[] NO_TYPE = new ConfigurationPropertyName.ElementType[0];
      public static final ConfigurationPropertyName.Elements EMPTY = new ConfigurationPropertyName.Elements(
         "", 0, NO_POSITION, NO_POSITION, NO_TYPE, null, null
      );
      private final CharSequence source;
      private final int size;
      private final int[] start;
      private final int[] end;
      private final ConfigurationPropertyName.ElementType[] type;
      private final int[] hashCode;
      private final @Nullable CharSequence @Nullable [] resolved;

      Elements(
         CharSequence source,
         int size,
         int[] start,
         int[] end,
         ConfigurationPropertyName.ElementType[] type,
         int @Nullable [] hashCode,
         CharSequence @Nullable [] resolved
      ) {
         this.source = source;
         this.size = size;
         this.start = start;
         this.end = end;
         this.type = type;
         this.hashCode = hashCode != null ? hashCode : new int[size];
         this.resolved = resolved;
      }

      ConfigurationPropertyName.Elements append(ConfigurationPropertyName.Elements additional) {
         int size = this.size + additional.size;
         ConfigurationPropertyName.ElementType[] type = new ConfigurationPropertyName.ElementType[size];
         int[] hashCode = new int[size];
         System.arraycopy(this.type, 0, type, 0, this.size);
         System.arraycopy(additional.type, 0, type, this.size, additional.size);
         System.arraycopy(this.hashCode, 0, hashCode, 0, this.size);
         System.arraycopy(additional.hashCode, 0, hashCode, this.size, additional.size);
         CharSequence[] resolved = this.newResolved(0, size);

         for (int i = 0; i < additional.size; i++) {
            resolved[this.size + i] = additional.get(i);
         }

         return new ConfigurationPropertyName.Elements(this.source, size, this.start, this.end, type, hashCode, resolved);
      }

      ConfigurationPropertyName.Elements chop(int size) {
         CharSequence[] resolved = this.newResolved(0, size);
         return new ConfigurationPropertyName.Elements(this.source, size, this.start, this.end, this.type, this.hashCode, resolved);
      }

      ConfigurationPropertyName.Elements subElements(int offset) {
         int size = this.size - offset;
         CharSequence[] resolved = this.newResolved(offset, size);
         int[] start = new int[size];
         System.arraycopy(this.start, offset, start, 0, size);
         int[] end = new int[size];
         System.arraycopy(this.end, offset, end, 0, size);
         ConfigurationPropertyName.ElementType[] type = new ConfigurationPropertyName.ElementType[size];
         System.arraycopy(this.type, offset, type, 0, size);
         int[] hashCode = new int[size];
         System.arraycopy(this.hashCode, offset, hashCode, 0, size);
         return new ConfigurationPropertyName.Elements(this.source, size, start, end, type, hashCode, resolved);
      }

      private CharSequence[] newResolved(int offset, int size) {
         CharSequence[] resolved = new CharSequence[size];
         if (this.resolved != null) {
            System.arraycopy(this.resolved, offset, resolved, 0, Math.min(size, this.size));
         }

         return resolved;
      }

      int getSize() {
         return this.size;
      }

      CharSequence get(int index) {
         if (this.resolved != null) {
            CharSequence element = this.resolved[index];
            if (element != null) {
               return element;
            }
         }

         int start = this.start[index];
         int end = this.end[index];
         return this.source.subSequence(start, end);
      }

      int getLength(int index) {
         if (this.resolved != null) {
            CharSequence element = this.resolved[index];
            if (element != null) {
               return element.length();
            }
         }

         int start = this.start[index];
         int end = this.end[index];
         return end - start;
      }

      char charAt(int index, int charIndex) {
         if (this.resolved != null) {
            CharSequence element = this.resolved[index];
            if (element != null) {
               return element.charAt(charIndex);
            }
         }

         int start = this.start[index];
         return this.source.charAt(start + charIndex);
      }

      ConfigurationPropertyName.ElementType getType(int index) {
         return this.type[index];
      }

      int hashCode(int index) {
         int hashCode = this.hashCode[index];
         if (hashCode == 0) {
            boolean indexed = this.getType(index).isIndexed();
            int length = this.getLength(index);

            for (int i = 0; i < length; i++) {
               char ch = this.charAt(index, i);
               if (!indexed) {
                  ch = Character.toLowerCase(ch);
               }

               if (ConfigurationPropertyName.ElementsParser.isAlphaNumeric(ch)) {
                  hashCode = 31 * hashCode + ch;
               }
            }

            this.hashCode[index] = hashCode;
         }

         return hashCode;
      }

      CharSequence getSource() {
         return this.source;
      }

      boolean canShortcutWithSource(ConfigurationPropertyName.ElementType requiredType) {
         return this.canShortcutWithSource(requiredType, requiredType);
      }

      boolean canShortcutWithSource(ConfigurationPropertyName.ElementType requiredType, ConfigurationPropertyName.ElementType alternativeType) {
         if (this.resolved != null) {
            return false;
         }

         for (int i = 0; i < this.size; i++) {
            ConfigurationPropertyName.ElementType type = this.type[i];
            if (type != requiredType && type != alternativeType) {
               return false;
            }

            if (i > 0 && this.end[i - 1] + 1 != this.start[i]) {
               return false;
            }
         }

         return true;
      }
   }

   private static class ElementsParser {
      private static final int DEFAULT_CAPACITY = 6;
      private final CharSequence source;
      private final char separator;
      private int size;
      private int[] start;
      private int[] end;
      private ConfigurationPropertyName.ElementType[] type;
      private CharSequence @Nullable [] resolved;

      ElementsParser(CharSequence source, char separator) {
         this(source, separator, 6);
      }

      ElementsParser(CharSequence source, char separator, int capacity) {
         this.source = source;
         this.separator = separator;
         this.start = new int[capacity];
         this.end = new int[capacity];
         this.type = new ConfigurationPropertyName.ElementType[capacity];
      }

      ConfigurationPropertyName.Elements parse() {
         return this.parse(null);
      }

      ConfigurationPropertyName.Elements parse(@Nullable Function<CharSequence, CharSequence> valueProcessor) {
         int length = this.source.length();
         int openBracketCount = 0;
         int start = 0;
         ConfigurationPropertyName.ElementType type = ConfigurationPropertyName.ElementType.EMPTY;

         for (int i = 0; i < length; i++) {
            char ch = this.source.charAt(i);
            if (ch == '[') {
               if (openBracketCount == 0) {
                  this.add(start, i, type, valueProcessor);
                  start = i + 1;
                  type = ConfigurationPropertyName.ElementType.NUMERICALLY_INDEXED;
               }

               openBracketCount++;
            } else if (ch == ']') {
               if (--openBracketCount == 0) {
                  this.add(start, i, type, valueProcessor);
                  start = i + 1;
                  type = ConfigurationPropertyName.ElementType.EMPTY;
               }
            } else if (!type.isIndexed() && ch == this.separator) {
               this.add(start, i, type, valueProcessor);
               start = i + 1;
               type = ConfigurationPropertyName.ElementType.EMPTY;
            } else {
               type = this.updateType(type, ch, i - start);
            }
         }

         if (openBracketCount != 0) {
            type = ConfigurationPropertyName.ElementType.NON_UNIFORM;
         }

         this.add(start, length, type, valueProcessor);
         return new ConfigurationPropertyName.Elements(this.source, this.size, this.start, this.end, this.type, null, this.resolved);
      }

      private ConfigurationPropertyName.ElementType updateType(ConfigurationPropertyName.ElementType existingType, char ch, int index) {
         if (existingType.isIndexed()) {
            return existingType == ConfigurationPropertyName.ElementType.NUMERICALLY_INDEXED && !isNumeric(ch)
               ? ConfigurationPropertyName.ElementType.INDEXED
               : existingType;
         } else if (existingType == ConfigurationPropertyName.ElementType.EMPTY && isValidChar(ch, index)) {
            return index == 0 ? ConfigurationPropertyName.ElementType.UNIFORM : ConfigurationPropertyName.ElementType.NON_UNIFORM;
         } else if (existingType == ConfigurationPropertyName.ElementType.UNIFORM && ch == '-') {
            return ConfigurationPropertyName.ElementType.DASHED;
         } else if (!isValidChar(ch, index)) {
            return existingType == ConfigurationPropertyName.ElementType.EMPTY && !isValidChar(Character.toLowerCase(ch), index)
               ? ConfigurationPropertyName.ElementType.EMPTY
               : ConfigurationPropertyName.ElementType.NON_UNIFORM;
         } else {
            return existingType;
         }
      }

      private void add(int start, int end, ConfigurationPropertyName.ElementType type, @Nullable Function<CharSequence, CharSequence> valueProcessor) {
         if (end - start >= 1 && type != ConfigurationPropertyName.ElementType.EMPTY) {
            if (this.start.length == this.size) {
               this.start = this.expand(this.start);
               this.end = this.expand(this.end);
               this.type = this.expand(this.type);
               this.resolved = this.expand(this.resolved);
            }

            if (valueProcessor != null) {
               if (this.resolved == null) {
                  this.resolved = new CharSequence[this.start.length];
               }

               CharSequence resolved = valueProcessor.apply(this.source.subSequence(start, end));
               ConfigurationPropertyName.Elements resolvedElements = new ConfigurationPropertyName.ElementsParser(resolved, '.').parse();
               Assert.state(resolvedElements.getSize() == 1, "Resolved element must not contain multiple elements");
               this.resolved[this.size] = resolvedElements.get(0);
               type = resolvedElements.getType(0);
            }

            this.start[this.size] = start;
            this.end[this.size] = end;
            this.type[this.size] = type;
            this.size++;
         }
      }

      private int[] expand(int[] src) {
         int[] dest = new int[src.length + 6];
         System.arraycopy(src, 0, dest, 0, src.length);
         return dest;
      }

      private ConfigurationPropertyName.ElementType[] expand(ConfigurationPropertyName.ElementType[] src) {
         ConfigurationPropertyName.ElementType[] dest = new ConfigurationPropertyName.ElementType[src.length + 6];
         System.arraycopy(src, 0, dest, 0, src.length);
         return dest;
      }

      private CharSequence @Nullable [] expand(CharSequence @Nullable [] src) {
         if (src == null) {
            return null;
         }

         CharSequence[] dest = new CharSequence[src.length + 6];
         System.arraycopy(src, 0, dest, 0, src.length);
         return dest;
      }

      static boolean isValidChar(char ch, int index) {
         return isAlpha(ch) || isNumeric(ch) || index != 0 && ch == '-';
      }

      static boolean isAlphaNumeric(char ch) {
         return isAlpha(ch) || isNumeric(ch);
      }

      private static boolean isAlpha(char ch) {
         return ch >= 'a' && ch <= 'z';
      }

      private static boolean isNumeric(char ch) {
         return ch >= '0' && ch <= '9';
      }
   }

   public enum Form {
      ORIGINAL,
      DASHED,
      UNIFORM;
   }

   enum ToStringFormat {
      DEFAULT,
      SYSTEM_ENVIRONMENT,
      LEGACY_SYSTEM_ENVIRONMENT;
   }
}
