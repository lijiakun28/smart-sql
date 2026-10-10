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
    try {
      const res = await listSessions(keyword)
      sessions.value = res.data || []
    } catch {
      // 错误提示由 request 拦截器统一给出；失败时保持列表原状
    }
    return sessions.value
  }

  /** 创建新会话；失败返回 null（错误提示由 request 拦截器给出） */
  async function create(name) {
    try {
      const res = await apiCreate(name)
      const session = res.data
      if (!session) return null
      sessions.value.unshift(session)
      currentId.value = session.id
      return session
    } catch {
      return null
    }
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
