import type { QueryAnswer } from './query'

export interface ReportSection {
  title: string
  query: QueryAnswer
}

export interface GeneratedReport {
  id: string
  title: string
  executiveSummary: string
  sections: ReportSection[]
  recommendations: string[]
}

export interface ReportListItem {
  id: string
  title: string
  request_text: string
  status: string
  created_at: string
}
