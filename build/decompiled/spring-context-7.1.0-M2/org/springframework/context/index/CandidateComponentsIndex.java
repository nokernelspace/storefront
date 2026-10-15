package org.springframework.context.index;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class CandidateComponentsIndex {
   private static final AntPathMatcher pathMatcher = new AntPathMatcher(".");
   private final Set<String> registeredScans = new LinkedHashSet<>();
   private final MultiValueMap<String, CandidateComponentsIndex.Entry> index = new LinkedMultiValueMap();
   private final boolean complete;

   CandidateComponentsIndex(List<Properties> content) {
      for (Properties entry : content) {
         entry.forEach((type, values) -> {
            String[] stereotypes = ((String)values).split(",");

            for (String stereotype : stereotypes) {
               this.index.add(stereotype, new CandidateComponentsIndex.Entry((String)type));
            }
         });
      }

      this.complete = true;
   }

   public CandidateComponentsIndex() {
      this.complete = false;
   }

   public void registerScan(String... basePackages) {
      Collections.addAll(this.registeredScans, basePackages);
   }

   public Set<String> getRegisteredScans() {
      return this.registeredScans;
   }

   public boolean hasScannedPackage(String packageName) {
      return this.complete || this.registeredScans.stream().anyMatch(basePackage -> matchPackage(basePackage, packageName));
   }

   public void registerCandidateType(String type, String... stereotypes) {
      for (String stereotype : stereotypes) {
         this.index.add(stereotype, new CandidateComponentsIndex.Entry(type));
      }
   }

   public Set<String> getRegisteredStereotypes() {
      return this.index.keySet();
   }

   public Set<String> getCandidateTypes(String basePackage, String stereotype) {
      List<CandidateComponentsIndex.Entry> candidates = (List<CandidateComponentsIndex.Entry>)this.index.get(stereotype);
      return candidates != null
         ? candidates.stream().filter(entry -> entry.match(basePackage)).map(entry -> entry.type).collect(Collectors.toSet())
         : Collections.emptySet();
   }

   private static boolean matchPackage(String basePackage, String packageName) {
      return pathMatcher.isPattern(basePackage)
         ? pathMatcher.match(basePackage, packageName)
         : packageName.equals(basePackage) || packageName.startsWith(basePackage + ".");
   }

   private static class Entry {
      final String type;
      private final String packageName;

      Entry(String type) {
         this.type = type;
         this.packageName = ClassUtils.getPackageName(type);
      }

      public boolean match(String basePackage) {
         return CandidateComponentsIndex.matchPackage(basePackage, this.packageName);
      }
   }
}
