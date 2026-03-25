import { http } from './request'
import api from './api'

export interface KnowledgeBase {
  knowledgeBaseId: number
  knowledgeBaseName: string
  description: string | null
  userId: number
  embeddingConfigId: number | null
  embeddingModelName: string | null
  state: string
  createTime: string
  updateTime: string
}

export interface KnowledgeDoc {
  docId: number
  knowledgeBaseId: number
  userId: number
  docName: string
  docType: string
  filePath: string
  fileSize: number
  chunkCount: number
  charCount: number
  status: string
  errorMsg: string | null
  state: string
  createTime: string
  updateTime: string
}

export interface KnowledgeBaseQueryParams {
  knowledgeBaseName?: string
  start?: number
  limit?: number
}

export interface KnowledgeDocQueryParams {
  knowledgeBaseId?: number
  docName?: string
  status?: string
  docType?: string
  start?: number
  limit?: number
}

/**
 * 查询知识库列表
 */
export function queryKnowledgeBases(params: Partial<KnowledgeBaseQueryParams>) {
  return http.get<KnowledgeBase[]>(api.knowledge.baseQuery, params)
}

/**
 * 创建知识库
 */
export function createKnowledgeBase(data: { knowledgeBaseName: string; description?: string; embeddingConfigId?: number }) {
  return http.postJSON<KnowledgeBase>(api.knowledge.baseAdd, data)
}

/**
 * 更新知识库
 */
export function updateKnowledgeBase(data: { knowledgeBaseId: number; knowledgeBaseName?: string; description?: string; embeddingConfigId?: number }) {
  return http.putJSON(api.knowledge.baseUpdate, data)
}

/**
 * 删除知识库
 */
export function deleteKnowledgeBase(knowledgeBaseId: number) {
  return http.delete(`${api.knowledge.baseDelete}/${knowledgeBaseId}`)
}

/**
 * 查询知识库文档列表
 */
export function queryKnowledgeDocs(params: Partial<KnowledgeDocQueryParams>) {
  return http.get<KnowledgeDoc[]>(api.knowledge.docQuery, params)
}

/**
 * 上传知识库文档
 */
export function uploadKnowledgeDoc(file: File, knowledgeBaseId: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('knowledgeBaseId', String(knowledgeBaseId))
  return http.upload<KnowledgeDoc>(api.knowledge.docUpload, formData)
}

/**
 * 删除知识库文档
 */
export function deleteKnowledgeDoc(docId: number) {
  return http.delete(`${api.knowledge.docDelete}/${docId}`)
}

/**
 * 重新处理文档
 */
export function reprocessKnowledgeDoc(docId: number) {
  return http.postJSON(`${api.knowledge.docReprocess}/${docId}/reprocess`, {})
}

/**
 * 查询文档处理状态（用于轮询）
 */
export function getDocStatus(docId: number) {
  return http.get<KnowledgeDoc>(`${api.knowledge.docStatus}/${docId}/status`)
}
