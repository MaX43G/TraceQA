// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 分页查询文档列表 GET /api/documents */
export async function page(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.pageParams,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponsePageResultDocumentVO>("/api/documents", {
    method: "GET",
    params: {
      // page has a default value: 1
      page: "1",
      // size has a default value: 10
      size: "10",
      ...params,
    },
    ...(options || {}),
  });
}

/** 上传文档（异步解析，立即返回 202） POST /api/documents */
export async function upload(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.uploadParams,
  body: {},
  file?: File,
  options?: { [key: string]: any }
) {
  const formData = new FormData();

  if (file) {
    formData.append("file", file);
  }

  Object.keys(body).forEach((ele) => {
    const item = (body as any)[ele];

    if (item !== undefined && item !== null) {
      if (typeof item === "object" && !(item instanceof File)) {
        if (item instanceof Array) {
          item.forEach((f) => formData.append(ele, f || ""));
        } else {
          formData.append(
            ele,
            new Blob([JSON.stringify(item)], { type: "application/json" })
          );
        }
      } else {
        formData.append(ele, item);
      }
    }
  });

  return request<API.ApiResponseDocumentUploadVO>("/api/documents", {
    method: "POST",
    params: {
      ...params,
    },
    data: formData,
    requestType: "form",
    ...(options || {}),
  });
}

/** 逻辑删除文档 DELETE /api/documents/${param0} */
export async function delete2(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.delete2Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/documents/${param0}`, {
    method: "DELETE",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 文档解析进度 SSE（先补发快照，再轮询推送） GET /api/documents/${param0}/progress */
export async function progress(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.progressParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.SseEmitter>(`/api/documents/${param0}/progress`, {
    method: "GET",
    params: { ...queryParams },
    ...(options || {}),
  });
}

/** 查询知识库下全部文档 GET /api/documents/by-kb */
export async function listByKb(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listByKbParams,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseListDocumentVO>("/api/documents/by-kb", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
