// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 查询知识库列表 GET /api/kbs */
export async function list1(options?: { [key: string]: any }) {
    return request<API.ApiResponseListKnowledgeBaseDTO>("/api/kbs", {
        method: "GET",
        ...(options || {}),
    });
}

/** 创建知识库 POST /api/kbs */
export async function create1(
    body: API.KnowledgeBaseDTO,
    options?: { [key: string]: any }
) {
    return request<API.ApiResponseKnowledgeBaseDTO>("/api/kbs", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        data: body,
        ...(options || {}),
    });
}

/** 更新知识库 PUT /api/kbs/${param0} */
export async function update1(
    // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
    params: API.update1Params,
    body: API.KnowledgeBaseDTO,
    options?: { [key: string]: any }
) {
    const {id: param0, ...queryParams} = params;
    return request<API.ApiResponseKnowledgeBaseDTO>(`/api/kbs/${param0}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
        },
        params: {...queryParams},
        data: body,
        ...(options || {}),
    });
}

/** 删除知识库 DELETE /api/kbs/${param0} */
export async function delete1(
    // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
    params: API.delete1Params,
    options?: { [key: string]: any }
) {
    const {id: param0, ...queryParams} = params;
    return request<API.ApiResponseVoid>(`/api/kbs/${param0}`, {
        method: "DELETE",
        params: {...queryParams},
        ...(options || {}),
    });
}
