package org.springframework.web.service.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

final class GroupsMetadata {
   private final Map<String, GroupsMetadata.Registration> groupMap = new LinkedHashMap<>();

   public GroupsMetadata() {
      this(Collections.emptyList());
   }

   GroupsMetadata(Iterable<GroupsMetadata.Registration> registrations) {
      registrations.forEach(registration -> this.groupMap.put(registration.name(), registration));
   }

   public GroupsMetadata.Registration getOrCreateGroup(String groupName, HttpServiceGroup.ClientType clientType) {
      return this.groupMap.computeIfAbsent(groupName, name -> new GroupsMetadata.Registration(name, clientType)).clientType(clientType);
   }

   public void mergeWith(GroupsMetadata other) {
      other.groupMap
         .values()
         .forEach(
            registration -> this.getOrCreateGroup(registration.name(), registration.clientType())
               .httpServiceTypeNames()
               .addAll(registration.httpServiceTypeNames())
         );
   }

   public void forEachRegistration(BiConsumer<String, Set<String>> consumer) {
      this.groupMap.values().forEach(registration -> consumer.accept(registration.name(), registration.httpServiceTypeNames()));
   }

   public Collection<HttpServiceGroup> groups(@Nullable ClassLoader classLoader) {
      return this.groupMap.values().stream().map(registration -> registration.toHttpServiceGroup(classLoader)).toList();
   }

   Stream<GroupsMetadata.Registration> registrations() {
      return this.groupMap.values().stream();
   }

   boolean isEmpty() {
      return this.groupMap.isEmpty();
   }

   private record RegisteredGroup(String name, HttpServiceGroup.ClientType clientType, Set<Class<?>> httpServiceTypes) implements HttpServiceGroup {
   }

   static class Registration {
      private final String name;
      private HttpServiceGroup.ClientType clientType;
      private final Set<String> httpServiceTypeNames;

      Registration(String name, HttpServiceGroup.ClientType clientType) {
         this(name, clientType, new LinkedHashSet<>());
      }

      Registration(String name, HttpServiceGroup.ClientType clientType, Set<String> httpServiceTypeNames) {
         this.name = name;
         this.clientType = clientType;
         this.httpServiceTypeNames = httpServiceTypeNames;
      }

      String name() {
         return this.name;
      }

      HttpServiceGroup.ClientType clientType() {
         return this.clientType;
      }

      Set<String> httpServiceTypeNames() {
         return this.httpServiceTypeNames;
      }

      public GroupsMetadata.Registration clientType(HttpServiceGroup.ClientType other) {
         this.clientType = this.clientType.isUnspecified() ? other : this.clientType;
         Assert.isTrue(this.clientType == other || other.isUnspecified(), "ClientType conflict for HttpServiceGroup '" + this.name + "'");
         return this;
      }

      public HttpServiceGroup toHttpServiceGroup(@Nullable ClassLoader classLoader) {
         HttpServiceGroup.ClientType clientTypeToUse = this.clientType.isUnspecified() ? HttpServiceGroup.ClientType.REST_CLIENT : this.clientType;
         Set<Class<?>> httpServiceTypes = this.httpServiceTypeNames
            .stream()
            .map(typeName -> ClassUtils.resolveClassName(typeName, classLoader))
            .collect(Collectors.toSet());
         return new GroupsMetadata.RegisteredGroup(this.name, clientTypeToUse, httpServiceTypes);
      }

      @Override
      public String toString() {
         return "Group '" + this.name + "', ClientType." + this.clientType + ", " + this.httpServiceTypeNames;
      }
   }
}
