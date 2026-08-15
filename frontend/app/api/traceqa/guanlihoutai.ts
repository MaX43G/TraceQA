// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 查询全部角色 GET /api/admin/roles */
export async function listRoles(options?: { [key: string]: any }) {
  return request<API.ApiResponseListRoleDTO>("/api/admin/roles", {
    method: "GET",
    ...(options || {}),
  });
}

/** 更新角色权限 PUT /api/admin/roles/${param0} */
export async function updateRole1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateRole1Params,
  body: API.RoleDTO,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseRoleDTO>(`/api/admin/roles/${param0}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  });
}

/** 分页查询用户 GET /api/admin/users */
export async function pageUsers(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.pageUsersParams,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponsePageResultAdminUserVO>("/api/admin/users", {
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

/** 变更用户角色 PUT /api/admin/users/${param0}/role */
export async function updateRole(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateRoleParams,
  body: API.RoleChangeRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/admin/users/${param0}/role`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  });
}

/** 启用/禁用用户 PUT /api/admin/users/${param0}/status */
export async function updateStatus(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateStatusParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params;
  return request<API.ApiResponseVoid>(`/api/admin/users/${param0}/status`, {
    method: "PUT",
    params: {
      ...queryParams,
    },
    ...(options || {}),
  });
}
