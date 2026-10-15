package org.springframework.boot.context.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

public class Profiles implements Iterable<String> {
   public static final String INCLUDE_PROFILES_PROPERTY_NAME = "spring.profiles.include";
   static final ConfigurationPropertyName INCLUDE_PROFILES = ConfigurationPropertyName.of("spring.profiles.include");
   private static final Bindable<MultiValueMap<String, String>> STRING_STRINGS_MAP = Bindable.of(
      ResolvableType.forClassWithGenerics(MultiValueMap.class, new Class[]{String.class, String.class})
   );
   private static final Bindable<Set<String>> STRING_SET = Bindable.setOf(String.class);
   private final MultiValueMap<String, String> groups;
   private final List<String> activeProfiles;
   private final List<String> defaultProfiles;

   Profiles(Environment environment, Binder binder, @Nullable Collection<String> additionalProfiles) {
      ProfilesValidator validator = ProfilesValidator.get(binder);
      if (additionalProfiles != null) {
         validator.validate(additionalProfiles, () -> "Invalid profile property value found in additional profiles");
      }

      this.groups = binder.bind("spring.profiles.group", STRING_STRINGS_MAP, validator).orElseGet(LinkedMultiValueMap::new);
      this.activeProfiles = this.expandProfiles(this.getActivatedProfiles(environment, binder, validator, additionalProfiles));
      this.defaultProfiles = this.expandProfiles(this.getDefaultProfiles(environment, binder, validator));
   }

   private List<String> getActivatedProfiles(
      Environment environment, Binder binder, ProfilesValidator validator, @Nullable Collection<String> additionalProfiles
   ) {
      return this.asUniqueItemList(this.getProfiles(environment, binder, validator, Profiles.Type.ACTIVE), additionalProfiles);
   }

   private List<String> getDefaultProfiles(Environment environment, Binder binder, ProfilesValidator validator) {
      return this.asUniqueItemList(this.getProfiles(environment, binder, validator, Profiles.Type.DEFAULT));
   }

   private Collection<String> getProfiles(Environment environment, Binder binder, ProfilesValidator validator, Profiles.Type type) {
      String environmentPropertyValue = environment.getProperty(type.getName());
      Set<String> environmentPropertyProfiles = !StringUtils.hasLength(environmentPropertyValue)
         ? Collections.emptySet()
         : StringUtils.commaDelimitedListToSet(StringUtils.trimAllWhitespace(environmentPropertyValue));
      validator.validate(environmentPropertyProfiles, () -> "Invalid profile property value found in Environment under '%s'".formatted(type.getName()));
      Set<String> environmentProfiles = new LinkedHashSet<>(Arrays.asList(type.get(environment)));
      BindResult<Set<String>> boundProfiles = binder.bind(type.getName(), STRING_SET, validator);
      if (this.hasProgrammaticallySetProfiles(type, environmentPropertyValue, environmentPropertyProfiles, environmentProfiles)) {
         return type.isMergeWithEnvironmentProfiles() && boundProfiles.isBound()
            ? boundProfiles.<Collection<String>>map(bound -> this.merge(environmentProfiles, (Set<String>)bound)).get()
            : environmentProfiles;
      } else {
         return boundProfiles.orElse(type.getDefaultValue());
      }
   }

   private boolean hasProgrammaticallySetProfiles(
      Profiles.Type type, @Nullable String environmentPropertyValue, Set<String> environmentPropertyProfiles, Set<String> environmentProfiles
   ) {
      if (!StringUtils.hasLength(environmentPropertyValue)) {
         return !type.getDefaultValue().equals(environmentProfiles);
      } else {
         return type.getDefaultValue().equals(environmentProfiles) ? false : !environmentPropertyProfiles.equals(environmentProfiles);
      }
   }

   private Set<String> merge(Set<String> environmentProfiles, Set<String> bound) {
      Set<String> result = new LinkedHashSet<>(environmentProfiles);
      result.addAll(bound);
      return result;
   }

   private List<String> expandProfiles(@Nullable List<String> profiles) {
      Deque<String> stack = new ArrayDeque<>();
      this.asReversedList(profiles).forEach(stack::push);
      Set<String> expandedProfiles = new LinkedHashSet<>();

      while (!stack.isEmpty()) {
         String current = stack.pop();
         if (expandedProfiles.add(current)) {
            this.asReversedList((List<String>)this.groups.get(current)).forEach(stack::push);
         }
      }

      return this.asUniqueItemList(expandedProfiles);
   }

   private List<String> asReversedList(@Nullable List<String> list) {
      if (CollectionUtils.isEmpty(list)) {
         return Collections.emptyList();
      }

      List<String> reversed = new ArrayList<>(list);
      Collections.reverse(reversed);
      return reversed;
   }

   private List<String> asUniqueItemList(Collection<String> profiles) {
      return this.asUniqueItemList(profiles, null);
   }

   private List<String> asUniqueItemList(Collection<String> profiles, @Nullable Collection<String> additional) {
      LinkedHashSet<String> uniqueItems = new LinkedHashSet<>();
      if (!CollectionUtils.isEmpty(additional)) {
         uniqueItems.addAll(additional);
      }

      uniqueItems.addAll(profiles);
      return Collections.unmodifiableList(new ArrayList<>(uniqueItems));
   }

   @Override
   public Iterator<String> iterator() {
      return this.getAccepted().iterator();
   }

   public List<String> getActive() {
      return this.activeProfiles;
   }

   public List<String> getDefault() {
      return this.defaultProfiles;
   }

   public List<String> getAccepted() {
      return !this.activeProfiles.isEmpty() ? this.activeProfiles : this.defaultProfiles;
   }

   public boolean isAccepted(String profile) {
      return this.getAccepted().contains(profile);
   }

   @Override
   public String toString() {
      ToStringCreator creator = new ToStringCreator(this);
      creator.append("active", this.getActive().toString());
      creator.append("default", this.getDefault().toString());
      creator.append("accepted", this.getAccepted().toString());
      return creator.toString();
   }

   private enum Type {
      ACTIVE("spring.profiles.active", Environment::getActiveProfiles, true, Collections.emptySet()),
      DEFAULT("spring.profiles.default", Environment::getDefaultProfiles, false, Collections.singleton("default"));

      private final Function<Environment, String[]> getter;
      private final boolean mergeWithEnvironmentProfiles;
      private final String name;
      private final Set<String> defaultValue;

      Type(String name, Function<Environment, String[]> getter, boolean mergeWithEnvironmentProfiles, Set<String> defaultValue) {
         this.name = name;
         this.getter = getter;
         this.mergeWithEnvironmentProfiles = mergeWithEnvironmentProfiles;
         this.defaultValue = defaultValue;
      }

      String getName() {
         return this.name;
      }

      String[] get(Environment environment) {
         return this.getter.apply(environment);
      }

      Set<String> getDefaultValue() {
         return this.defaultValue;
      }

      boolean isMergeWithEnvironmentProfiles() {
         return this.mergeWithEnvironmentProfiles;
      }
   }
}
