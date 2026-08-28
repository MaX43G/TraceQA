// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 查询可用模型列表 GET /api/models */
export async function list2(options?: { [key: string]: any }) {
    return request<API.ApiResponseListModelVO>("/api/models", {
        method: "GET",
        ...(options || {}),
    });
}
