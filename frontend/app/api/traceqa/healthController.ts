// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 此处后端没有提供注释 GET /api/health */
export async function health(options?: { [key: string]: any }) {
  return request<API.ApiResponseMapStringString>("/api/health", {
    method: "GET",
    ...(options || {}),
  });
}
