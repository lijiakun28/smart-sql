import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 统一错误提示
 *
 * 约定：所有用户可见的错误提示由本拦截器负责。
 * 调用方只需 try/catch 后提前返回，不要重复弹提示。
 * 例外：SSE 走的是 fetch（见 api/chat.js），不经过 axios。
 */
const notifyError = (message) => {
  ElMessage.error(message || '请求失败')
}

// 响应拦截
request.interceptors.response.use(
  response => {
    const res = response.data
    // 后端统一响应体是 {code, message, data}，只有 code === 200 才算成功。
    //
    // ⚠️ 之前这里不判业务码、直接把 res 交给调用方，
    // 而后端异常分支返回的是 HTTP 200 + body code=500，
    // 于是"后端报错"被当成"操作成功"——全项目只有两处自己检查了 code，
    // 其余调用点在后端失败时照样弹"已保存 / 已删除"。
    if (res && typeof res.code === 'number' && res.code !== 200) {
      notifyError(res.message)
      return Promise.reject(new Error(res.message || `业务失败 code=${res.code}`))
    }
    return res
  },
  error => {
    console.error('请求失败:', error)
    // HTTP 层错误也在这一处统一提示：
    // - 400 来自 ConstraintViolationException，响应体仍是 Result，能取到 message
    // - 其余（超时 / 断连）退化为 axios 自带消息
    notifyError(error?.response?.data?.message || error?.message)
    return Promise.reject(error)
  }
)

export default request
