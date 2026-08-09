/*
  paged-response.model.ts
  Generic wrapper matching every backend service's shared PagedResponse<T>
  shape, reused across all paginated list endpoints.
*/

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
