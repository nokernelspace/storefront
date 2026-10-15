package com.google.gson;

import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.Primitives;
import com.google.gson.internal.Streams;
import com.google.gson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory;
import com.google.gson.internal.bind.JsonTreeReader;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.internal.bind.SerializationDelegatingTypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Gson {
   private static final String JSON_NON_EXECUTABLE_PREFIX = ")]}'\n";
   private final ThreadLocal<Map<TypeToken<?>, TypeAdapter<?>>> threadLocalAdapterResults = new ThreadLocal<>();
   private final ConcurrentMap<TypeToken<?>, TypeAdapter<?>> typeTokenCache = new ConcurrentHashMap<>();
   private final ConstructorConstructor constructorConstructor;
   private final JsonAdapterAnnotationTypeAdapterFactory jsonAdapterFactory;
   final List<TypeAdapterFactory> factories;
   final Excluder excluder;
   final FieldNamingStrategy fieldNamingStrategy;
   final Map<Type, InstanceCreator<?>> instanceCreators;
   final boolean serializeNulls;
   final boolean complexMapKeySerialization;
   final boolean generateNonExecutableJson;
   final boolean htmlSafe;
   final FormattingStyle formattingStyle;
   final Strictness strictness;
   final boolean serializeSpecialFloatingPointValues;
   final boolean useJdkUnsafe;
   final String datePattern;
   final int dateStyle;
   final int timeStyle;
   final LongSerializationPolicy longSerializationPolicy;
   final List<TypeAdapterFactory> builderFactories;
   final List<TypeAdapterFactory> builderHierarchyFactories;
   final ToNumberStrategy objectToNumberStrategy;
   final ToNumberStrategy numberToNumberStrategy;
   final List<ReflectionAccessFilter> reflectionFilters;

   public Gson() {
      this(GsonBuilder.DEFAULT);
   }

   Gson(GsonBuilder builder) {
      this.excluder = builder.excluder;
      this.fieldNamingStrategy = builder.fieldNamingPolicy;
      this.instanceCreators = new HashMap<>(builder.instanceCreators);
      this.serializeNulls = builder.serializeNulls;
      this.complexMapKeySerialization = builder.complexMapKeySerialization;
      this.generateNonExecutableJson = builder.generateNonExecutableJson;
      this.htmlSafe = builder.escapeHtmlChars;
      this.formattingStyle = builder.formattingStyle;
      this.strictness = builder.strictness;
      this.serializeSpecialFloatingPointValues = builder.serializeSpecialFloatingPointValues;
      this.useJdkUnsafe = builder.useJdkUnsafe;
      this.longSerializationPolicy = builder.longSerializationPolicy;
      this.datePattern = builder.datePattern;
      this.dateStyle = builder.dateStyle;
      this.timeStyle = builder.timeStyle;
      this.builderFactories = GsonBuilder.newImmutableList(builder.factories);
      this.builderHierarchyFactories = GsonBuilder.newImmutableList(builder.hierarchyFactories);
      this.objectToNumberStrategy = builder.objectToNumberStrategy;
      this.numberToNumberStrategy = builder.numberToNumberStrategy;
      this.reflectionFilters = GsonBuilder.newImmutableList(builder.reflectionFilters);
      if (builder == GsonBuilder.DEFAULT) {
         this.constructorConstructor = GsonBuilder.DEFAULT_CONSTRUCTOR_CONSTRUCTOR;
         this.jsonAdapterFactory = GsonBuilder.DEFAULT_JSON_ADAPTER_ANNOTATION_TYPE_ADAPTER_FACTORY;
         this.factories = GsonBuilder.DEFAULT_TYPE_ADAPTER_FACTORIES;
      } else {
         this.constructorConstructor = new ConstructorConstructor(this.instanceCreators, this.useJdkUnsafe, this.reflectionFilters);
         this.jsonAdapterFactory = new JsonAdapterAnnotationTypeAdapterFactory(this.constructorConstructor);
         this.factories = builder.createFactories(this.constructorConstructor, this.jsonAdapterFactory);
      }
   }

   public GsonBuilder newBuilder() {
      return new GsonBuilder(this);
   }

   @Deprecated
   public Excluder excluder() {
      return this.excluder;
   }

   public FieldNamingStrategy fieldNamingStrategy() {
      return this.fieldNamingStrategy;
   }

   public boolean serializeNulls() {
      return this.serializeNulls;
   }

   public boolean htmlSafe() {
      return this.htmlSafe;
   }

   public <T> TypeAdapter<T> getAdapter(TypeToken<T> type) {
      Objects.requireNonNull(type, "type must not be null");
      TypeAdapter<?> cached = this.typeTokenCache.get(type);
      if (cached != null) {
         return (TypeAdapter<T>)cached;
      }

      Map<TypeToken<?>, TypeAdapter<?>> threadCalls = this.threadLocalAdapterResults.get();
      boolean isInitialAdapterRequest = false;
      if (threadCalls == null) {
         threadCalls = new HashMap<>();
         this.threadLocalAdapterResults.set(threadCalls);
         isInitialAdapterRequest = true;
      } else {
         TypeAdapter<T> ongoingCall = (TypeAdapter<T>)threadCalls.get(type);
         if (ongoingCall != null) {
            return ongoingCall;
         }
      }

      TypeAdapter<T> candidate = null;

      try {
         Gson.FutureTypeAdapter<T> call = new Gson.FutureTypeAdapter<>();
         threadCalls.put(type, call);

         for (TypeAdapterFactory factory : this.factories) {
            candidate = factory.create(this, type);
            if (candidate != null) {
               call.setDelegate(candidate);
               threadCalls.put(type, candidate);
               break;
            }
         }
      } finally {
         if (isInitialAdapterRequest) {
            this.threadLocalAdapterResults.remove();
         }
      }

      if (candidate == null) {
         throw new IllegalArgumentException("GSON (2.14.0) cannot handle " + type);
      }

      if (isInitialAdapterRequest) {
         this.typeTokenCache.putAll(threadCalls);
      }

      return candidate;
   }

   public <T> TypeAdapter<T> getAdapter(Class<T> type) {
      return this.getAdapter(TypeToken.get(type));
   }

   public <T> TypeAdapter<T> getDelegateAdapter(TypeAdapterFactory skipPast, TypeToken<T> type) {
      Objects.requireNonNull(skipPast, "skipPast must not be null");
      Objects.requireNonNull(type, "type must not be null");
      if (this.jsonAdapterFactory.isClassJsonAdapterFactory(type, skipPast)) {
         skipPast = this.jsonAdapterFactory;
      }

      boolean skipPastFound = false;

      for (TypeAdapterFactory factory : this.factories) {
         if (!skipPastFound) {
            if (factory == skipPast) {
               skipPastFound = true;
            }
         } else {
            TypeAdapter<T> candidate = factory.create(this, type);
            if (candidate != null) {
               return candidate;
            }
         }
      }

      if (skipPastFound) {
         throw new IllegalArgumentException("GSON cannot serialize or deserialize " + type);
      } else {
         return this.getAdapter(type);
      }
   }

   public JsonElement toJsonTree(Object src) {
      return src == null ? JsonNull.INSTANCE : this.toJsonTree(src, src.getClass());
   }

   public JsonElement toJsonTree(Object src, Type typeOfSrc) {
      JsonTreeWriter writer = new JsonTreeWriter();
      this.toJson(src, typeOfSrc, writer);
      return writer.get();
   }

   public String toJson(Object src) {
      return src == null ? this.toJson(JsonNull.INSTANCE) : this.toJson(src, src.getClass());
   }

   public String toJson(Object src, Type typeOfSrc) {
      StringBuilder writer = new StringBuilder();
      this.toJson(src, typeOfSrc, writer);
      return writer.toString();
   }

   public void toJson(Object src, Appendable writer) throws JsonIOException {
      if (src != null) {
         this.toJson(src, src.getClass(), writer);
      } else {
         this.toJson(JsonNull.INSTANCE, writer);
      }
   }

   public void toJson(Object src, Type typeOfSrc, Appendable writer) throws JsonIOException {
      try {
         JsonWriter jsonWriter = this.newJsonWriter(Streams.writerForAppendable(writer));
         this.toJson(src, typeOfSrc, jsonWriter);
      } catch (IOException e) {
         throw new JsonIOException(e);
      }
   }

   public void toJson(Object src, Type typeOfSrc, JsonWriter writer) throws JsonIOException {
      TypeAdapter<Object> adapter = this.getAdapter((TypeToken<Object>)TypeToken.get(typeOfSrc));
      Strictness oldStrictness = writer.getStrictness();
      if (this.strictness != null) {
         writer.setStrictness(this.strictness);
      } else if (writer.getStrictness() == Strictness.LEGACY_STRICT) {
         writer.setStrictness(Strictness.LENIENT);
      }

      boolean oldHtmlSafe = writer.isHtmlSafe();
      boolean oldSerializeNulls = writer.getSerializeNulls();
      writer.setHtmlSafe(this.htmlSafe);
      writer.setSerializeNulls(this.serializeNulls);

      try {
         adapter.write(writer, src);
      } catch (IOException e) {
         throw new JsonIOException(e);
      } catch (AssertionError e) {
         throw new AssertionError("AssertionError (GSON 2.14.0): " + e.getMessage(), e);
      } finally {
         writer.setStrictness(oldStrictness);
         writer.setHtmlSafe(oldHtmlSafe);
         writer.setSerializeNulls(oldSerializeNulls);
      }
   }

   public String toJson(JsonElement jsonElement) {
      StringBuilder writer = new StringBuilder();
      this.toJson(jsonElement, writer);
      return writer.toString();
   }

   public void toJson(JsonElement jsonElement, Appendable writer) throws JsonIOException {
      try {
         JsonWriter jsonWriter = this.newJsonWriter(Streams.writerForAppendable(writer));
         this.toJson(jsonElement, jsonWriter);
      } catch (IOException e) {
         throw new JsonIOException(e);
      }
   }

   public void toJson(JsonElement jsonElement, JsonWriter writer) throws JsonIOException {
      Strictness oldStrictness = writer.getStrictness();
      boolean oldHtmlSafe = writer.isHtmlSafe();
      boolean oldSerializeNulls = writer.getSerializeNulls();
      writer.setHtmlSafe(this.htmlSafe);
      writer.setSerializeNulls(this.serializeNulls);
      if (this.strictness != null) {
         writer.setStrictness(this.strictness);
      } else if (writer.getStrictness() == Strictness.LEGACY_STRICT) {
         writer.setStrictness(Strictness.LENIENT);
      }

      try {
         Streams.write(jsonElement, writer);
      } catch (IOException e) {
         throw new JsonIOException(e);
      } catch (AssertionError e) {
         throw new AssertionError("AssertionError (GSON 2.14.0): " + e.getMessage(), e);
      } finally {
         writer.setStrictness(oldStrictness);
         writer.setHtmlSafe(oldHtmlSafe);
         writer.setSerializeNulls(oldSerializeNulls);
      }
   }

   public JsonWriter newJsonWriter(Writer writer) throws IOException {
      if (this.generateNonExecutableJson) {
         writer.write(")]}'\n");
      }

      JsonWriter jsonWriter = new JsonWriter(writer);
      jsonWriter.setFormattingStyle(this.formattingStyle);
      jsonWriter.setHtmlSafe(this.htmlSafe);
      jsonWriter.setStrictness(this.strictness == null ? Strictness.LEGACY_STRICT : this.strictness);
      jsonWriter.setSerializeNulls(this.serializeNulls);
      return jsonWriter;
   }

   public JsonReader newJsonReader(Reader reader) {
      JsonReader jsonReader = new JsonReader(reader);
      jsonReader.setStrictness(this.strictness == null ? Strictness.LEGACY_STRICT : this.strictness);
      return jsonReader;
   }

   public <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
      return this.fromJson(json, TypeToken.get(classOfT));
   }

   public <T> T fromJson(String json, Type typeOfT) throws JsonSyntaxException {
      return this.fromJson(json, (TypeToken<T>)TypeToken.get(typeOfT));
   }

   public <T> T fromJson(String json, TypeToken<T> typeOfT) throws JsonSyntaxException {
      if (json == null) {
         return null;
      }

      StringReader reader = new StringReader(json);
      return this.fromJson(reader, typeOfT);
   }

   public <T> T fromJson(Reader json, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
      return this.fromJson(json, TypeToken.get(classOfT));
   }

   public <T> T fromJson(Reader json, Type typeOfT) throws JsonIOException, JsonSyntaxException {
      return this.fromJson(json, (TypeToken<T>)TypeToken.get(typeOfT));
   }

   public <T> T fromJson(Reader json, TypeToken<T> typeOfT) throws JsonIOException, JsonSyntaxException {
      JsonReader jsonReader = this.newJsonReader(json);
      T object = this.fromJson(jsonReader, typeOfT);
      assertFullConsumption(object, jsonReader);
      return object;
   }

   public <T> T fromJson(JsonReader reader, Type typeOfT) throws JsonIOException, JsonSyntaxException {
      return this.fromJson(reader, (TypeToken<T>)TypeToken.get(typeOfT));
   }

   public <T> T fromJson(JsonReader reader, TypeToken<T> typeOfT) throws JsonIOException, JsonSyntaxException {
      boolean isEmpty = true;
      Strictness oldStrictness = reader.getStrictness();
      if (this.strictness != null) {
         reader.setStrictness(this.strictness);
      } else if (reader.getStrictness() == Strictness.LEGACY_STRICT) {
         reader.setStrictness(Strictness.LENIENT);
      }

      try {
         JsonToken unused = reader.peek();
         isEmpty = false;
         TypeAdapter<T> typeAdapter = this.getAdapter(typeOfT);
         T object = typeAdapter.read(reader);
         Class<?> expectedTypeWrapped = Primitives.wrap(typeOfT.getRawType());
         if (object != null && !expectedTypeWrapped.isInstance(object)) {
            throw new ClassCastException(
               "Type adapter '"
                  + typeAdapter
                  + "' returned wrong type; requested "
                  + typeOfT.getRawType()
                  + " but got instance of "
                  + object.getClass()
                  + "\nVerify that the adapter was registered for the correct type."
            );
         } else {
            return object;
         }
      } catch (EOFException e) {
         if (isEmpty) {
            return null;
         } else {
            throw new JsonSyntaxException(e);
         }
      } catch (IllegalStateException e) {
         throw new JsonSyntaxException(e);
      } catch (IOException e) {
         throw new JsonSyntaxException(e);
      } catch (AssertionError e) {
         throw new AssertionError("AssertionError (GSON 2.14.0): " + e.getMessage(), e);
      } finally {
         reader.setStrictness(oldStrictness);
      }
   }

   public <T> T fromJson(JsonElement json, Class<T> classOfT) throws JsonSyntaxException {
      return this.fromJson(json, TypeToken.get(classOfT));
   }

   public <T> T fromJson(JsonElement json, Type typeOfT) throws JsonSyntaxException {
      return this.fromJson(json, (TypeToken<T>)TypeToken.get(typeOfT));
   }

   public <T> T fromJson(JsonElement json, TypeToken<T> typeOfT) throws JsonSyntaxException {
      return json == null ? null : this.fromJson(new JsonTreeReader(json), typeOfT);
   }

   private static void assertFullConsumption(Object obj, JsonReader reader) {
      try {
         if (obj != null && reader.peek() != JsonToken.END_DOCUMENT) {
            throw new JsonSyntaxException("JSON document was not fully consumed.");
         }
      } catch (MalformedJsonException e) {
         throw new JsonSyntaxException(e);
      } catch (IOException e) {
         throw new JsonIOException(e);
      }
   }

   @Override
   public String toString() {
      return "{serializeNulls:" + this.serializeNulls + ",factories:" + this.factories + ",instanceCreators:" + this.constructorConstructor + "}";
   }

   static class FutureTypeAdapter<T> extends SerializationDelegatingTypeAdapter<T> {
      private TypeAdapter<T> delegate = null;

      public void setDelegate(TypeAdapter<T> typeAdapter) {
         if (this.delegate != null) {
            throw new AssertionError("Delegate is already set");
         }

         this.delegate = typeAdapter;
      }

      private TypeAdapter<T> delegate() {
         TypeAdapter<T> delegate = this.delegate;
         if (delegate == null) {
            throw new IllegalStateException("Adapter for type with cyclic dependency has been used before dependency has been resolved");
         } else {
            return delegate;
         }
      }

      @Override
      public TypeAdapter<T> getSerializationDelegate() {
         return this.delegate();
      }

      @Override
      public T read(JsonReader in) throws IOException {
         return this.delegate().read(in);
      }

      @Override
      public void write(JsonWriter out, T value) throws IOException {
         this.delegate().write(out, value);
      }
   }
}
