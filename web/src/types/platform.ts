export interface PlatformInfo {
  aiMode: string
  llmProvider: string
  vectorProvider: string
  chatProvider: string
  embeddingProvider: string
  chatModel: string
  embeddingModel: string
  vectorSize: number
}

export type ModelProvider = 'ollama' | 'qwen-api'

export interface ModelRuntime {
  mode: string
  activeChatProvider: ModelProvider
  activeEmbeddingProvider: ModelProvider
  activeChatModel: string
  activeEmbeddingModel: string
  ollamaChatModel: string
  ollamaEmbeddingModel: string
  qwenChatModel: string
  qwenEmbeddingModel: string
  qwenApiConfigured: boolean
  qwenApiHost: string
  vectorSize: number
}

export interface ModelRuntimeUpdate {
  chatProvider: ModelProvider
  embeddingProvider: ModelProvider
  qwenChatModel: string
  qwenEmbeddingModel: string
  reindexKnowledge: boolean
}

export interface ModelRuntimeUpdateResult {
  runtime: ModelRuntime
  embeddingChanged: boolean
  knowledgeReindexed: boolean
}
