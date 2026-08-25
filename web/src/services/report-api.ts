import type { GeneratedReport, ReportListItem } from '../types/report'
import { http } from './http'

export const reportApi = {
  generate: (title: string, request: string, knowledgeDomain: string) =>
    http.post<GeneratedReport>('/reports/generate', { title, request, knowledgeDomain }),
  list: (domain: string) => http.get<ReportListItem[]>('/reports', { params: { domain } }),
  detail: (id: string) => http.get<GeneratedReport>(`/reports/${id}`),
}
