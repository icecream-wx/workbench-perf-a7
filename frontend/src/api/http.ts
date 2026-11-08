import axios, { type AxiosRequestConfig } from 'axios';

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      const message = (error.response?.data as { message?: string } | undefined)?.message;
      return Promise.reject(new Error(message || error.message || '网络请求失败'));
    }
    return Promise.reject(error instanceof Error ? error : new Error('网络请求失败'));
  },
);

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T>>(config);
  const body = response.data;
  if (body && typeof body === 'object' && 'code' in body) {
    if (body.code !== 0) throw new Error(body.message || '请求失败');
    return body.data;
  }
  return body as unknown as T;
}

export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, method: 'GET', url });
}
