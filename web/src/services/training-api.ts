import type { DataRow, TrainingDashboard, TrainingResource } from '../types/training'
import { http } from './http'

const endpointByResource: Record<TrainingResource, string> = {
  document: '/admin/knowledge/documents', schema: '/admin/training/schemas',
  metric: '/admin/semantic/metrics', relation: '/admin/semantic/relations',
  synonym: '/admin/training/synonyms', example: '/admin/training/sql-examples',
  golden: '/admin/training/golden-questions',
}
const params = (domain: string) => ({ params: { domain } })

export const trainingApi = {
  dashboard: (domain: string) => http.get<TrainingDashboard>('/admin/training/dashboard', params(domain)),
  schemas: (domain: string) => http.get<DataRow[]>('/admin/training/schemas', params(domain)),
  metrics: (domain: string) => http.get<DataRow[]>('/admin/semantic/metrics', params(domain)),
  relations: (domain: string) => http.get<DataRow[]>('/admin/semantic/relations', params(domain)),
  synonyms: (domain: string) => http.get<DataRow[]>('/admin/training/synonyms', params(domain)),
  examples: (domain: string) => http.get<DataRow[]>('/admin/training/sql-examples', params(domain)),
  golden: (domain: string) => http.get<DataRow[]>('/admin/training/golden-questions', params(domain)),
  feedback: (domain: string) => http.get<DataRow[]>('/admin/training/feedback', params(domain)),
  documents: (domain: string) => http.get<DataRow[]>('/admin/knowledge/documents', params(domain)),
  create: (resource: Exclude<TrainingResource, 'document'>, body: DataRow) => http.post(endpointByResource[resource], body),
  update: (resource: TrainingResource, id: number, body: DataRow, domain: string) => resource === 'document'
    ? http.put(`${endpointByResource[resource]}/${id}`, body, params(domain))
    : http.put(`${endpointByResource[resource]}/${id}`, body),
  delete: (resource: TrainingResource, id: number, domain: string) => http.delete(`${endpointByResource[resource]}/${id}`, params(domain)),
  publishMetric: (id: number, domain: string) => http.post(`/admin/semantic/metrics/${id}/publish`, null, params(domain)),
  publishExample: (id: number, domain: string) => http.post(`/admin/training/sql-examples/${id}/publish`, null, params(domain)),
  runGolden: (id: number, domain: string) => http.post<DataRow>(`/admin/training/golden-questions/${id}/run`, null, params(domain)),
  promoteFeedback: (id: number, domain: string) => http.post(`/admin/training/feedback/${id}/promote`, null, params(domain)),
  uploadDocument: (file: File, domain: string) => {
    const form = new FormData()
    form.append('file', file)
    return http.post(`/admin/knowledge/documents?domain=${encodeURIComponent(domain)}`, form)
  },
}
