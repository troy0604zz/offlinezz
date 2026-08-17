export interface ChartSpec {
  type: 'kpi' | 'bar' | 'line' | 'none'
  title: string
  categoryField: string | null
  valueFields: string[]
  reason: string
}

export interface QueryAnswer {
  queryRunId: string
  question: string
  status: string
  sql: string
  explanation: string
  assumptions: string[]
  confidence: number
  tables: string[]
  rows: Record<string, unknown>[]
  chart: ChartSpec
  answer: string
  llmProvider: string
  vectorProvider: string
  elapsedMs: number
}

export interface QueryFeedback {
  rating: number
  comment: string
  correctedSql: string | null
}

export type QueryExportFormat = 'xlsx' | 'csv' | 'xml'
