package org.springframework.web.service.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.ValueCodeGenerator;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.aot.generate.ValueCodeGenerator.Delegate;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;

final class GroupsMetadataValueDelegate implements Delegate {
   public @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value) {
      if (value instanceof GroupsMetadata.Registration registration) {
         return this.generateRegistrationCode(valueCodeGenerator, registration);
      } else {
         return value instanceof GroupsMetadata groupsMetadata ? this.generateGroupsMetadataCode(valueCodeGenerator, groupsMetadata) : null;
      }
   }

   public CodeBlock generateRegistrationCode(ValueCodeGenerator valueCodeGenerator, GroupsMetadata.Registration value) {
      Builder code = CodeBlock.builder();
      code.add(
         "new $T($S, $L, $L)",
         new Object[]{
            GroupsMetadata.Registration.class,
            value.name(),
            valueCodeGenerator.generateCode(value.clientType()),
            !value.httpServiceTypeNames().isEmpty()
               ? valueCodeGenerator.generateCode(value.httpServiceTypeNames())
               : CodeBlock.of("new $T()", new Object[]{LinkedHashSet.class})
         }
      );
      return code.build();
   }

   private CodeBlock generateGroupsMetadataCode(ValueCodeGenerator valueCodeGenerator, GroupsMetadata groupsMetadata) {
      Collection<GroupsMetadata.Registration> registrations = groupsMetadata.registrations().collect(Collectors.toCollection(ArrayList::new));
      return valueCodeGenerator.getGeneratedMethods() != null
         ? valueCodeGenerator.getGeneratedMethods()
            .add(
               "getGroupsMetadata",
               method -> method.addJavadoc("Create the {@link $T}.", new Object[]{GroupsMetadata.class})
                  .addModifiers(new Modifier[]{Modifier.PRIVATE, Modifier.STATIC})
                  .returns(GroupsMetadata.class)
                  .addCode(this.generateGroupsMetadataMethod(valueCodeGenerator, registrations))
            )
            .toMethodReference()
            .toInvokeCodeBlock(ArgumentCodeGenerator.none())
         : CodeBlock.of("new $T($L)", new Object[]{GroupsMetadata.class, valueCodeGenerator.generateCode(registrations)});
   }

   private CodeBlock generateGroupsMetadataMethod(ValueCodeGenerator valueCodeGenerator, Collection<GroupsMetadata.Registration> registrations) {
      Builder code = CodeBlock.builder();
      String registrationsVariable = "registrations";
      code.addStatement("$T<$T> $L = new $T<>()", new Object[]{List.class, GroupsMetadata.Registration.class, registrationsVariable, ArrayList.class});
      registrations.forEach(registration -> code.addStatement("$L.add($L)", new Object[]{registrationsVariable, valueCodeGenerator.generateCode(registration)}));
      code.addStatement("return new $T($L)", new Object[]{GroupsMetadata.class, registrationsVariable});
      return code.build();
   }
}
