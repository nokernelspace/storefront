package org.springframework.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

public class MimeType implements Comparable<MimeType>, Serializable {
   private static final long serialVersionUID = 4085923477777865903L;
   protected static final String WILDCARD_TYPE = "*";
   private static final String PARAM_CHARSET = "charset";
   private static final BitSet TOKEN;
   private final String type;
   private final String subtype;
   private final Map<String, String> parameters;
   private transient @Nullable Charset resolvedCharset;
   private volatile @Nullable String toStringValue;
   private volatile int hash;

   public MimeType(String type) {
      this(type, "*");
   }

   public MimeType(String type, String subtype) {
      this(type, subtype, Collections.emptyMap());
   }

   public MimeType(String type, String subtype, Charset charset) {
      this(type, subtype, Collections.singletonMap("charset", charset.name()));
   }

   public MimeType(String type, String subtype, @Nullable Map<String, String> parameters) {
      Assert.hasLength(type, "'type' must not be empty");
      Assert.hasLength(subtype, "'subtype' must not be empty");
      this.checkToken(type);
      this.checkToken(subtype);
      this.type = type.toLowerCase(Locale.ROOT);
      this.subtype = subtype.toLowerCase(Locale.ROOT);
      this.parameters = this.createParametersMap(parameters);
      if (this.parameters.containsKey("charset")) {
         this.resolvedCharset = Charset.forName(this.unquote(this.parameters.get("charset")));
      }
   }

   public MimeType(MimeType other, Charset charset) {
      this.type = other.type;
      this.subtype = other.subtype;
      Map<String, String> map = new LinkedCaseInsensitiveMap<>(other.parameters.size() + 1, Locale.ROOT);
      map.putAll(other.parameters);
      map.put("charset", charset.name());
      this.parameters = Collections.unmodifiableMap(map);
      this.resolvedCharset = charset;
   }

   public MimeType(MimeType other, @Nullable Map<String, String> parameters) {
      this.type = other.type;
      this.subtype = other.subtype;
      this.parameters = this.createParametersMap(parameters);
      if (this.parameters.containsKey("charset")) {
         this.resolvedCharset = Charset.forName(this.unquote(this.parameters.get("charset")));
      }
   }

   protected MimeType(MimeType other) {
      this.type = other.type;
      this.subtype = other.subtype;
      this.parameters = other.parameters;
      this.resolvedCharset = other.resolvedCharset;
      this.toStringValue = other.toStringValue;
      this.hash = other.hash;
   }

   private void checkToken(String token) {
      for (int i = 0; i < token.length(); i++) {
         char ch = token.charAt(i);
         if (!TOKEN.get(ch)) {
            throw new IllegalArgumentException("Invalid token character '" + ch + "' in token \"" + token + "\"");
         }
      }
   }

   private Map<String, String> createParametersMap(@Nullable Map<String, String> parameters) {
      if (!CollectionUtils.isEmpty(parameters)) {
         Map<String, String> map = new LinkedCaseInsensitiveMap<>(parameters.size(), Locale.ROOT);
         parameters.forEach((parameter, value) -> {
            this.checkParameters(parameter, value);
            map.put(parameter, value);
         });
         return Collections.unmodifiableMap(map);
      } else {
         return Collections.emptyMap();
      }
   }

   protected void checkParameters(String parameter, String value) {
      Assert.hasLength(parameter, "'parameter' must not be empty");
      Assert.hasLength(value, "'value' must not be empty");
      this.checkToken(parameter);
      if (!this.isQuotedString(value)) {
         this.checkToken(value);
      }
   }

   private boolean isQuotedString(String s) {
      return s.length() < 2 ? false : s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'");
   }

   protected String unquote(String s) {
      if (!this.isQuotedString(s)) {
         return s;
      }

      String inner = s.substring(1, s.length() - 1);
      if (inner.indexOf(92) == -1) {
         return inner;
      }

      StringBuilder sb = new StringBuilder(inner.length());

      for (int i = 0; i < inner.length(); i++) {
         char c = inner.charAt(i);
         if (c == '\\' && i + 1 < inner.length()) {
            c = inner.charAt(++i);
         }

         sb.append(c);
      }

      return sb.toString();
   }

   public boolean isWildcardType() {
      return "*".equals(this.getType());
   }

   public boolean isWildcardSubtype() {
      String subtype = this.getSubtype();
      return "*".equals(subtype) || subtype.startsWith("*+");
   }

   public boolean isConcrete() {
      return !this.isWildcardType() && !this.isWildcardSubtype();
   }

   public String getType() {
      return this.type;
   }

   public String getSubtype() {
      return this.subtype;
   }

