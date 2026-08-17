import type { ModelRuntime, ModelRuntimeUpdate, ModelRuntimeUpdateResult, PlatformInfo } from '../types/platform'
import { http } from './http'

export const platformApi = {
  info: () => http.get<PlatformInfo>('/platform/info'),
  modelRuntime: () => http.get<ModelRuntime>('/admin/model-runtime'),
  updateModelRuntime: (payload: ModelRuntimeUpdate) =>
    http.put<ModelRuntimeUpdateResult>('/admin/model-runtime', payload),
}
