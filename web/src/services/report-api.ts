import type { GeneratedReport, ReportExportFormat, ReportListItem } from '../types/report'
import { http } from './http'

export const reportApi = {
  generate: (title: string, request: string, knowledgeDomain: string) =>
    http.post<GeneratedReport>('/reports/generate', { title, request, knowledgeDomain }),
  list: (domain: string) => http.get<ReportListItem[]>('/reports', { params: { domain } }),
  detail: (id: string) => http.get<GeneratedReport>(`/reports/${id}`),
  download: (id: string, format: ReportExportFormat) =>
    http.get<Blob>(`/reports/${id}/export`, { params: { format }, responseType: 'blob' }),
  delete: (id: string) => http.delete<{ deleted: boolean; id: string }>(`/reports/${id}`),
}
