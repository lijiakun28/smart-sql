import request from './request'

/** 创建会话 */
export const createSession = (name = '新会话') =>
  request.post('/sessions', null, { params: { name } })

/** 会话列表 */
export const listSessions = (keyword) =>
  request.get('/sessions', { params: keyword ? { keyword } : {} })

/** 改名 */
export const renameSession = (id, name) =>
  request.put(`/sessions/${id}/name`, null, { params: { name } })

/** 删除 */
export const deleteSession = (id) =>
  request.delete(`/sessions/${id}`)

/** 消息列表 */
export const listMessages = (sessionId) =>
  request.get(`/sessions/${sessionId}/messages`)
