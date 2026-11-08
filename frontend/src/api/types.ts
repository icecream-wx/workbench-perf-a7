export interface PageResult<T> {
  items: T[];
  total: number;
}

export interface ApiErrorBody {
  code: number;
  message: string;
}
