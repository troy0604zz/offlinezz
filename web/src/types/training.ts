export type TrainingResource =
  | 'schema'
  | 'metric'
  | 'relation'
  | 'synonym'
  | 'example'
  | 'golden'

export interface TrainingDashboard {
  documents: number
  schemas: number
  metrics: number
  relations: number
  synonyms: number
  sqlExamples: number
  goldenQuestions: number
  feedback: number
  lastEvaluation?: string
}

export type DataRow = Record<string, unknown>

export interface TrainingState {
  dashboard: Partial<TrainingDashboard>
  documents: DataRow[]
  schemas: DataRow[]
  metrics: DataRow[]
  relations: DataRow[]
  synonyms: DataRow[]
  examples: DataRow[]
  golden: DataRow[]
  feedback: DataRow[]
}
