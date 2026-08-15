declare namespace API {
  type AdminUserVO = {
    id?: number;
    username?: string;
    nickname?: string;
    roleCode?: string;
    status?: number;
    createTime?: string;
  };

  type ApiResponseDocumentUploadVO = {
    code?: number;
    msg?: string;
    data?: DocumentUploadVO;
    traceId?: string;
  };

  type ApiResponseKnowledgeBaseDTO = {
    code?: number;
    msg?: string;
    data?: KnowledgeBaseDTO;
    traceId?: string;
  };

  type ApiResponseListChatMessageVO = {
    code?: number;
    msg?: string;
    data?: ChatMessageVO[];
    traceId?: string;
  };

  type ApiResponseListDocumentVO = {
    code?: number;
    msg?: string;
    data?: DocumentVO[];
    traceId?: string;
  };

  type ApiResponseListKnowledgeBaseDTO = {
    code?: number;
    msg?: string;
    data?: KnowledgeBaseDTO[];
    traceId?: string;
  };

  type ApiResponseListModelVO = {
    code?: number;
    msg?: string;
    data?: ModelVO[];
    traceId?: string;
  };

  type ApiResponseListRoleDTO = {
    code?: number;
    msg?: string;
    data?: RoleDTO[];
    traceId?: string;
  };

  type ApiResponseListSessionVO = {
    code?: number;
    msg?: string;
    data?: SessionVO[];
    traceId?: string;
  };

  type ApiResponseListSystemPromptDTO = {
    code?: number;
    msg?: string;
    data?: SystemPromptDTO[];
    traceId?: string;
  };

  type ApiResponseLoginResponse = {
    code?: number;
    msg?: string;
    data?: LoginResponse;
    traceId?: string;
  };

  type ApiResponseMapStringLong = {
    code?: number;
    msg?: string;
    data?: Record<string, any>;
    traceId?: string;
  };

  type ApiResponseMapStringString = {
    code?: number;
    msg?: string;
    data?: Record<string, any>;
    traceId?: string;
  };

  type ApiResponsePageResultAdminUserVO = {
    code?: number;
    msg?: string;
    data?: PageResultAdminUserVO;
    traceId?: string;
  };

  type ApiResponsePageResultDocumentVO = {
    code?: number;
    msg?: string;
    data?: PageResultDocumentVO;
    traceId?: string;
  };

  type ApiResponsePageResultKnowledgeBaseDTO = {
    code?: number;
    msg?: string;
    data?: PageResultKnowledgeBaseDTO;
    traceId?: string;
  };

  type ApiResponseRoleDTO = {
    code?: number;
    msg?: string;
    data?: RoleDTO;
    traceId?: string;
  };

  type ApiResponseSessionVO = {
    code?: number;
    msg?: string;
    data?: SessionVO;
    traceId?: string;
  };

  type ApiResponseSystemPromptDTO = {
    code?: number;
    msg?: string;
    data?: SystemPromptDTO;
    traceId?: string;
  };

  type ApiResponseUserInfo = {
    code?: number;
    msg?: string;
    data?: UserInfo;
    traceId?: string;
  };

  type ApiResponseVoid = {
    code?: number;
    msg?: string;
    data?: any;
    traceId?: string;
  };

  type ChatMessageVO = {
    id?: number;
    sessionId?: number;
    role?: string;
    content?: string;
    thinkingTrace?: ThinkingNodeVO[];
    references?: ReferenceVO[];
    latencyMs?: number;
    createTime?: string;
  };

  type ChatStreamRequest = {
    sessionId?: number;
    knowledgeBaseId?: number;
    content: string;
    model?: string;
    baseUrl?: string;
    apiKey?: string;
  };

  type delete1Params = {
    id: number;
  };

  type delete2Params = {
    id: number;
  };

  type deleteMessageParams = {
    id: number;
  };

  type deleteSessionParams = {
    id: number;
  };

  type deleteUsingDELETEParams = {
    id: number;
  };

  type docCountParams = {
    id: number;
  };

  type DocumentUploadVO = {
    documentId?: number;
    trackId?: string;
  };

  type DocumentVO = {
    id?: number;
    knowledgeBaseId?: number;
    originalName?: string;
    fileType?: string;
    fileSize?: number;
    status?: string;
    chunkCount?: number;
    entityCount?: number;
    relationCount?: number;
    errorMsg?: string;
    createTime?: string;
    updateTime?: string;
  };

  type enableParams = {
    id: number;
  };

  type exportMarkdownParams = {
    id: number;
  };

  type KnowledgeBaseDTO = {
    id?: number;
    name: string;
    description?: string;
    course?: string;
    status?: number;
    createTime?: string;
  };

  type listByKbParams = {
    knowledgeBaseId: number;
  };

  type listMessagesParams = {
    id: number;
  };

  type LoginRequest = {
    username: string;
    password: string;
  };

  type LoginResponse = {
    token?: string;
    userInfo?: UserInfo;
  };

  type ModelVO = {
    name?: string;
    model?: string;
    baseUrl?: string;
    isDefault?: boolean;
  };

  type page1Params = {
    page?: number;
    size?: number;
  };

  type pageParams = {
    knowledgeBaseId?: number;
    page?: number;
    size?: number;
  };

  type PageResultAdminUserVO = {
    page?: number;
    size?: number;
    total?: number;
    records?: AdminUserVO[];
  };

  type PageResultDocumentVO = {
    page?: number;
    size?: number;
    total?: number;
    records?: DocumentVO[];
  };

  type PageResultKnowledgeBaseDTO = {
    page?: number;
    size?: number;
    total?: number;
    records?: KnowledgeBaseDTO[];
  };

  type pageUsersParams = {
    keyword?: string;
    page?: number;
    size?: number;
  };

  type PasswordChangeRequest = {
    oldPassword: string;
    newPassword: string;
  };

  type progressParams = {
    id: number;
  };

  type ReferenceVO = {
    index?: number;
    title?: string;
    filePath?: string;
    content?: string;
  };

  type RegisterRequest = {
    username: string;
    password: string;
    nickname?: string;
  };

  type RoleChangeRequest = {
    roleCode: string;
  };

  type RoleDTO = {
    id?: number;
    code?: string;
    name?: string;
    permissions?: string;
    description?: string;
    updateTime?: string;
  };

  type SessionCreateRequest = {
    title?: string;
    knowledgeBaseId?: number;
  };

  type SessionVO = {
    id?: number;
    title?: string;
    knowledgeBaseId?: number;
    pinned?: number;
    createTime?: string;
    updateTime?: string;
  };

  type SseEmitter = {
    timeout?: number;
  };

  type SystemPromptDTO = {
    id?: number;
    scenario: string;
    name: string;
    content?: string;
    enabled?: number;
    remark?: string;
    updateTime?: string;
  };

  type ThinkingNodeVO = {
    stage?: string;
    agent?: string;
    status?: string;
    message?: string;
    detail?: string;
  };

  type togglePinParams = {
    id: number;
    pinned: boolean;
  };

  type update1Params = {
    id: number;
  };

  type updateParams = {
    id: number;
  };

  type updateRole1Params = {
    id: number;
  };

  type updateRoleParams = {
    id: number;
  };

  type updateStatusParams = {
    id: number;
    status: number;
  };

  type uploadParams = {
    knowledgeBaseId: number;
  };

  type UserInfo = {
    userId?: number;
    username?: string;
    nickname?: string;
    roleCode?: string;
    status?: number;
    permissions?: string[];
  };
}
