package org.springframework.util;

import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class StringUtils {
   private static final String[] EMPTY_STRING_ARRAY = new String[0];
   private static final String FOLDER_SEPARATOR = "/";
   private static final char FOLDER_SEPARATOR_CHAR = '/';
   private static final String WINDOWS_FOLDER_SEPARATOR = "\\";
   private static final char WINDOWS_FOLDER_SEPARATOR_CHAR = '\\';
   private static final String DOUBLE_BACKSLASHES = "\\\\";
   private static final String TOP_PATH = "..";
   private static final String CURRENT_PATH = ".";
   private static final char DOT_CHAR = '.';
   private static final int DEFAULT_TRUNCATION_THRESHOLD = 100;
   private static final String TRUNCATION_SUFFIX = " (truncated)...";

   @Deprecated(since = "5.3")
   @Contract("null -> true")
   public static boolean isEmpty(@Nullable Object str) {
      return str == null || "".equals(str);
   }

   @Contract("null -> false")
   public static boolean hasLength(@Nullable CharSequence str) {
      return str != null && !str.isEmpty();
   }

   @Contract("null -> false")
   public static boolean hasLength(@Nullable String str) {
      return str != null && !str.isEmpty();
   }

   @Contract("null -> false")
   public static boolean hasText(@Nullable CharSequence str) {
      if (str == null) {
         return false;
      }

      int strLen = str.length();
      if (strLen == 0) {
         return false;
      }

      for (int i = 0; i < strLen; i++) {
         if (!Character.isWhitespace(str.charAt(i))) {
            return true;
         }
      }

      return false;
   }

   @Contract("null -> false")
   public static boolean hasText(@Nullable String str) {
      return str != null && !str.isBlank();
   }

   @Contract("null -> false")
   public static boolean containsWhitespace(@Nullable CharSequence str) {
      if (!hasLength(str)) {
         return false;
      }

      int strLen = str.length();

      for (int i = 0; i < strLen; i++) {
         if (Character.isWhitespace(str.charAt(i))) {
            return true;
         }
      }

      return false;
   }

   @Contract("null -> false")
   public static boolean containsWhitespace(@Nullable String str) {
      return containsWhitespace((CharSequence)str);
   }

   @Deprecated(since = "6.0")
   public static String trimWhitespace(String str) {
      return !hasLength(str) ? str : str.strip();
   }

   public static CharSequence trimAllWhitespace(CharSequence str) {
      if (!hasLength(str)) {
         return str;
      }

      int len = str.length();
      StringBuilder sb = new StringBuilder(str.length());

      for (int i = 0; i < len; i++) {
         char c = str.charAt(i);
         if (!Character.isWhitespace(c)) {
            sb.append(c);
         }
      }

      return sb;
   }

   public static String trimAllWhitespace(String str) {
      return !hasLength(str) ? str : trimAllWhitespace((CharSequence)str).toString();
   }

   @Deprecated(since = "6.0")
   public static String trimLeadingWhitespace(String str) {
      return !hasLength(str) ? str : str.stripLeading();
   }

   @Deprecated(since = "6.0")
   public static String trimTrailingWhitespace(String str) {
      return !hasLength(str) ? str : str.stripTrailing();
   }

   public static String trimLeadingCharacter(String str, char leadingCharacter) {
      if (!hasLength(str)) {
         return str;
      }

      int beginIdx = 0;

      while (beginIdx < str.length() && leadingCharacter == str.charAt(beginIdx)) {
         beginIdx++;
      }

      return str.substring(beginIdx);
   }

   public static String trimTrailingCharacter(String str, char trailingCharacter) {
      if (!hasLength(str)) {
         return str;
      }

      int endIdx = str.length() - 1;

      while (endIdx >= 0 && trailingCharacter == str.charAt(endIdx)) {
         endIdx--;
      }

      return str.substring(0, endIdx + 1);
   }

   @Contract("null, _ -> false")
   public static boolean matchesCharacter(@Nullable String str, char singleCharacter) {
      return str != null && str.length() == 1 && str.charAt(0) == singleCharacter;
   }

   @Contract("null, _ -> false; _, null -> false")
   public static boolean startsWithIgnoreCase(@Nullable String str, @Nullable String prefix) {
      return str != null && prefix != null && str.length() >= prefix.length() && str.regionMatches(true, 0, prefix, 0, prefix.length());
   }

   @Contract("null, _ -> false; _, null -> false")
   public static boolean endsWithIgnoreCase(@Nullable String str, @Nullable String suffix) {
      return str != null
         && suffix != null
         && str.length() >= suffix.length()
         && str.regionMatches(true, str.length() - suffix.length(), suffix, 0, suffix.length());
   }

   public static boolean substringMatch(CharSequence str, int index, CharSequence substring) {
      if (index + substring.length() > str.length()) {
         return false;
      }

      for (int i = 0; i < substring.length(); i++) {
         if (str.charAt(index + i) != substring.charAt(i)) {
            return false;
         }
      }

      return true;
   }

   public static int countOccurrencesOf(String str, String sub) {
      if (hasLength(str) && hasLength(sub)) {
         int count = 0;
         int pos = 0;

         int idx;
         while ((idx = str.indexOf(sub, pos)) != -1) {
            count++;
            pos = idx + sub.length();
         }

         return count;
      } else {
         return 0;
      }
   }

   public static String replace(String inString, String oldPattern, @Nullable String newPattern) {
      if (hasLength(inString) && hasLength(oldPattern) && newPattern != null) {
         int index = inString.indexOf(oldPattern);
         if (index == -1) {
            return inString;
         }

         int capacity = inString.length();
         if (newPattern.length() > oldPattern.length()) {
            capacity += 16;
         }

         StringBuilder sb = new StringBuilder(capacity);
         int pos = 0;
         int patLen = oldPattern.length();

         while (index >= 0) {
            sb.append(inString, pos, index);
            sb.append(newPattern);
            pos = index + patLen;
            index = inString.indexOf(oldPattern, pos);
         }

         sb.append(inString, pos, inString.length());
         return sb.toString();
      } else {
         return inString;
      }
   }

   public static String delete(String inString, String pattern) {
      return replace(inString, pattern, "");
   }

   public static String deleteAny(String inString, @Nullable String charsToDelete) {
      if (hasLength(inString) && hasLength(charsToDelete)) {
         int lastCharIndex = 0;
         char[] result = new char[inString.length()];

         for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            if (charsToDelete.indexOf(c) == -1) {
               result[lastCharIndex++] = c;
            }
         }

         return lastCharIndex == inString.length() ? inString : new String(result, 0, lastCharIndex);
      } else {
         return inString;
      }
   }

   @Contract("null -> null; !null -> !null")
   public static @Nullable String quote(@Nullable String str) {
      return str != null ? "'" + str + "'" : null;
   }

   @Contract("null -> null; !null -> !null")
   public static @Nullable Object quoteIfString(@Nullable Object obj) {
      return obj instanceof String str ? quote(str) : obj;
   }

   public static String unqualify(String qualifiedName) {
      return unqualify(qualifiedName, '.');
   }

   public static String unqualify(String qualifiedName, char separator) {
      return qualifiedName.substring(qualifiedName.lastIndexOf(separator) + 1);
   }

   public static String capitalize(String str) {
      return changeFirstCharacterCase(str, true);
   }

   public static String uncapitalize(String str) {
      return changeFirstCharacterCase(str, false);
   }

   public static String uncapitalizeAsProperty(String str) {
      return hasLength(str) && (str.length() <= 1 || !Character.isUpperCase(str.charAt(0)) || !Character.isUpperCase(str.charAt(1)))
         ? changeFirstCharacterCase(str, false)
         : str;
   }

   private static String changeFirstCharacterCase(String str, boolean capitalize) {
      if (!hasLength(str)) {
         return str;
      }

      char baseChar = str.charAt(0);
      char updatedChar;
      if (capitalize) {
         updatedChar = Character.toUpperCase(baseChar);
      } else {
         updatedChar = Character.toLowerCase(baseChar);
      }

      if (baseChar == updatedChar) {
         return str;
      }

      char[] chars = str.toCharArray();
      chars[0] = updatedChar;
      return new String(chars);
   }

   @Contract("null -> null; !null -> !null")
   public static @Nullable String getFilename(@Nullable String path) {
      if (path == null) {
         return null;
      }

      int separatorIndex = path.lastIndexOf(47);
      return separatorIndex != -1 ? path.substring(separatorIndex + 1) : path;
   }

   @Contract("null -> null")
   public static @Nullable String getFilenameExtension(@Nullable String path) {
      if (path == null) {
         return null;
      }

      int extIndex = path.lastIndexOf(46);
      if (extIndex == -1) {
         return null;
      }

      int folderIndex = path.lastIndexOf(47);
      return folderIndex > extIndex ? null : path.substring(extIndex + 1);
   }

   public static String stripFilenameExtension(String path) {
      int extIndex = path.lastIndexOf(46);
      if (extIndex == -1) {
         return path;
      }

      int folderIndex = path.lastIndexOf(47);
      return folderIndex > extIndex ? path : path.substring(0, extIndex);
   }

   public static String applyRelativePath(String path, String relativePath) {
      int separatorIndex = path.lastIndexOf(47);
      if (separatorIndex != -1) {
         String newPath = path.substring(0, separatorIndex);
         if (!relativePath.startsWith("/")) {
            newPath = newPath + "/";
         }

         return newPath + relativePath;
      } else {
         return relativePath;
      }
   }

   public static String cleanPath(String path) {
      if (!hasLength(path)) {
         return path;
      }

      String normalizedPath;
      if (path.indexOf(92) != -1) {
         normalizedPath = replace(path, "\\\\", "/");
         normalizedPath = replace(normalizedPath, "\\", "/");
      } else {
         normalizedPath = path;
      }

      String pathToUse = normalizedPath;
      if (pathToUse.indexOf(46) == -1) {
         return pathToUse;
      }

      int prefixIndex = pathToUse.indexOf(58);
      String prefix = "";
      if (prefixIndex != -1) {
         prefix = pathToUse.substring(0, prefixIndex + 1);
         if (prefix.contains("/")) {
            prefix = "";
         } else {
            pathToUse = pathToUse.substring(prefixIndex + 1);
         }
      }

      if (pathToUse.startsWith("/")) {
         prefix = prefix + "/";
         pathToUse = pathToUse.substring(1);
      }

      String[] pathArray = delimitedListToStringArray(pathToUse, "/");
      Deque<String> pathElements = new ArrayDeque<>(pathArray.length);
      int tops = 0;

      for (int i = pathArray.length - 1; i >= 0; i--) {
         String element = pathArray[i];
         if (!".".equals(element)) {
            if ("..".equals(element)) {
               tops++;
            } else if (tops > 0) {
               tops--;
            } else {
               pathElements.addFirst(element);
            }
         }
      }

      if (pathArray.length == pathElements.size()) {
         return normalizedPath;
      }

      for (int i = 0; i < tops; i++) {
         pathElements.addFirst("..");
      }

      if (pathElements.size() == 1 && pathElements.getLast().isEmpty() && !prefix.endsWith("/")) {
         pathElements.addFirst(".");
      }

      String joined = collectionToDelimitedString(pathElements, "/");
      return prefix.isEmpty() ? joined : prefix + joined;
   }

   public static boolean pathEquals(String path1, String path2) {
      return cleanPath(path1).equals(cleanPath(path2));
   }

   public static String uriDecode(String source, Charset charset) {
      int length = source.length();
      int firstPercentIndex = source.indexOf(37);
      if (length != 0 && firstPercentIndex >= 0) {
         StringBuilder output = new StringBuilder(length);
         output.append(source, 0, firstPercentIndex);
         byte[] bytes = null;
         int i = firstPercentIndex;

         while (i < length) {
            char ch = source.charAt(i);
            if (ch == '%') {
               try {
                  if (bytes == null) {
                     bytes = new byte[(length - i) / 3];
                  }

                  int pos = 0;

                  while (i + 2 < length && ch == '%') {
                     bytes[pos++] = (byte)HexFormat.fromHexDigits(source, i + 1, i + 3);
                     i += 3;
                     if (i < length) {
                        ch = source.charAt(i);
                     }
                  }

                  if (i < length && ch == '%') {
                     throw new IllegalArgumentException("Incomplete trailing escape (%) pattern");
                  }

                  output.append(new String(bytes, 0, pos, charset));
               } catch (NumberFormatException ex) {
                  throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
               }
            } else {
               output.append(ch);
               i++;
            }
         }

         return output.toString();
      } else {
         return source;
      }
   }

   public static @Nullable Locale parseLocale(String localeValue) {
      if (!localeValue.contains("_") && !localeValue.contains(" ")) {
         validateLocalePart(localeValue);
         Locale resolved = Locale.forLanguageTag(localeValue);
         if (!resolved.getLanguage().isEmpty()) {
            return resolved;
         }
      }

      return parseLocaleString(localeValue);
   }

   public static @Nullable Locale parseLocaleString(String localeString) {
      if (localeString.isEmpty()) {
         return null;
      }

      String delimiter = "_";
      if (!localeString.contains("_") && localeString.contains(" ")) {
         delimiter = " ";
      }

      String[] tokens = localeString.split(delimiter, -1);
      if (tokens.length == 1) {
         String language = tokens[0];
         validateLocalePart(language);
         return new Locale(language);
      } else if (tokens.length == 2) {
         String language = tokens[0];
         validateLocalePart(language);
         String country = tokens[1];
         validateLocalePart(country);
         return new Locale(language, country);
      } else if (tokens.length > 2) {
         String language = tokens[0];
         validateLocalePart(language);
         String country = tokens[1];
         validateLocalePart(country);
         String variant = Arrays.stream(tokens).skip(2L).collect(Collectors.joining(delimiter));
         validateLocalePart(variant);
         return new Locale(language, country, variant);
      } else {
         throw new IllegalArgumentException("Invalid locale format: '" + localeString + "'");
      }
   }

   private static void validateLocalePart(String localePart) {
      for (int i = 0; i < localePart.length(); i++) {
         char ch = localePart.charAt(i);
         if (ch != ' ' && ch != '_' && ch != '-' && ch != '#' && !Character.isLetterOrDigit(ch)) {
            throw new IllegalArgumentException("Locale part \"" + localePart + "\" contains invalid characters");
         }
      }
   }

   public static TimeZone parseTimeZoneString(String timeZoneString) {
      TimeZone timeZone = TimeZone.getTimeZone(timeZoneString);
      if ("GMT".equals(timeZone.getID()) && !timeZoneString.startsWith("GMT")) {
         throw new IllegalArgumentException("Invalid time zone specification '" + timeZoneString + "'");
      } else {
         return timeZone;
      }
   }

   public static String[] toStringArray(@Nullable Collection<String> collection) {
      return !CollectionUtils.isEmpty(collection) ? collection.toArray(EMPTY_STRING_ARRAY) : EMPTY_STRING_ARRAY;
   }

   public static String[] toStringArray(@Nullable Enumeration<String> enumeration) {
      return enumeration != null ? toStringArray(Collections.list(enumeration)) : EMPTY_STRING_ARRAY;
   }

   public static String[] addStringToArray(String @Nullable [] array, String str) {
      if (ObjectUtils.isEmpty(array)) {
         return new String[]{str};
      }

      String[] newArr = new String[array.length + 1];
      System.arraycopy(array, 0, newArr, 0, array.length);
      newArr[array.length] = str;
      return newArr;
   }

   @Contract("null, _ -> param2; _, null -> param1")
   public static String @Nullable [] concatenateStringArrays(String @Nullable [] array1, String @Nullable [] array2) {
      if (ObjectUtils.isEmpty(array1)) {
         return array2;
      }

      if (ObjectUtils.isEmpty(array2)) {
         return array1;
      }

      String[] newArr = new String[array1.length + array2.length];
      System.arraycopy(array1, 0, newArr, 0, array1.length);
      System.arraycopy(array2, 0, newArr, array1.length, array2.length);
      return newArr;
   }

   public static String[] sortStringArray(String[] array) {
      if (ObjectUtils.isEmpty(array)) {
         return array;
      }

      Arrays.sort(array);
      return array;
   }

   public static String[] trimArrayElements(String[] array) {
      if (ObjectUtils.isEmpty(array)) {
         return array;
      }

      String[] result = new String[array.length];

      for (int i = 0; i < array.length; i++) {
         String element = array[i];
         result[i] = element != null ? element.trim() : null;
      }

      return result;
   }

   public static String[] removeDuplicateStrings(String[] array) {
      if (ObjectUtils.isEmpty(array)) {
         return array;
      }

      Set<String> set = new LinkedHashSet<>(Arrays.asList(array));
      return toStringArray(set);
   }

   @Contract("null, _ -> null; _, null -> null")
   public static String @Nullable [] split(@Nullable String toSplit, @Nullable String delimiter) {
      if (hasLength(toSplit) && hasLength(delimiter)) {
         int offset = toSplit.indexOf(delimiter);
         if (offset < 0) {
            return null;
         }

         String beforeDelimiter = toSplit.substring(0, offset);
         String afterDelimiter = toSplit.substring(offset + delimiter.length());
         return new String[]{beforeDelimiter, afterDelimiter};
      } else {
         return null;
      }
   }

   @Contract("null, _ -> null")
   public static @Nullable Properties splitArrayElementsIntoProperties(String @Nullable [] array, String delimiter) {
      return splitArrayElementsIntoProperties(array, delimiter, null);
   }

   @Contract("null, _, _ -> null")
   public static @Nullable Properties splitArrayElementsIntoProperties(String @Nullable [] array, String delimiter, @Nullable String charsToDelete) {
      if (ObjectUtils.isEmpty(array)) {
         return null;
      }

      Properties result = new Properties();

      for (String element : array) {
         if (charsToDelete != null) {
            element = deleteAny(element, charsToDelete);
         }

         String[] splittedElement = split(element, delimiter);
         if (splittedElement != null) {
            result.setProperty(splittedElement[0].trim(), splittedElement[1].trim());
         }
      }

      return result;
   }

   public static String[] tokenizeToStringArray(@Nullable String str, String delimiters) {
      return tokenizeToStringArray(str, delimiters, true, true);
   }

   public static String[] tokenizeToStringArray(@Nullable String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {
      if (str == null) {
         return EMPTY_STRING_ARRAY;
      }

      StringTokenizer st = new StringTokenizer(str, delimiters);
      List<String> tokens = new ArrayList<>();

      while (st.hasMoreTokens()) {
         String token = st.nextToken();
         if (trimTokens) {
            token = token.trim();
         }

         if (!ignoreEmptyTokens || !token.isEmpty()) {
            tokens.add(token);
         }
      }

      return toStringArray(tokens);
   }

   public static String[] delimitedListToStringArray(@Nullable String str, @Nullable String delimiter) {
      return delimitedListToStringArray(str, delimiter, null);
   }

   public static String[] delimitedListToStringArray(@Nullable String str, @Nullable String delimiter, @Nullable String charsToDelete) {
      if (str == null) {
         return EMPTY_STRING_ARRAY;
      }

      if (delimiter == null) {
         return new String[]{str};
      }

      List<String> result = new ArrayList<>();
      if (delimiter.isEmpty()) {
         for (int i = 0; i < str.length(); i++) {
            result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
         }
      } else {
         int pos = 0;

         int delPos;
         while ((delPos = str.indexOf(delimiter, pos)) != -1) {
            result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
            pos = delPos + delimiter.length();
         }

         if (!str.isEmpty() && pos <= str.length()) {
            result.add(deleteAny(str.substring(pos), charsToDelete));
         }
      }

      return toStringArray(result);
   }

   public static String[] commaDelimitedListToStringArray(@Nullable String str) {
      return delimitedListToStringArray(str, ",");
   }

   public static Set<String> commaDelimitedListToSet(@Nullable String str) {
      String[] tokens = commaDelimitedListToStringArray(str);
      return new LinkedHashSet<>(Arrays.asList(tokens));
   }

   public static String collectionToDelimitedString(@Nullable Collection<?> coll, String delim, String prefix, String suffix) {
      if (CollectionUtils.isEmpty((Collection<? extends Object>)coll)) {
         return "";
      }

      int totalLength = coll.size() * (prefix.length() + suffix.length()) + (coll.size() - 1) * delim.length();

      for (Object element : coll) {
         totalLength += String.valueOf(element).length();
      }

      StringBuilder sb = new StringBuilder(totalLength);
      Iterator<?> it = coll.iterator();

      while (it.hasNext()) {
         sb.append(prefix).append(it.next()).append(suffix);
         if (it.hasNext()) {
            sb.append(delim);
         }
      }

      return sb.toString();
   }

   public static String collectionToDelimitedString(@Nullable Collection<?> coll, String delim) {
      return collectionToDelimitedString(coll, delim, "", "");
   }

   public static String collectionToCommaDelimitedString(@Nullable Collection<?> coll) {
      return collectionToDelimitedString(coll, ",");
   }

   public static String arrayToDelimitedString(@Nullable Object @Nullable [] arr, String delim) {
      if (ObjectUtils.isEmpty(arr)) {
         return "";
      }

      if (arr.length == 1) {
         return ObjectUtils.nullSafeToString(arr[0]);
      }

      StringJoiner sj = new StringJoiner(delim);

      for (Object elem : arr) {
         sj.add(String.valueOf(elem));
      }

      return sj.toString();
   }

   public static String arrayToCommaDelimitedString(@Nullable Object @Nullable [] arr) {
      return arrayToDelimitedString(arr, ",");
   }

   public static String truncate(CharSequence charSequence) {
      return truncate(charSequence, 100);
   }

   public static String truncate(CharSequence charSequence, int threshold) {
      Assert.isTrue(threshold > 0, () -> "Truncation threshold must be a positive number: " + threshold);
      return charSequence.length() > threshold ? charSequence.subSequence(0, threshold) + " (truncated)..." : charSequence.toString();
   }
}
