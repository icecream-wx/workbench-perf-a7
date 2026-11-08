import axios, { type AxiosRequestConfig } from 'axios';
import type { ApiErrorBody, ApiFieldError } from './types';

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export class ApiRequestError extends Error {
  status?: number;
  code?: string;
  fieldErrors: ApiFieldError[];

  constructor(
    message: string,
    options: { status?: number; code?: string; fieldErrors?: ApiFieldError[] } = {},
  ) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = options.status;
    this.code = options.code;
    this.fieldErrors = options.fieldErrors ?? [];
  }
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      const body = error.response?.data as ApiErrorBody | undefined;
      const message = body?.message || error.message || '网络请求失败';
      return Promise.reject(
        new ApiRequestError(message, {
          status: error.response?.status,
          code: body?.code,
          fieldErrors: Array.isArray(body?.fieldErrors) ? body.fieldErrors : [],
        }),
      );
    }
    return Promise.reject(error instanceof Error ? error : new Error('网络请求失败'));
  },
);

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T> | T>(config);
  const body = response.data;
  if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
    const envelope = body as ApiEnvelope<T>;
    if (envelope.code !== 0) throw new Error(envelope.message || '请求失败');
    return envelope.data;
  }
  return body as T;
}

export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, method: 'GET', url });
}
