import axios from 'axios';

export interface HealthStatus {
  status: string;
  details?: Record<string, unknown>;
}

/** Actuator health is outside the /api business prefix. */
export async function fetchHealth(): Promise<HealthStatus> {
  const { data } = await axios.get<HealthStatus>('/actuator/health', { timeout: 5000 });
  return data;
}
