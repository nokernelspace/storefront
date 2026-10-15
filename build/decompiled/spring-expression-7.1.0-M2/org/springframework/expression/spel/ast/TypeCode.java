package org.springframework.expression.spel.ast;

public enum TypeCode {
   OBJECT(Object.class),
   BOOLEAN(boolean.class),
   CHAR(char.class),
   BYTE(byte.class),
   SHORT(short.class),
   INT(int.class),
   LONG(long.class),
   FLOAT(float.class),
   DOUBLE(double.class);

   private final Class<?> type;

   TypeCode(Class<?> type) {
      this.type = type;
   }

   public Class<?> getType() {
      return this.type;
   }

   public static TypeCode forName(String name) {
      for (TypeCode typeCode : values()) {
         if (typeCode.name().equalsIgnoreCase(name)) {
            return typeCode;
         }
      }

      return OBJECT;
   }

   public static TypeCode forClass(Class<?> clazz) {
      for (TypeCode typeCode : values()) {
         if (typeCode.getType() == clazz) {
            return typeCode;
         }
      }

      return OBJECT;
   }
}
