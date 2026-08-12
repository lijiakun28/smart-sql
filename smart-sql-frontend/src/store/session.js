import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listSessions, createSession as apiCreate } from '@/api/session'

/**
 * 会话列表状态管理
 */
export const useSessionStore = defineStore('session', () => {
  const sessions = ref([])
  const currentId = ref(null)

  /** 加载会话列表 */
  async function loadSessions(keyword) {
    const res = await listSessions(keyword)
    sessions.value = res.data || []
    return sessions.value
  }

  /** 创建新会话 */
  async function create(name) {
    const res = await apiCreate(name)
    const session = res.data
    sessions.value.unshift(session)
    currentId.value = session.id
    return session
  }

  /** 切换会话 */
  function switchSession(id) {
    currentId.value = id
  }

  return {
    sessions,
    currentId,
    loadSessions,
    create,
    switchSession
  }
})
