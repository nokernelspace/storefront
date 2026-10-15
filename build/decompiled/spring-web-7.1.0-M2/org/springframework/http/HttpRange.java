package org.springframework.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class HttpRange {
   private static final int MAX_RANGES = 100;
   private static final String BYTE_RANGE_PREFIX = "bytes=";

   public ResourceRegion toResourceRegion(Resource resource) {
      Assert.isTrue(resource.getClass() != InputStreamResource.class, "Cannot convert an InputStreamResource to a ResourceRegion");
      long contentLength = getLengthFor(resource);
      long start = this.getRangeStart(contentLength);
      long end = this.getRangeEnd(contentLength);
      Assert.isTrue(start < contentLength, () -> "'position' exceeds the resource length " + contentLength);
      return new ResourceRegion(resource, start, end - start + 1L);
   }

   public abstract long getRangeStart(long length);

   public abstract long getRangeEnd(long length);

   public static HttpRange createByteRange(long firstBytePos) {
      return new HttpRange.ByteRange(firstBytePos, null);
   }

   public static HttpRange createByteRange(long firstBytePos, long lastBytePos) {
      return new HttpRange.ByteRange(firstBytePos, lastBytePos);
   }

   public static HttpRange createSuffixRange(long suffixLength) {
      return new HttpRange.SuffixByteRange(suffixLength);
   }

   public static List<HttpRange> parseRanges(@Nullable String ranges) {
      if (!StringUtils.hasLength(ranges)) {
         return Collections.emptyList();
      }

      if (!ranges.startsWith("bytes=")) {
         throw new IllegalArgumentException("Range '" + ranges + "' does not start with 'bytes='");
      }

      ranges = ranges.substring("bytes=".length());
      String[] tokens = StringUtils.tokenizeToStringArray(ranges, ",");
      if (tokens.length > 100) {
         throw new IllegalArgumentException("Too many ranges: " + tokens.length);
      }

      List<HttpRange> result = new ArrayList<>(tokens.length);

      for (String token : tokens) {
         result.add(parseRange(token));
      }

      return result;
   }

   private static HttpRange parseRange(String range) {
      Assert.hasLength(range, "Range String must not be empty");
      int dashIdx = range.indexOf(45);
      if (dashIdx > 0) {
         long firstPos = Long.parseLong(range, 0, dashIdx, 10);
         if (dashIdx < range.length() - 1) {
            Long lastPos = Long.parseLong(range, dashIdx + 1, range.length(), 10);
            return new HttpRange.ByteRange(firstPos, lastPos);
         } else {
            return new HttpRange.ByteRange(firstPos, null);
         }
      } else if (dashIdx == 0) {
         long suffixLength = Long.parseLong(range, 1, range.length(), 10);
         return new HttpRange.SuffixByteRange(suffixLength);
      } else {
         throw new IllegalArgumentException("Range '" + range + "' does not contain \"-\"");
      }
   }

   public static List<ResourceRegion> toResourceRegions(List<HttpRange> ranges, Resource resource) {
      if (CollectionUtils.isEmpty(ranges)) {
         return Collections.emptyList();
      }

      List<ResourceRegion> regions = new ArrayList<>(ranges.size());

      for (HttpRange range : ranges) {
         regions.add(range.toResourceRegion(resource));
      }

      if (ranges.size() > 1) {
         long length = getLengthFor(resource);
         long total = 0L;

         for (ResourceRegion region : regions) {
            total += region.getCount();
         }

         if (total >= length) {
            throw new IllegalArgumentException("The sum of all ranges (" + total + ") should be less than the resource length (" + length + ")");
         }
      }

      return regions;
   }

   private static long getLengthFor(Resource resource) {
      try {
         long contentLength = resource.contentLength();
         Assert.isTrue(contentLength > 0L, "Resource content length should be > 0");
         return contentLength;
      } catch (IOException ex) {
         throw new IllegalArgumentException("Failed to obtain Resource content length", ex);
      }
   }

   public static String toString(Collection<HttpRange> ranges) {
      Assert.notEmpty(ranges, "Ranges Collection must not be empty");
      StringJoiner builder = new StringJoiner(", ", "bytes=", "");

      for (HttpRange range : ranges) {
         builder.add(range.toString());
      }

      return builder.toString();
   }

   private static class ByteRange extends HttpRange {
      private final long firstPos;
      private final @Nullable Long lastPos;

      public ByteRange(long firstPos, @Nullable Long lastPos) {
         this.assertPositions(firstPos, lastPos);
         this.firstPos = firstPos;
         this.lastPos = lastPos;
      }

      private void assertPositions(long firstBytePos, @Nullable Long lastBytePos) {
         if (firstBytePos < 0L) {
            throw new IllegalArgumentException("Invalid first byte position: " + firstBytePos);
         }

         if (lastBytePos != null && lastBytePos < firstBytePos) {
            throw new IllegalArgumentException("firstBytePosition=" + firstBytePos + " should be less than or equal to lastBytePosition=" + lastBytePos);
         }
      }

      @Override
      public long getRangeStart(long length) {
         return this.firstPos;
      }

      @Override
      public long getRangeEnd(long length) {
         return this.lastPos != null && this.lastPos < length ? this.lastPos : length - 1L;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof HttpRange.ByteRange that && this.firstPos == that.firstPos && ObjectUtils.nullSafeEquals(this.lastPos, that.lastPos);
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.firstPos, this.lastPos);
      }

      @Override
      public String toString() {
         StringBuilder builder = new StringBuilder();
         builder.append(this.firstPos);
         builder.append('-');
         if (this.lastPos != null) {
            builder.append(this.lastPos);
         }

         return builder.toString();
      }
   }

   private static class SuffixByteRange extends HttpRange {
      private final long suffixLength;

      public SuffixByteRange(long suffixLength) {
         if (suffixLength < 0L) {
            throw new IllegalArgumentException("Invalid suffix length: " + suffixLength);
         }

         this.suffixLength = suffixLength;
      }

      @Override
      public long getRangeStart(long length) {
         return this.suffixLength < length ? length - this.suffixLength : 0L;
      }

      @Override
      public long getRangeEnd(long length) {
         return length - 1L;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof HttpRange.SuffixByteRange that && this.suffixLength == that.suffixLength;
      }

      @Override
      public int hashCode() {
         return Long.hashCode(this.suffixLength);
      }

      @Override
      public String toString() {
         return "-" + this.suffixLength;
      }
   }
}
