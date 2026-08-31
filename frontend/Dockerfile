# ============================================================
# 溯知 / TraceQA 前端镜像（Nuxt 4 SSR）
# 多阶段构建：阶段一编译（Node），阶段二运行（Nitro Node Server）
# ============================================================

# ---- 构建阶段 ----
FROM node:26-alpine AS build
WORKDIR /app

# 使用国内 npm 镜像源
RUN npm config set registry https://registry.npmmirror.com

# 启用 corepack 并指定 pnpm 版本（避免 corepack 去 npmjs.org 查询）
RUN npm install -g corepack && corepack enable
ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
RUN corepack prepare pnpm@latest --activate

# 使用国内 pnpm 镜像源
RUN pnpm config set registry https://registry.npmmirror.com

# 拷贝源码并构建（API 代理目标在构建期注入，指向网关容器）
COPY . .
ARG API_PROXY_TARGET=http://gateway:8080
ENV API_PROXY_TARGET=$API_PROXY_TARGET
RUN pnpm build

# ---- 运行阶段 ----
FROM node:26-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV NITRO_PORT=3000
ENV NITRO_HOST=0.0.0.0

# 拷贝构建产物
COPY --from=build /app/.output .output

# 非 root 运行
RUN chown -R node:node /app

USER node

EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1:3000/ >/dev/null 2>&1 || exit 1

CMD ["node", ".output/server/index.mjs"]
