import request from './request'

/** 术语库列表 */
export const listTerms = (params) => request.get('/knowledge/terms', { params })

/** 新增术语 */
export const createTerm = (data) => request.post('/knowledge/terms', data)

/** 修改术语 */
export const updateTerm = (id, data) => request.put(`/knowledge/terms/${id}`, data)

/** 删除术语 */
export const deleteTerm = (id) => request.delete(`/knowledge/terms/${id}`)

/** 术语状态变更 */
export const changeTermStatus = (id, status) =>
  request.put(`/knowledge/terms/${id}/status`, null, { params: { status } })

/** 口径库列表 */
export const listCalibers = (params) => request.get('/knowledge/calibers', { params })

/** 新增口径 */
export const createCaliber = (data) => request.post('/knowledge/calibers', data)

/** 修改口径 */
export const updateCaliber = (id, data) => request.put(`/knowledge/calibers/${id}`, data)

/** 删除口径 */
export const deleteCaliber = (id) => request.delete(`/knowledge/calibers/${id}`)

/** 口径状态变更 */
export const changeCaliberStatus = (id, status) =>
  request.put(`/knowledge/calibers/${id}/status`, null, { params: { status } })

/** 知识库搜索（上下文用） */
export const searchKnowledge = (keyword) =>
  request.get('/knowledge/search', { params: { keyword } })
