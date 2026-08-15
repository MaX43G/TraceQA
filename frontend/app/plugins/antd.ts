import Antd from 'ant-design-vue'

/**
 * Ant Design Vue 全局注册插件。
 *
 * <p>Nuxt 服务端渲染与客户端水合时均注册组件库；
 * antd 内部已兼容 SSR，避免重复注册造成告警。</p>
 */
export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.use(Antd)
})
