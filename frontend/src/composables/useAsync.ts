import { ref } from 'vue';

export function useAsync<T>(task: () => Promise<T>) {
  const loading = ref(false);
  const error = ref<string | null>(null);
  const data = ref<T | null>(null);

  async function run(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      data.value = await task();
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '操作失败';
    } finally {
      loading.value = false;
    }
  }

  return { loading, error, data, run };
}
