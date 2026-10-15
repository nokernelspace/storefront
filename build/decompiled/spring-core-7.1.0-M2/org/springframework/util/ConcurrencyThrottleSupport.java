package org.springframework.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class ConcurrencyThrottleSupport implements Serializable {
   public static final int UNBOUNDED_CONCURRENCY = -1;
   public static final int NO_CONCURRENCY = 0;
   protected transient Log logger = LogFactory.getLog(this.getClass());
   private final Lock concurrencyLock = new ReentrantLock();
   private final Condition concurrencyCondition = this.concurrencyLock.newCondition();
   private int concurrencyLimit = -1;
   private int concurrencyCount = 0;

   public void setConcurrencyLimit(int concurrencyLimit) {
      this.concurrencyLimit = concurrencyLimit;
   }

   public int getConcurrencyLimit() {
      return this.concurrencyLimit;
   }

   public boolean isThrottleActive() {
      return this.concurrencyLimit >= 0;
   }

   protected void beforeAccess() {
      if (this.concurrencyLimit == 0) {
         this.onAccessRejected("Concurrency limit set to NO_CONCURRENCY - not allowed to enter");
      }

      if (this.concurrencyLimit > 0) {
         this.concurrencyLock.lock();

         try {
            if (this.concurrencyCount >= this.concurrencyLimit) {
               this.onLimitReached();
            }

            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Entering throttle at concurrency count " + this.concurrencyCount);
            }

            this.concurrencyCount++;
         } finally {
            this.concurrencyLock.unlock();
         }
      }
   }

   protected void onLimitReached() {
      boolean interrupted = false;

      while (this.concurrencyCount >= this.concurrencyLimit) {
         if (interrupted) {
            this.onAccessRejected("Thread was interrupted while waiting for access but concurrency limit still does not allow for entering");
         }

         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Concurrency count " + this.concurrencyCount + " has reached limit " + this.concurrencyLimit + " - blocking");
         }

         try {
            this.concurrencyCondition.await();
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            interrupted = true;
         }
      }
   }

   protected void onAccessRejected(String msg) {
      throw new IllegalStateException(msg);
   }

   protected void afterAccess() {
      if (this.concurrencyLimit >= 0) {
         boolean debug = this.logger.isDebugEnabled();
         this.concurrencyLock.lock();

         try {
            this.concurrencyCount--;
            if (debug) {
               this.logger.debug("Returning from throttle at concurrency count " + this.concurrencyCount);
            }

            this.concurrencyCondition.signal();
         } finally {
            this.concurrencyLock.unlock();
         }
      }
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.logger = LogFactory.getLog(this.getClass());
   }
}
