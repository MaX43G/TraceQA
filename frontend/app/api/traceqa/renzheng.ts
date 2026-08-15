// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** 用户登录 POST /api/auth/login */
export async function login(
  body: API.LoginRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseLoginResponse>("/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 查询当前登录用户 GET /api/auth/me */
export async function me(options?: { [key: string]: any }) {
  return request<API.ApiResponseUserInfo>("/api/auth/me", {
    method: "GET",
    ...(options || {}),
  });
}

/** 修改当前用户密码 PUT /api/auth/password */
export async function changePassword(
  body: API.PasswordChangeRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseVoid>("/api/auth/password", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

/** 用户注册 POST /api/auth/register */
export async function register(
  body: API.RegisterRequest,
  options?: { [key: string]: any }
) {
  return request<API.ApiResponseUserInfo>("/api/auth/register", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}
