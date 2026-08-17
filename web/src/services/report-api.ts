import type { GeneratedReport, ReportListItem } from '../types/report'
import { http } from './http'

export const reportApi = {
  generate: (title: string, request: string, knowledgeDomain = 'sales') =>
    http.post<GeneratedReport>('/reports/generate', { title, request, knowledgeDomain }),
  list: () => http.get<ReportListItem[]>('/reports'),
}
