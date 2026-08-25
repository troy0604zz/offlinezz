import type { QueryAnswer } from './query'

export interface ReportSection {
  title: string
  question: string
  query?: QueryAnswer
  error?: string
}

export interface GeneratedReport {
  id: string
  domain: string
  title: string
  request: string
  executiveSummary: string
  sections: ReportSection[]
  recommendations: string[]
  warnings?: string[]
  generatedAt: string
  generatedBy: string
}

export interface ReportListItem {
  id: string
  domain: string
  title: string
  request_text: string
  status: string
  error_message?: string
  created_by: string
  created_at: string
  can_delete: boolean
}

export type ReportExportFormat = 'pdf' | 'docx'
