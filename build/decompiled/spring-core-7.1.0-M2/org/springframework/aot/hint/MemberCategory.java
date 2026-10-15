package org.springframework.aot.hint;

public enum MemberCategory {
   @Deprecated(since = "7.0", forRemoval = true)
   PUBLIC_FIELDS,
   @Deprecated(since = "7.0", forRemoval = true)
   DECLARED_FIELDS,
   ACCESS_PUBLIC_FIELDS,
   ACCESS_DECLARED_FIELDS,
   @Deprecated(since = "7.0", forRemoval = true)
   INTROSPECT_PUBLIC_CONSTRUCTORS,
   @Deprecated(since = "7.0", forRemoval = true)
   INTROSPECT_DECLARED_CONSTRUCTORS,
   INVOKE_PUBLIC_CONSTRUCTORS,
   INVOKE_DECLARED_CONSTRUCTORS,
   @Deprecated(since = "7.0", forRemoval = true)
   INTROSPECT_PUBLIC_METHODS,
   @Deprecated(since = "7.0", forRemoval = true)
   INTROSPECT_DECLARED_METHODS,
   INVOKE_PUBLIC_METHODS,
   INVOKE_DECLARED_METHODS,
   @Deprecated(since = "7.0", forRemoval = true)
   PUBLIC_CLASSES,
   @Deprecated(since = "7.0", forRemoval = true)
   DECLARED_CLASSES,
   UNSAFE_ALLOCATED;
}
