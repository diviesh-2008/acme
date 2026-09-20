/** One page of results, as returned by the backend's PageResponse. */
export interface PageResponse<T> {
  content: T[];
  /** Zero-based page index. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
