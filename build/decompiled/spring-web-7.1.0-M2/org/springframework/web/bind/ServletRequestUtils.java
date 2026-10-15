package org.springframework.web.bind;

import jakarta.servlet.ServletRequest;
import org.jspecify.annotations.Nullable;

public abstract class ServletRequestUtils {
   private static final ServletRequestUtils.IntParser INT_PARSER = new ServletRequestUtils.IntParser();
   private static final ServletRequestUtils.LongParser LONG_PARSER = new ServletRequestUtils.LongParser();
   private static final ServletRequestUtils.FloatParser FLOAT_PARSER = new ServletRequestUtils.FloatParser();
   private static final ServletRequestUtils.DoubleParser DOUBLE_PARSER = new ServletRequestUtils.DoubleParser();
   private static final ServletRequestUtils.BooleanParser BOOLEAN_PARSER = new ServletRequestUtils.BooleanParser();
   private static final ServletRequestUtils.StringParser STRING_PARSER = new ServletRequestUtils.StringParser();

   public static @Nullable Integer getIntParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return request.getParameter(name) == null ? null : getRequiredIntParameter(request, name);
   }

   public static int getIntParameter(ServletRequest request, String name, int defaultVal) {
      if (request.getParameter(name) == null) {
         return defaultVal;
      }

      try {
         return getRequiredIntParameter(request, name);
      } catch (ServletRequestBindingException ex) {
         return defaultVal;
      }
   }

   public static int[] getIntParameters(ServletRequest request, String name) {
      try {
         return getRequiredIntParameters(request, name);
      } catch (ServletRequestBindingException ex) {
         return new int[0];
      }
   }

   public static int getRequiredIntParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return INT_PARSER.parseInt(name, request.getParameter(name));
   }

   public static int[] getRequiredIntParameters(ServletRequest request, String name) throws ServletRequestBindingException {
      return INT_PARSER.parseInts(name, request.getParameterValues(name));
   }

   public static @Nullable Long getLongParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return request.getParameter(name) == null ? null : getRequiredLongParameter(request, name);
   }

   public static long getLongParameter(ServletRequest request, String name, long defaultVal) {
      if (request.getParameter(name) == null) {
         return defaultVal;
      }

      try {
         return getRequiredLongParameter(request, name);
      } catch (ServletRequestBindingException ex) {
         return defaultVal;
      }
   }

   public static long[] getLongParameters(ServletRequest request, String name) {
      try {
         return getRequiredLongParameters(request, name);
      } catch (ServletRequestBindingException ex) {
         return new long[0];
      }
   }

   public static long getRequiredLongParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return LONG_PARSER.parseLong(name, request.getParameter(name));
   }

   public static long[] getRequiredLongParameters(ServletRequest request, String name) throws ServletRequestBindingException {
      return LONG_PARSER.parseLongs(name, request.getParameterValues(name));
   }

   public static @Nullable Float getFloatParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return request.getParameter(name) == null ? null : getRequiredFloatParameter(request, name);
   }

   public static float getFloatParameter(ServletRequest request, String name, float defaultVal) {
      if (request.getParameter(name) == null) {
         return defaultVal;
      }

      try {
         return getRequiredFloatParameter(request, name);
      } catch (ServletRequestBindingException ex) {
         return defaultVal;
      }
   }

   public static float[] getFloatParameters(ServletRequest request, String name) {
      try {
         return getRequiredFloatParameters(request, name);
      } catch (ServletRequestBindingException ex) {
         return new float[0];
      }
   }

   public static float getRequiredFloatParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return FLOAT_PARSER.parseFloat(name, request.getParameter(name));
   }

   public static float[] getRequiredFloatParameters(ServletRequest request, String name) throws ServletRequestBindingException {
      return FLOAT_PARSER.parseFloats(name, request.getParameterValues(name));
   }

   public static @Nullable Double getDoubleParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return request.getParameter(name) == null ? null : getRequiredDoubleParameter(request, name);
   }

   public static double getDoubleParameter(ServletRequest request, String name, double defaultVal) {
      if (request.getParameter(name) == null) {
         return defaultVal;
      }

      try {
         return getRequiredDoubleParameter(request, name);
      } catch (ServletRequestBindingException ex) {
         return defaultVal;
      }
   }

   public static double[] getDoubleParameters(ServletRequest request, String name) {
      try {
         return getRequiredDoubleParameters(request, name);
      } catch (ServletRequestBindingException ex) {
         return new double[0];
      }
   }

   public static double getRequiredDoubleParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return DOUBLE_PARSER.parseDouble(name, request.getParameter(name));
   }

   public static double[] getRequiredDoubleParameters(ServletRequest request, String name) throws ServletRequestBindingException {
      return DOUBLE_PARSER.parseDoubles(name, request.getParameterValues(name));
   }

   public static @Nullable Boolean getBooleanParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return request.getParameter(name) == null ? null : getRequiredBooleanParameter(request, name);
   }

   public static boolean getBooleanParameter(ServletRequest request, String name, boolean defaultVal) {
      if (request.getParameter(name) == null) {
         return defaultVal;
      }

      try {
         return getRequiredBooleanParameter(request, name);
      } catch (ServletRequestBindingException ex) {
         return defaultVal;
      }
   }

   public static boolean[] getBooleanParameters(ServletRequest request, String name) {
      try {
         return getRequiredBooleanParameters(request, name);
      } catch (ServletRequestBindingException ex) {
         return new boolean[0];
      }
   }

   public static boolean getRequiredBooleanParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return BOOLEAN_PARSER.parseBoolean(name, request.getParameter(name));
   }

   public static boolean[] getRequiredBooleanParameters(ServletRequest request, String name) throws ServletRequestBindingException {
      return BOOLEAN_PARSER.parseBooleans(name, request.getParameterValues(name));
   }

   public static @Nullable String getStringParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return request.getParameter(name) == null ? null : getRequiredStringParameter(request, name);
   }

   public static String getStringParameter(ServletRequest request, String name, String defaultVal) {
      String val = request.getParameter(name);
      return val != null ? val : defaultVal;
   }

   public static String[] getStringParameters(ServletRequest request, String name) {
      try {
         return getRequiredStringParameters(request, name);
      } catch (ServletRequestBindingException ex) {
         return new String[0];
      }
   }

   public static String getRequiredStringParameter(ServletRequest request, String name) throws ServletRequestBindingException {
      return STRING_PARSER.validateRequiredString(name, request.getParameter(name));
   }

   public static String[] getRequiredStringParameters(ServletRequest request, String name) throws ServletRequestBindingException {
      return STRING_PARSER.validateRequiredStrings(name, request.getParameterValues(name));
   }

   private static class BooleanParser extends ServletRequestUtils.ParameterParser<Boolean> {
      @Override
      protected String getType() {
         return "boolean";
      }

      protected Boolean doParse(String parameter) throws NumberFormatException {
         return parameter.equalsIgnoreCase("true") || parameter.equalsIgnoreCase("on") || parameter.equalsIgnoreCase("yes") || parameter.equals("1");
      }

      public boolean parseBoolean(String name, String parameter) throws ServletRequestBindingException {
         return this.parse(name, parameter);
      }

      public boolean[] parseBooleans(String name, String[] values) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, values);
         boolean[] parameters = new boolean[values.length];

         for (int i = 0; i < values.length; i++) {
            parameters[i] = this.parseBoolean(name, values[i]);
         }

         return parameters;
      }
   }

   private static class DoubleParser extends ServletRequestUtils.ParameterParser<Double> {
      @Override
      protected String getType() {
         return "double";
      }

      protected Double doParse(String parameter) throws NumberFormatException {
         return Double.valueOf(parameter);
      }

      public double parseDouble(String name, String parameter) throws ServletRequestBindingException {
         return this.parse(name, parameter);
      }

      public double[] parseDoubles(String name, String[] values) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, values);
         double[] parameters = new double[values.length];

         for (int i = 0; i < values.length; i++) {
            parameters[i] = this.parseDouble(name, values[i]);
         }

         return parameters;
      }
   }

   private static class FloatParser extends ServletRequestUtils.ParameterParser<Float> {
      @Override
      protected String getType() {
         return "float";
      }

      protected Float doParse(String parameter) throws NumberFormatException {
         return Float.valueOf(parameter);
      }

      public float parseFloat(String name, String parameter) throws ServletRequestBindingException {
         return this.parse(name, parameter);
      }

      public float[] parseFloats(String name, String[] values) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, values);
         float[] parameters = new float[values.length];

         for (int i = 0; i < values.length; i++) {
            parameters[i] = this.parseFloat(name, values[i]);
         }

         return parameters;
      }
   }

   private static class IntParser extends ServletRequestUtils.ParameterParser<Integer> {
      @Override
      protected String getType() {
         return "int";
      }

      protected Integer doParse(String s) throws NumberFormatException {
         return Integer.valueOf(s);
      }

      public int parseInt(String name, String parameter) throws ServletRequestBindingException {
         return this.parse(name, parameter);
      }

      public int[] parseInts(String name, String[] values) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, values);
         int[] parameters = new int[values.length];

         for (int i = 0; i < values.length; i++) {
            parameters[i] = this.parseInt(name, values[i]);
         }

         return parameters;
      }
   }

   private static class LongParser extends ServletRequestUtils.ParameterParser<Long> {
      @Override
      protected String getType() {
         return "long";
      }

      protected Long doParse(String parameter) throws NumberFormatException {
         return Long.valueOf(parameter);
      }

      public long parseLong(String name, String parameter) throws ServletRequestBindingException {
         return this.parse(name, parameter);
      }

      public long[] parseLongs(String name, String[] values) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, values);
         long[] parameters = new long[values.length];

         for (int i = 0; i < values.length; i++) {
            parameters[i] = this.parseLong(name, values[i]);
         }

         return parameters;
      }
   }

   private abstract static class ParameterParser<T> {
      protected final T parse(String name, String parameter) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, parameter);

         try {
            return this.doParse(parameter);
         } catch (NumberFormatException ex) {
            throw new ServletRequestBindingException(
               "Required " + this.getType() + " parameter '" + name + "' with value of '" + parameter + "' is not a valid number", ex
            );
         }
      }

      protected final void validateRequiredParameter(String name, @Nullable Object parameter) throws ServletRequestBindingException {
         if (parameter == null) {
            throw new MissingServletRequestParameterException(name, this.getType());
         }
      }

      protected abstract String getType();

      protected abstract T doParse(String parameter) throws NumberFormatException;
   }

   private static class StringParser extends ServletRequestUtils.ParameterParser<String> {
      @Override
      protected String getType() {
         return "string";
      }

      protected String doParse(String parameter) throws NumberFormatException {
         return parameter;
      }

      public String validateRequiredString(String name, String value) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, value);
         return value;
      }

      public String[] validateRequiredStrings(String name, String[] values) throws ServletRequestBindingException {
         this.validateRequiredParameter(name, values);

         for (String value : values) {
            this.validateRequiredParameter(name, value);
         }

         return values;
      }
   }
}
