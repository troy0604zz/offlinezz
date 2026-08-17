import type { DataRow, TrainingDashboard, TrainingResource } from '../types/training'
import { http } from './http'

const endpointByResource: Record<TrainingResource, string> = {
  schema: '/admin/training/schemas',
  metric: '/admin/semantic/metrics',
  relation: '/admin/semantic/relations',
  synonym: '/admin/training/synonyms',
  example: '/admin/training/sql-examples',
  golden: '/admin/training/golden-questions',
}

export const trainingApi = {
  dashboard: () => http.get<TrainingDashboard>('/admin/training/dashboard'),
  schemas: () => http.get<DataRow[]>('/admin/training/schemas'),
  metrics: () => http.get<DataRow[]>('/admin/semantic/metrics'),
  relations: () => http.get<DataRow[]>('/admin/semantic/relations'),
  synonyms: () => http.get<DataRow[]>('/admin/training/synonyms'),
  examples: () => http.get<DataRow[]>('/admin/training/sql-examples'),
  golden: () => http.get<DataRow[]>('/admin/training/golden-questions'),
  feedback: () => http.get<DataRow[]>('/admin/training/feedback'),
  documents: () => http.get<DataRow[]>('/admin/knowledge/documents'),
  create: (resource: TrainingResource, body: DataRow) => http.post(endpointByResource[resource], body),
  publishMetric: (id: number) => http.post(`/admin/semantic/metrics/${id}/publish`),
  publishExample: (id: number) => http.post(`/admin/training/sql-examples/${id}/publish`),
  runGolden: (id: number) => http.post<DataRow>(`/admin/training/golden-questions/${id}/run`),
  promoteFeedback: (id: number) => http.post(`/admin/training/feedback/${id}/promote`),
  uploadDocument: (file: File, domain = 'sales') => {
    const form = new FormData()
    form.append('file', file)
    return http.post(`/admin/knowledge/documents?domain=${encodeURIComponent(domain)}`, form)
  },
}
