package org.springframework.boot.info;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

public class ProcessInfo {
   private static final String VIRTUAL_THREAD_SCHEDULER_CLASS = "jdk.management.VirtualThreadSchedulerMXBean";
   private static final boolean VIRTUAL_THREAD_SCHEDULER_CLASS_PRESENT = ClassUtils.isPresent("jdk.management.VirtualThreadSchedulerMXBean", null);
   private static final Runtime runtime = Runtime.getRuntime();
   private final long pid;
   private final long parentPid;
   private final @Nullable String owner;
   private final @Nullable Instant startTime;
   private final ZoneId timezone;
   private final Locale locale;
   private final String workingDirectory;

   public ProcessInfo() {
      ProcessHandle process = ProcessHandle.current();
      this.pid = process.pid();
      this.parentPid = process.parent().map(ProcessHandle::pid).orElse(-1L);
      this.owner = process.info().user().orElse(null);
      this.startTime = process.info().startInstant().orElse(null);
      this.timezone = ZoneId.systemDefault();
      this.locale = Locale.getDefault();
      this.workingDirectory = Path.of(".").toAbsolutePath().normalize().toString();
   }

   public int getCpus() {
      return runtime.availableProcessors();
   }

   public ProcessInfo.MemoryInfo getMemory() {
      return new ProcessInfo.MemoryInfo();
   }

   public ProcessInfo.@Nullable VirtualThreadsInfo getVirtualThreads() {
      if (!VIRTUAL_THREAD_SCHEDULER_CLASS_PRESENT) {
         return null;
      }

      try {
         Class<PlatformManagedObject> mxbeanClass = ClassUtils.forName("jdk.management.VirtualThreadSchedulerMXBean", null);
         PlatformManagedObject mxbean = ManagementFactory.getPlatformMXBean(mxbeanClass);
         int mountedVirtualThreadCount = this.<Integer>invokeMethod(mxbeanClass, mxbean, "getMountedVirtualThreadCount");
         long queuedVirtualThreadCount = this.<Long>invokeMethod(mxbeanClass, mxbean, "getQueuedVirtualThreadCount");
         int parallelism = this.<Integer>invokeMethod(mxbeanClass, mxbean, "getParallelism");
         int poolSize = this.<Integer>invokeMethod(mxbeanClass, mxbean, "getPoolSize");
         return new ProcessInfo.VirtualThreadsInfo(mountedVirtualThreadCount, queuedVirtualThreadCount, parallelism, poolSize);
      } catch (Exception ex) {
         return null;
      }
   }

   private <T> T invokeMethod(Class<?> mxbeanClass, Object mxbean, String name) throws ReflectiveOperationException {
      Method method = mxbeanClass.getMethod(name);
      return (T)method.invoke(mxbean);
   }

   public long getPid() {
      return this.pid;
   }

   public long getParentPid() {
      return this.parentPid;
   }

   public @Nullable String getOwner() {
      return this.owner;
   }

   public @Nullable Duration getUptime() {
      return this.startTime != null ? Duration.between(this.startTime, Instant.now()) : null;
   }

   public @Nullable Instant getStartTime() {
      return this.startTime;
   }

   public Instant getCurrentTime() {
      return Instant.now();
   }

   public ZoneId getTimezone() {
      return this.timezone;
   }

   public Locale getLocale() {
      return this.locale;
   }

   public String getWorkingDirectory() {
      return this.workingDirectory;
   }

   public static class MemoryInfo {
      private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
      private static final List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
      private final ProcessInfo.MemoryInfo.MemoryUsageInfo heap = new ProcessInfo.MemoryInfo.MemoryUsageInfo(memoryMXBean.getHeapMemoryUsage());
      private final ProcessInfo.MemoryInfo.MemoryUsageInfo nonHeap = new ProcessInfo.MemoryInfo.MemoryUsageInfo(memoryMXBean.getNonHeapMemoryUsage());
      private final List<ProcessInfo.MemoryInfo.GarbageCollectorInfo> garbageCollectors = garbageCollectorMXBeans.stream()
         .map(ProcessInfo.MemoryInfo.GarbageCollectorInfo::new)
         .toList();

      MemoryInfo() {
      }

      public ProcessInfo.MemoryInfo.MemoryUsageInfo getHeap() {
         return this.heap;
      }

      public ProcessInfo.MemoryInfo.MemoryUsageInfo getNonHeap() {
         return this.nonHeap;
      }

      public List<ProcessInfo.MemoryInfo.GarbageCollectorInfo> getGarbageCollectors() {
         return this.garbageCollectors;
      }

      public static class GarbageCollectorInfo {
         private final String name;
         private final long collectionCount;

         GarbageCollectorInfo(GarbageCollectorMXBean garbageCollectorMXBean) {
            this.name = garbageCollectorMXBean.getName();
            this.collectionCount = garbageCollectorMXBean.getCollectionCount();
         }

         public String getName() {
            return this.name;
         }

         public long getCollectionCount() {
            return this.collectionCount;
         }
      }

      public static class MemoryUsageInfo {
         private final MemoryUsage memoryUsage;

         MemoryUsageInfo(MemoryUsage memoryUsage) {
            this.memoryUsage = memoryUsage;
         }

         public long getInit() {
            return this.memoryUsage.getInit();
         }

         public long getUsed() {
            return this.memoryUsage.getUsed();
         }

         public long getCommitted() {
            return this.memoryUsage.getCommitted();
         }

         public long getMax() {
            return this.memoryUsage.getMax();
         }
      }
   }

   public static class VirtualThreadsInfo {
      private final int mounted;
      private final long queued;
      private final int parallelism;
      private final int poolSize;

      VirtualThreadsInfo(int mounted, long queued, int parallelism, int poolSize) {
         this.mounted = mounted;
         this.queued = queued;
         this.parallelism = parallelism;
         this.poolSize = poolSize;
      }

      public int getMounted() {
         return this.mounted;
      }

      public long getQueued() {
         return this.queued;
      }

      public int getParallelism() {
         return this.parallelism;
      }

      public int getPoolSize() {
         return this.poolSize;
      }
   }
}
