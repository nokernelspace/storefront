package org.springframework.core.retry.support;

import java.util.concurrent.Callable;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryOperations;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.core.task.TaskCallback;
import org.springframework.util.Assert;

public class RetryTask<V, E extends Exception> implements TaskCallback<V, E> {
   private final TaskCallback<V, E> task;
   private final RetryOperations retryTemplate;

   public RetryTask(TaskCallback<V, E> task) {
      this(task, new RetryTemplate());
   }

   public RetryTask(TaskCallback<V, E> task, RetryPolicy retryPolicy) {
      this(task, new RetryTemplate(retryPolicy));
   }

   public RetryTask(TaskCallback<V, E> task, RetryOperations retryTemplate) {
      Assert.notNull(task, "TaskCallback must not be null");
      Assert.notNull(retryTemplate, "RetryTemplate must not be null");
      this.task = task;
      this.retryTemplate = retryTemplate;
   }

   @Override
   public V call() throws E {
      try {
         return this.retryTemplate.execute(new Retryable<V>() {
            @Override
            public V execute() throws E {
               return RetryTask.this.task.call();
            }

            @Override
            public String getName() {
               return RetryTask.this.getName();
            }
         });
      } catch (RetryException retryException) {
         throw (Exception)retryException.getCause();
      }
   }

   public String getName() {
      return this.task.getClass().getName();
   }

   @Override
   public String toString() {
      return "RetryTask for " + this.getName() + " using " + (this.retryTemplate instanceof RetryTemplate rt ? rt.getRetryPolicy() : this.retryTemplate);
   }

   public static <V> Callable<V> wrap(Callable<V> task) {
      return wrap(task, new RetryTemplate());
   }

   public static <V> Callable<V> wrap(Callable<V> task, RetryPolicy retryPolicy) {
      return wrap(task, new RetryTemplate(retryPolicy));
   }

   public static <V> Callable<V> wrap(Callable<V> task, RetryOperations retryTemplate) {
      return new RetryTask<V, Exception>(TaskCallback.from(task), retryTemplate) {
         @Override
         public String getName() {
            return task.getClass().getName();
         }
      };
   }

   public static Runnable wrap(Runnable task) {
      return wrap(task, new RetryTemplate());
   }

   public static Runnable wrap(Runnable task, RetryPolicy retryPolicy) {
      return wrap(task, new RetryTemplate(retryPolicy));
   }

   public static Runnable wrap(Runnable task, RetryOperations retryTemplate) {
      final RetryTask<Void, RuntimeException> rt = new RetryTask<Void, RuntimeException>(TaskCallback.from(task), retryTemplate) {
         @Override
         public String getName() {
            return task.getClass().getName();
         }
      };
      return new Runnable() {
         @Override
         public void run() {
            rt.call();
         }

         @Override
         public String toString() {
            return rt.toString();
         }
      };
   }
}
