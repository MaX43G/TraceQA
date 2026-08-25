// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 查询全部系统提示词 GET /api/prompts */
export async function list(options?: { [key: string]: any }) {
  return request<API.ApiResponseListSystemPromptDTO>("/api/prompts", {
    method: "GET",
    ...(options || {}),
  });
}

/** 更新系统提示词 PUT /api/prompts/${param0} */
export async function update(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateParams,
  body: API.SystemPromptDTO,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseSystemPromptDTO>(`/api/prompts/${param0}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  });
}

/** 启用指定提示词（自动停用同场景其他项） PUT /api/prompts/${param0}/enable */
export async function enable(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.enableParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/prompts/${param0}/enable`, {
    method: "PUT",
    params: { ...queryParams },
    ...(options || {}),
  });
}
