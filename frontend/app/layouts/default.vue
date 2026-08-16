<template>
  <a-layout class="app-layout">
    <a-layout-header class="app-header">
      <div class="app-header__brand" @click="navigateTo('/')">
        <span class="app-header__logo">溯</span>
        <span class="app-header__title">溯知 · TraceQA</span>
      </div>
      <div class="app-header__nav">
        <a-space>
          <a-button type="text" @click="navigateTo('/')">首页</a-button>
          <a-button type="text" @click="navigateTo('/chat')">智能问答</a-button>
          <a-button v-if="auth.isAdmin" type="text" @click="navigateTo('/admin')">
            管理后台
          </a-button>
        </a-space>
      </div>
      <div class="app-header__user">
        <template v-if="auth.isLoggedIn">
          <a-dropdown>
            <a-space class="app-header__user-info">
              <a-avatar size="small" style="background-color: #1677ff">
                {{ (auth.userInfo?.nickname || 'U').charAt(0) }}
              </a-avatar>
              <span>{{ auth.userInfo?.nickname || auth.userInfo?.username }}</span>
            </a-space>
            <template #overlay>
              <a-menu>
                <a-menu-item key="nickname" @click="showNicknameModal = true">
                  <EditOutlined />
                  修改昵称
                </a-menu-item>
                <a-menu-item key="password" @click="showPasswordModal = true">
                  <KeyOutlined />
                  修改密码
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
        <a-button v-else type="primary" size="small" @click="navigateTo('/login')">登录</a-button>
      </div>
    </a-layout-header>
    <a-layout-content class="app-content">
      <slot />
    </a-layout-content>

    <ChangePasswordModal v-model:open="showPasswordModal" />
    <NicknameModal v-model:open="showNicknameModal" />
  </a-layout>
</template>

<script setup lang="ts">
/**
 * 默认布局：顶栏（品牌 + 导航 + 用户菜单）+ 内容区 + 修改密码/昵称弹窗。
 */
import { EditOutlined, KeyOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import ChangePasswordModal from '@/components/auth/ChangePasswordModal.vue'
import NicknameModal from '@/components/auth/NicknameModal.vue'

const auth = useAuthStore()
const showPasswordModal = ref(false)
const showNicknameModal = ref(false)

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
  background: #fff;
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
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: linear-gradient(135deg, #1677ff, #06b6d4);
  color: #fff;
  font-weight: 700;
}

.app-header__title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.app-header__nav {
  flex: 1;
}

.app-header__user-info {
  cursor: pointer;
  color: #1f2329;
}

/* 移动端：顶栏紧凑 */
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
}
</style>
