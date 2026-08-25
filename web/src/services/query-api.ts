import type { QueryAnswer, QueryExportFormat, QueryFeedback } from '../types/query'
import { http } from './http'

export const queryApi = {
  ask: (question: string, knowledgeDomain: string) =>
    http.post<QueryAnswer>('/questions', { question, knowledgeDomain }),
  feedback: (queryRunId: string, feedback: QueryFeedback) =>
    http.post(`/query-runs/${queryRunId}/feedback`, feedback),
  download: (queryRunId: string, format: QueryExportFormat) =>
    http.get<Blob>(`/query-runs/${queryRunId}/export`, { params: { format }, responseType: 'blob' }),
}
