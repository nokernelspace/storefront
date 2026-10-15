package org.springframework.expression.spel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypedValue;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class ExpressionState {
   private final EvaluationContext relatedContext;
   private final TypedValue rootObject;
   private final SpelParserConfiguration configuration;
   private int operationsCount;
   private @Nullable Deque<TypedValue> contextObjects;
   private @Nullable Deque<TypedValue> scopeRootObjects;

   public ExpressionState(EvaluationContext context) {
      this(context, context.getRootObject(), SpelParserConfiguration.withDefaults());
   }

   public ExpressionState(EvaluationContext context, SpelParserConfiguration configuration) {
      this(context, context.getRootObject(), configuration);
   }

   public ExpressionState(EvaluationContext context, TypedValue rootObject) {
      this(context, rootObject, SpelParserConfiguration.withDefaults());
   }

   public ExpressionState(EvaluationContext context, TypedValue rootObject, SpelParserConfiguration configuration) {
      Assert.notNull(context, "EvaluationContext must not be null");
      Assert.notNull(rootObject, "'rootObject' must not be null");
      Assert.notNull(configuration, "SpelParserConfiguration must not be null");
      this.relatedContext = context;
      this.rootObject = rootObject;
      this.configuration = configuration;
   }

   public TypedValue getActiveContextObject() {
      return CollectionUtils.isEmpty(this.contextObjects) ? this.rootObject : this.contextObjects.element();
   }

   public void pushActiveContextObject(TypedValue obj) {
      this.initContextObjects().push(obj);
   }

   public void popActiveContextObject() {
      try {
         this.initContextObjects().pop();
      } catch (NoSuchElementException ex) {
         throw new IllegalStateException("Cannot pop active context object: stack is empty");
      }
   }

   public TypedValue getRootContextObject() {
      return this.rootObject;
   }

   public TypedValue getScopeRootContextObject() {
      return CollectionUtils.isEmpty(this.scopeRootObjects) ? this.rootObject : this.scopeRootObjects.element();
   }

   public TypedValue assignVariable(String name, Supplier<TypedValue> valueSupplier) {
      this.trackOperation();
      return this.relatedContext.assignVariable(name, valueSupplier);
   }

   public void setVariable(String name, @Nullable Object value) {
      this.relatedContext.setVariable(name, value);
   }

   public TypedValue lookupVariable(String name) {
      this.trackOperation();
      Object value = this.relatedContext.lookupVariable(name);
      return value != null ? new TypedValue(value) : TypedValue.NULL;
   }

   public TypeComparator getTypeComparator() {
      return this.relatedContext.getTypeComparator();
   }

   public Class<?> findType(String type) throws EvaluationException {
      return this.relatedContext.getTypeLocator().findType(type);
   }

   public TypeConverter getTypeConverter() {
      return this.relatedContext.getTypeConverter();
   }

   public Object convertValue(Object value, TypeDescriptor targetTypeDescriptor) throws EvaluationException {
      Object result = this.relatedContext.getTypeConverter().convertValue(value, TypeDescriptor.forObject(value), targetTypeDescriptor);
      if (result == null) {
         throw new IllegalStateException("Null conversion result for value [" + value + "]");
      } else {
         return result;
      }
   }

   public @Nullable Object convertValue(TypedValue value, TypeDescriptor targetTypeDescriptor) throws EvaluationException {
      Object val = value.getValue();
      return this.relatedContext.getTypeConverter().convertValue(val, TypeDescriptor.forObject(val), targetTypeDescriptor);
   }

   public void enterScope() {
      this.initScopeRootObjects().push(this.getActiveContextObject());
   }

   public void exitScope() {
      this.initScopeRootObjects().pop();
   }

   private Deque<TypedValue> initContextObjects() {
      if (this.contextObjects == null) {
         this.contextObjects = new ArrayDeque<>();
      }

      return this.contextObjects;
   }

   private Deque<TypedValue> initScopeRootObjects() {
      if (this.scopeRootObjects == null) {
         this.scopeRootObjects = new ArrayDeque<>();
      }

      return this.scopeRootObjects;
   }

   public TypedValue operate(Operation op, @Nullable Object left, @Nullable Object right) throws EvaluationException {
      OperatorOverloader overloader = this.relatedContext.getOperatorOverloader();
      if (overloader.overridesOperation(op, left, right)) {
         this.trackOperation();
         Object returnValue = overloader.operate(op, left, right);
         return new TypedValue(returnValue);
      } else {
         String leftType = left == null ? "null" : left.getClass().getName();
         String rightType = right == null ? "null" : right.getClass().getName();
         throw new SpelEvaluationException(SpelMessage.OPERATOR_NOT_SUPPORTED_BETWEEN_TYPES, op, leftType, rightType);
      }
   }

   public List<PropertyAccessor> getPropertyAccessors() {
      return this.relatedContext.getPropertyAccessors();
   }

   public EvaluationContext getEvaluationContext() {
      return this.relatedContext;
   }

   public SpelParserConfiguration getConfiguration() {
      return this.configuration;
   }

   public void trackOperation() {
      int maxOperations = this.configuration.getMaximumOperations();
      if (++this.operationsCount >= maxOperations) {
         throw new SpelEvaluationException(SpelMessage.MAX_OPERATIONS_EXCEEDED, maxOperations);
      }
   }
}
