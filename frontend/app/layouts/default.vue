<template>
  <a-layout class="app-layout">
    <a-layout-header class="app-header">
      <div class="app-header__brand" @click="navigateTo('/')">
        <span class="app-header__logo">溯</span>
        <span class="app-header__title">溯知 · TraceQA</span>
      </div>
      <div class="app-header__nav">
        <a-button
          v-for="item in staticNav"
          :key="item.path"
          type="text"
          class="app-header__nav-btn"
          :class="{ 'is-active': isActive(item.path) }"
          @click="navigateTo(item.path)"
        >
          <component :is="item.icon" class="app-header__nav-icon" />
          {{ item.label }}
        </a-button>
        <ClientOnly>
          <a-button
            v-if="auth.isAdmin"
            type="text"
            class="app-header__nav-btn"
            :class="{ 'is-active': isActive('/admin') }"
            @click="navigateTo('/admin')"
          >
            <SettingOutlined class="app-header__nav-icon" />
            管理后台
          </a-button>
        </ClientOnly>
      </div>
      <div class="app-header__user">
        <ClientOnly>
          <template v-if="auth.isLoggedIn">
            <a-dropdown>
              <a-space class="app-header__user-info">
                <a-avatar v-if="auth.userInfo?.avatar" :size="28" :src="auth.userInfo.avatar" />
                <a-avatar v-else size="28" style="background: linear-gradient(135deg, #1677ff, #06b6d4)">
                  {{ (auth.userInfo?.nickname || 'U').charAt(0) }}
                </a-avatar>
                <span>{{ auth.userInfo?.nickname || auth.userInfo?.username }}</span>
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="personal" @click="navigateTo('/personal')">
                    <UserOutlined />
                    个人信息
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout" @click="handleLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
          <template v-else>
            <a-button type="primary" size="small" @click="navigateTo('/login')">登录</a-button>
          </template>
        </ClientOnly>

        <!-- 移动端：展开导航抽屉 -->
        <a-button class="app-header__menu-btn" type="text" @click="drawerOpen = true">
          <template #icon><MenuOutlined /></template>
        </a-button>
      </div>
    </a-layout-header>
    <a-layout-content class="app-content">
      <slot />
    </a-layout-content>

    <!-- 移动端导航抽屉 -->
    <a-drawer v-model:open="drawerOpen" placement="right" :width="240" title="导航" :closable="false">
      <div class="app-drawer-nav">
        <a-button
          v-for="item in navItems"
          :key="item.path"
          block
          size="large"
          type="text"
          class="app-drawer-nav__item"
          :class="{ 'is-active': isActive(item.path) }"
          @click="navigateTo(item.path); drawerOpen = false"
        >
          <component :is="item.icon" />
          {{ item.label }}
        </a-button>
      </div>
    </a-drawer>

    </a-layout>
</template>

<script setup lang="ts">
/**
 * 默认布局：顶栏（品牌 + 导航 + 用户菜单）+ 内容区 + 移动端导航抽屉。
 */
import {
  LogoutOutlined,
  UserOutlined,
  HomeOutlined,
  MessageOutlined,
  SettingOutlined,
  MenuOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const drawerOpen = ref(false)

const staticNav = [
  { label: '首页', path: '/', icon: HomeOutlined },
  { label: '智能问答', path: '/chat', icon: MessageOutlined }
]

const navItems = computed(() =>
  [
    { label: '首页', path: '/', icon: HomeOutlined },
    { label: '智能问答', path: '/chat', icon: MessageOutlined },
    { label: '管理后台', path: '/admin', icon: SettingOutlined, adminOnly: true }
  ].filter((n) => !n.adminOnly || auth.isAdmin)
)

/** 当前路由是否命中导航项（首页匹配根路径） */
function isActive(path: string): boolean {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}

async function handleLogout(): Promise<void> {
  auth.logout()
  await navigateTo('/login')
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 24px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 0 24px;
  line-height: normal;
}

.app-header__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.app-header__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1677ff, #06b6d4);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.3);
}

.app-header__title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.app-header__nav {
  flex: 1;
  display: flex;
  gap: 4px;
}

.app-header__nav-btn {
  color: #4e5969;
  transition: all 0.2s;
}

.app-header__nav-btn:hover {
  color: #1677ff;
}

.app-header__nav-btn.is-active {
  color: #1677ff;
  font-weight: 600;
  background: #e6f4ff;
}

.app-header__nav-icon {
  margin-right: 2px;
}

.app-header__user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-header__user-info {
  cursor: pointer;
  color: #1f2329;
}

.app-header__menu-btn {
  display: none;
}

.app-drawer-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.app-drawer-nav__item {
  text-align: left;
  border-radius: 8px;
}

.app-drawer-nav__item.is-active {
  color: #1677ff;
  font-weight: 600;
  background: #e6f4ff;
}

.app-content {
  min-height: calc(100vh - 64px);
  animation: tqFadeIn 0.4s ease both;
}

/* 移动端：顶栏紧凑 + 显示汉堡按钮 */
@media (max-width: 768px) {
  .app-header {
    padding: 0 12px;
    gap: 8px;
  }

  .app-header__title {
    font-size: 14px;
  }

  .app-header__nav {
    display: none;
  }

  .app-header__menu-btn {
    display: inline-flex;
  }
}
</style>