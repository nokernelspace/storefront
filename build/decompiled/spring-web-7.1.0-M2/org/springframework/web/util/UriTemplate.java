package org.springframework.web.util;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class UriTemplate implements Serializable {
   private final String uriTemplate;
   private final UriComponents uriComponents;
   private final List<String> variableNames;
   private final Pattern matchPattern;

   public UriTemplate(String uriTemplate) {
      Assert.notNull(uriTemplate, "'uriTemplate' must not be null");
      this.uriTemplate = uriTemplate;
      this.uriComponents = UriComponentsBuilder.fromUriString(uriTemplate).build();
      UriTemplate.TemplateInfo info = UriTemplate.TemplateInfo.parse(uriTemplate);
      this.variableNames = Collections.unmodifiableList(info.getVariableNames());
      this.matchPattern = info.getMatchPattern();
   }

   public List<String> getVariableNames() {
      return this.variableNames;
   }

   public URI expand(Map<String, ?> uriVariables) {
      UriComponents expandedComponents = this.uriComponents.expand((Map<String, ? extends Object>)uriVariables);
      UriComponents encodedComponents = expandedComponents.encode();
      return encodedComponents.toUri();
   }

   public URI expand(Object... uriVariableValues) {
      UriComponents expandedComponents = this.uriComponents.expand(uriVariableValues);
      UriComponents encodedComponents = expandedComponents.encode();
      return encodedComponents.toUri();
   }

   public boolean matches(@Nullable String uri) {
      if (uri == null) {
         return false;
      }

      Matcher matcher = this.matchPattern.matcher(uri);
      return matcher.matches();
   }

   public Map<String, String> match(String uri) {
      Assert.notNull(uri, "'uri' must not be null");
      Map<String, String> result = CollectionUtils.newLinkedHashMap(this.variableNames.size());
      Matcher matcher = this.matchPattern.matcher(uri);
      if (matcher.find()) {
         for (int i = 1; i <= matcher.groupCount(); i++) {
            String name = this.variableNames.get(i - 1);
            String value = matcher.group(i);
            result.put(name, value);
         }
      }

      return result;
   }

   @Override
   public String toString() {
      return this.uriTemplate;
   }

   private static final class TemplateInfo {
      private final List<String> variableNames;
      private final Pattern pattern;

      private TemplateInfo(List<String> vars, Pattern pattern) {
         this.variableNames = vars;
         this.pattern = pattern;
      }

      public List<String> getVariableNames() {
         return this.variableNames;
      }

      public Pattern getMatchPattern() {
         return this.pattern;
      }

      public static UriTemplate.TemplateInfo parse(String uriTemplate) {
         int level = 0;
         List<String> variableNames = new ArrayList<>();
         StringBuilder pattern = new StringBuilder();
         StringBuilder builder = new StringBuilder();

         for (int i = 0; i < uriTemplate.length(); i++) {
            char c = uriTemplate.charAt(i);
            if (c == '{') {
               if (++level == 1) {
                  pattern.append(quote(builder));
                  builder = new StringBuilder();
                  continue;
               }
            } else if (c == '}') {
               if (--level == 0) {
                  String variable = builder.toString();
                  int idx = variable.indexOf(58);
                  if (idx == -1) {
                     pattern.append("([^/]*)");
                     variableNames.add(variable);
                  } else {
                     if (idx + 1 == variable.length()) {
                        throw new IllegalArgumentException("No custom regular expression specified after ':' in \"" + variable + "\"");
                     }

                     String regex = variable.substring(idx + 1);
                     pattern.append('(');
                     pattern.append(regex);
                     pattern.append(')');
                     variableNames.add(variable.substring(0, idx));
                  }

                  builder = new StringBuilder();
                  continue;
               }
            }

            builder.append(c);
         }

         if (builder.length() > 0) {
            pattern.append(quote(builder));
         }

         return new UriTemplate.TemplateInfo(variableNames, Pattern.compile(pattern.toString()));
      }

      private static String quote(StringBuilder builder) {
         return builder.length() > 0 ? Pattern.quote(builder.toString()) : "";
      }
   }
}