   public @Nullable String getSubtypeSuffix() {
      int suffixIndex = this.subtype.lastIndexOf(43);
      return suffixIndex != -1 && this.subtype.length() > suffixIndex ? this.subtype.substring(suffixIndex + 1) : null;
   }

   public @Nullable Charset getCharset() {
      return this.resolvedCharset;
   }

   public @Nullable String getParameter(String name) {
      return this.parameters.get(name);
   }

   public Map<String, String> getParameters() {
      return this.parameters;
   }

   public boolean includes(@Nullable MimeType other) {
      if (other == null) {
         return false;
      }

      if (this.isWildcardType()) {
         return true;
      }

      if (this.getType().equals(other.getType())) {
         if (this.getSubtype().equals(other.getSubtype())) {
            return true;
         }

         if (this.isWildcardSubtype()) {
            int thisPlusIdx = this.getSubtype().lastIndexOf(43);
            if (thisPlusIdx == -1) {
               return true;
            }

            int otherPlusIdx = other.getSubtype().lastIndexOf(43);
            if (otherPlusIdx != -1) {
               String thisSubtypeNoSuffix = this.getSubtype().substring(0, thisPlusIdx);
               String thisSubtypeSuffix = this.getSubtype().substring(thisPlusIdx + 1);
               String otherSubtypeSuffix = other.getSubtype().substring(otherPlusIdx + 1);
               if (thisSubtypeSuffix.equals(otherSubtypeSuffix) && "*".equals(thisSubtypeNoSuffix)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public boolean isCompatibleWith(@Nullable MimeType other) {
      if (other == null) {
         return false;
      }

      if (!this.isWildcardType() && !other.isWildcardType()) {
         if (this.getType().equals(other.getType())) {
            if (this.getSubtype().equals(other.getSubtype())) {
               return true;
            }

            if (this.isWildcardSubtype() || other.isWildcardSubtype()) {
               String thisSuffix = this.getSubtypeSuffix();
               String otherSuffix = other.getSubtypeSuffix();
               if (this.getSubtype().equals("*") || other.getSubtype().equals("*")) {
                  return true;
               }

               if (this.isWildcardSubtype() && thisSuffix != null) {
                  return thisSuffix.equals(other.getSubtype()) || thisSuffix.equals(otherSuffix);
               }

               if (other.isWildcardSubtype() && otherSuffix != null) {
                  return this.getSubtype().equals(otherSuffix) || otherSuffix.equals(thisSuffix);
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public boolean equalsTypeAndSubtype(@Nullable MimeType other) {
      return other == null ? false : this.type.equalsIgnoreCase(other.type) && this.subtype.equalsIgnoreCase(other.subtype);
   }

   public boolean isPresentIn(Collection<? extends MimeType> mimeTypes) {
      for (MimeType mimeType : mimeTypes) {
         if (mimeType.equalsTypeAndSubtype(this)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof MimeType otherType
            && this.type.equalsIgnoreCase(otherType.type)
            && this.subtype.equalsIgnoreCase(otherType.subtype)
            && this.parametersAreEqual(otherType);
   }

   private boolean parametersAreEqual(MimeType other) {
      if (this.parameters.size() != other.parameters.size()) {
         return false;
      }

      for (Entry<String, String> entry : this.parameters.entrySet()) {
         String key = entry.getKey();
         if (!other.parameters.containsKey(key)) {
            return false;
         }

         if ("charset".equalsIgnoreCase(key)) {
            if (!ObjectUtils.nullSafeEquals(this.getCharset(), other.getCharset())) {
               return false;
            }
         } else if (!ObjectUtils.nullSafeEquals(this.unquote(entry.getValue()), this.unquote(other.parameters.get(key)))) {
            return false;
         }
      }

      return true;
   }

   @Override
   public int hashCode() {
      int result = this.hash;
      if (result == 0) {
         int var2 = this.type.hashCode();
         int var3 = 31 * var2 + this.subtype.hashCode();
         result = 31 * var3 + this.parametersHashCode();
         this.hash = result;
      }

      return result;
   }

   private int parametersHashCode() {
      int result = 0;

      for (Entry<String, String> entry : this.parameters.entrySet()) {
         String key = entry.getKey();
         Object value = "charset".equalsIgnoreCase(key) ? this.getCharset() : this.unquote(entry.getValue());
         result += key.toLowerCase(Locale.ROOT).hashCode() ^ ObjectUtils.nullSafeHashCode(value);
      }

      return result;
   }

   @Override
   public String toString() {
      String value = this.toStringValue;
      if (value == null) {
         StringBuilder builder = new StringBuilder();
         this.appendTo(builder);
         value = builder.toString();
         this.toStringValue = value;
      }

      return value;
   }

   protected void appendTo(StringBuilder builder) {
      builder.append(this.type);
      builder.append('/');
      builder.append(this.subtype);
      this.appendTo(this.parameters, builder);
   }

   private void appendTo(Map<String, String> map, StringBuilder builder) {
      map.forEach((key, val) -> {
         builder.append(';');
         builder.append(key);
         builder.append('=');
         builder.append(val);
      });
   }

   public int compareTo(MimeType other) {
      int comp = this.getType().compareToIgnoreCase(other.getType());
      if (comp != 0) {
         return comp;
      }

      comp = this.getSubtype().compareToIgnoreCase(other.getSubtype());
      if (comp != 0) {
         return comp;
      }

      comp = this.getParameters().size() - other.getParameters().size();
      if (comp != 0) {
         return comp;
      }

      TreeSet<String> thisAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      thisAttributes.addAll(this.getParameters().keySet());
      TreeSet<String> otherAttributes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      otherAttributes.addAll(other.getParameters().keySet());
      Iterator<String> thisAttributesIterator = thisAttributes.iterator();
      Iterator<String> otherAttributesIterator = otherAttributes.iterator();

      while (thisAttributesIterator.hasNext()) {
         String thisAttribute = thisAttributesIterator.next();
         String otherAttribute = otherAttributesIterator.next();
         comp = thisAttribute.compareToIgnoreCase(otherAttribute);
         if (comp != 0) {
            return comp;
         }

         if ("charset".equalsIgnoreCase(thisAttribute)) {
            Charset thisCharset = this.getCharset();
            Charset otherCharset = other.getCharset();
            if (thisCharset != otherCharset) {
               if (thisCharset == null) {
                  return -1;
               }

               if (otherCharset == null) {
                  return 1;
               }

               comp = thisCharset.compareTo(otherCharset);
               if (comp != 0) {
                  return comp;
               }
            }
         } else {
            String thisValue = this.getParameters().get(thisAttribute);
            String otherValue = other.getParameters().get(otherAttribute);
            Assert.notNull(thisValue, "Parameter for " + thisAttribute + " must not be null");
            if (otherValue == null) {
               otherValue = "";
            }

            comp = this.unquote(thisValue).compareTo(this.unquote(otherValue));
            if (comp != 0) {
               return comp;
            }
         }
      }

      return 0;
   }

   public boolean isMoreSpecific(MimeType other) {
      Assert.notNull(other, "Other must not be null");
      boolean thisWildcard = this.isWildcardType();
      boolean otherWildcard = other.isWildcardType();
      if (thisWildcard && !otherWildcard) {
         return false;
      } else if (!thisWildcard && otherWildcard) {
         return true;
      } else {
         boolean thisWildcardSubtype = this.isWildcardSubtype();
         boolean otherWildcardSubtype = other.isWildcardSubtype();
         if (thisWildcardSubtype && !otherWildcardSubtype) {
            return false;
         } else if (!thisWildcardSubtype && otherWildcardSubtype) {
            return true;
         } else if (this.getType().equals(other.getType()) && this.getSubtype().equals(other.getSubtype())) {
            int paramsSize1 = this.getParameters().size();
            int paramsSize2 = other.getParameters().size();
            return paramsSize1 > paramsSize2;
         } else {
            return false;
         }
      }
   }

   public boolean isLessSpecific(MimeType other) {
      Assert.notNull(other, "Other must not be null");
      return other.isMoreSpecific(this);
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      String charsetName = this.getParameter("charset");
      if (charsetName != null) {
         this.resolvedCharset = Charset.forName(this.unquote(charsetName));
      }
   }

   public static MimeType valueOf(String value) {
      return MimeTypeUtils.parseMimeType(value);
   }

   static {
      BitSet ctl = new BitSet(128);

      for (int i = 0; i <= 31; i++) {
         ctl.set(i);
      }

      ctl.set(127);
      BitSet separators = new BitSet(128);
      separators.set(40);
      separators.set(41);
      separators.set(60);
      separators.set(62);
      separators.set(64);
      separators.set(44);
      separators.set(59);
      separators.set(58);
      separators.set(92);
      separators.set(34);
      separators.set(47);
      separators.set(91);
      separators.set(93);
      separators.set(63);
      separators.set(61);
      separators.set(123);
      separators.set(125);
      separators.set(32);
      separators.set(9);
      TOKEN = new BitSet(128);
      TOKEN.set(0, 128);
      TOKEN.andNot(ctl);
      TOKEN.andNot(separators);
   }
}
