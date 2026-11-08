<script setup lang="ts">
import { onMounted } from 'vue';
import { ElButton, ElTag } from 'element-plus';
import PageHeader from '../components/PageHeader.vue';
import { fetchHealth } from '../api/health';
import { useAsync } from '../composables/useAsync';
import { useAppStore } from '../stores/app';

const app = useAppStore();
const { loading, error, data: health, run } = useAsync(fetchHealth);

async function reload(): Promise<void> {
  await run();
  app.setBackendStatus(health.value?.status ?? (error.value ? 'DOWN' : 'UNKNOWN'));
}

onMounted(() => {
  void reload();
});
</script>

<template>
  <main class="page">
    <PageHeader :title="app.appName" subtitle="后端骨架已就绪，业务页面由 Agent 增量编写。" />

    <section class="card">
      <div class="row">
        <span class="label">后端健康</span>
        <ElTag :type="app.backendStatus === 'UP' ? 'success' : 'info'" effect="light">
          {{ app.backendStatus }}
        </ElTag>
      </div>
      <p v-if="loading" class="muted">检查中…</p>
      <p v-else-if="error" class="error">{{ error }}</p>
      <p v-else class="muted">GET /actuator/health</p>
      <ElButton type="primary" :loading="loading" @click="reload">重新检查</ElButton>
    </section>
  </main>
</template>
