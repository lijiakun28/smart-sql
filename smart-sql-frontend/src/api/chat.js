import request from './request'

/**
 * SSE 流式问答
 *
 * @param {number} sessionId - 会话ID
 * @param {object} body - { question, commandType, context }
 * @param {function} onMessage - 收到消息回调
 * @param {function} onError - 错误回调
 * @param {function} onComplete - 完成回调
 * @returns {AbortController} - 用于中断请求
 */
export function chatStream(sessionId, body, onMessage, onError, onComplete) {
  const controller = new AbortController()

  fetch(`/api/sessions/${sessionId}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: controller.signal
  })
    .then(async (response) => {
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        // SSE 消息以 \n\n 分隔
        const lines = buffer.split('\n\n')
        buffer = lines.pop() // 保留不完整部分

        for (const line of lines) {
          // 浏览器实测修复：Spring SseEmitter 输出 "data:{json}"（无空格），
          // 标准 SSE 是 "data: {json}"（有空格）——两种都要兼容
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.slice(5).trim())
              onMessage(data)
            } catch (e) {
              // 忽略解析错误
            }
          }
        }
      }
      onComplete && onComplete()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError && onError(err)
      }
    })

  return controller
}

/** 更新消息反馈（作业题3修复：顶部静态导入，去掉无理由的动态 import） */
export const updateFeedback = (messageId, feedback) =>
  request.put(`/messages/${messageId}/feedback`, null, { params: { feedback } })
