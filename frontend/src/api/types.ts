export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: ApiFieldError[];
}
