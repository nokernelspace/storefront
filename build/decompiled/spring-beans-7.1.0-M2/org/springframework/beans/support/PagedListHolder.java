package org.springframework.beans.support;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

@Deprecated(since = "7.0.3", forRemoval = true)
public class PagedListHolder<E> implements Serializable {
   public static final int DEFAULT_PAGE_SIZE = 10;
   public static final int DEFAULT_MAX_LINKED_PAGES = 10;
   private List<E> source = Collections.emptyList();
   private @Nullable Date refreshDate;
   private @Nullable SortDefinition sort;
   private @Nullable SortDefinition sortUsed;
   private int pageSize = 10;
   private int page = 0;
   private boolean newPageSet;
   private int maxLinkedPages = 10;

   public PagedListHolder() {
      this(new ArrayList<>(0));
   }

   public PagedListHolder(List<E> source) {
      this(source, new MutableSortDefinition(true));
   }

   public PagedListHolder(List<E> source, SortDefinition sort) {
      this.setSource(source);
      this.setSort(sort);
   }

   public void setSource(List<E> source) {
      Assert.notNull(source, "Source List must not be null");
      this.source = source;
      this.refreshDate = new Date();
      this.sortUsed = null;
   }

   public List<E> getSource() {
      return this.source;
   }

   public @Nullable Date getRefreshDate() {
      return this.refreshDate;
   }

   public void setSort(@Nullable SortDefinition sort) {
      this.sort = sort;
   }

   public @Nullable SortDefinition getSort() {
      return this.sort;
   }

   public void setPageSize(int pageSize) {
      if (pageSize != this.pageSize) {
         this.pageSize = pageSize;
         if (!this.newPageSet) {
            this.page = 0;
         }
      }
   }

   public int getPageSize() {
      return this.pageSize;
   }

   public void setPage(int page) {
      this.page = page;
      this.newPageSet = true;
   }

   public int getPage() {
      this.newPageSet = false;
      if (this.page >= this.getPageCount()) {
         this.page = this.getPageCount() - 1;
      }

      return this.page;
   }

   public void setMaxLinkedPages(int maxLinkedPages) {
      this.maxLinkedPages = maxLinkedPages;
   }

   public int getMaxLinkedPages() {
      return this.maxLinkedPages;
   }

   public int getPageCount() {
      float nrOfPages = (float)this.getNrOfElements() / this.getPageSize();
      return (int)(!(nrOfPages > (int)nrOfPages) && nrOfPages != 0.0 ? nrOfPages : nrOfPages + 1.0F);
   }

   public boolean isFirstPage() {
      return this.getPage() == 0;
   }

   public boolean isLastPage() {
      return this.getPage() == this.getPageCount() - 1;
   }

   public void previousPage() {
      if (!this.isFirstPage()) {
         this.page--;
      }
   }

   public void nextPage() {
      if (!this.isLastPage()) {
         this.page++;
      }
   }

   public int getNrOfElements() {
      return this.getSource().size();
   }

   public int getFirstElementOnPage() {
      return this.getPageSize() * this.getPage();
   }

   public int getLastElementOnPage() {
      int endIndex = this.getPageSize() * (this.getPage() + 1);
      int size = this.getNrOfElements();
      return (endIndex > size ? size : endIndex) - 1;
   }

   public List<E> getPageList() {
      return this.getSource().subList(this.getFirstElementOnPage(), this.getLastElementOnPage() + 1);
   }

   public int getFirstLinkedPage() {
      return Math.max(0, this.getPage() - this.getMaxLinkedPages() / 2);
   }

   public int getLastLinkedPage() {
      return Math.min(this.getFirstLinkedPage() + this.getMaxLinkedPages() - 1, this.getPageCount() - 1);
   }

   public void resort() {
      SortDefinition sort = this.getSort();
      if (sort != null && !sort.equals(this.sortUsed)) {
         this.sortUsed = this.copySortDefinition(sort);
         this.doSort(this.getSource(), sort);
         this.setPage(0);
      }
   }

   protected SortDefinition copySortDefinition(SortDefinition sort) {
      return new MutableSortDefinition(sort);
   }

   protected void doSort(List<E> source, SortDefinition sort) {
      PropertyComparator.sort(source, sort);
   }
}
