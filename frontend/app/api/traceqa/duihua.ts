// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 逻辑删除单条消息 DELETE /api/chat/messages/${param0} */
export async function deleteMessage(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteMessageParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/chat/messages/${param0}`, {
    method: "DELETE",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 查询会话列表 GET /api/chat/sessions */
export async function listSessions(options?: { [key: string]: any }) {
  return request<API.ApiResponseListSessionVO>("/api/chat/sessions", {
    method: "GET",
    ...(options || {}),
  });
}

/** 创建会话 POST /api/chat/sessions */
export async function createSession(
  body: API.SessionCreateRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseSessionVO>("/api/chat/sessions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 逻辑删除会话 DELETE /api/chat/sessions/${param0} */
export async function deleteSession(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteSessionParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/chat/sessions/${param0}`, {
    method: "DELETE",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 导出会话为 Markdown GET /api/chat/sessions/${param0}/export */
export async function exportMarkdown(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.exportMarkdownParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<string>(`/api/chat/sessions/${param0}/export`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 查询会话消息 GET /api/chat/sessions/${param0}/messages */
export async function listMessages(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listMessagesParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseListChatMessageVO>(
    `/api/chat/sessions/${param0}/messages`,
    {
      method: "GET",
      params: { ...queryParams },
      ...(options || {}),
    }
  );
}

/** 置顶/取消置顶会话 PUT /api/chat/sessions/${param0}/pin */
export async function togglePin(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.togglePinParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/chat/sessions/${param0}/pin`, {
    method: "PUT",
    params: {
      ...queryParams,
    },
    ...(options || {}),
  });
}
