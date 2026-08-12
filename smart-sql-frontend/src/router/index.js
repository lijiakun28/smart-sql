import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('../views/ChatView.vue'),
    meta: { title: '智能SQL - 问答' }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('../views/KnowledgeBase.vue'),
    meta: { title: '智能SQL - 知识库管理' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
