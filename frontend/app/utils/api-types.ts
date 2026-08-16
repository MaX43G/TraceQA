/**
 * OpenAPI 生成类型的统一出口。
 *
 * <p>生成的 {@code typings.d.ts} 以全局命名空间 {@code API} 声明类型，
 * 无法直接命名导入；此处统一 re-export 供业务代码引用。</p>
 *
 * <p>说明：后端将 Long（雪花 ID）序列化为字符串，但 OpenAPI 生成的类型仍为
 * {@code number}，两者在运行时由后端字符串转 Long 自动兼容，业务代码按生成类型使用即可。</p>
 */
export type SessionVO = API.SessionVO
export type ChatMessageVO = API.ChatMessageVO
export type ThinkingNodeVO = API.ThinkingNodeVO
export type ReferenceVO = API.ReferenceVO
export type UserInfo = API.UserInfo
export type KnowledgeBaseDTO = API.KnowledgeBaseDTO
export type DocumentVO = API.DocumentVO
export type DocumentUploadVO = API.DocumentUploadVO
export type SystemPromptDTO = API.SystemPromptDTO
export type AdminUserVO = API.AdminUserVO
export type RoleDTO = API.RoleDTO
export type ModelVO = API.ModelVO
