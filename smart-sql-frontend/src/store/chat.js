import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 问答状态管理
 */
export const useChatStore = defineStore('chat', () => {
  // 当前会话ID
  const currentSessionId = ref(null)

  // 当前会话消息列表
  const messages = ref([])

  // 消息发送中
  const isStreaming = ref(false)

  // 当前命令类型
  const commandType = ref('generate')

  // 已选上下文
  const contextList = ref([])

  /** 设置会话 */
  function setSession(sessionId) {
    currentSessionId.value = sessionId
  }

  /** 设置消息列表 */
  function setMessages(list) {
    messages.value = list
  }

  /** 添加用户消息 */
  function addUserMessage(content) {
    messages.value.push({
      role: 'user',
      content,
      commandType: commandType.value
    })
  }

  /** 添加 AI 消息（空壳，后续流式填充） */
  function addAssistantMessage() {
    const msg = {
      role: 'assistant',
      content: '',
      thinking: '',
      sources: [],
      isStreaming: true
    }
    messages.value.push(msg)
    return messages.value.length - 1
  }

  /** 追加流式内容 */
  function appendContent(index, content) {
    if (messages.value[index]) {
      messages.value[index].content += content
    }
  }

  /** 追加思考过程 */
  function appendThinking(index, thinking) {
    if (messages.value[index]) {
      messages.value[index].thinking += thinking + '\n'
    }
  }

  /** 完成流式 */
  function finishStreaming(index) {
    if (messages.value[index]) {
      messages.value[index].isStreaming = false
    }
    isStreaming.value = false
  }

  /** 设置流式状态 */
  function setStreaming(val) {
    isStreaming.value = val
  }

  /** 停止生成 */
  function stopStreaming(index) {
    if (messages.value[index]) {
      messages.value[index].isStreaming = false
    }
    isStreaming.value = false
  }

  /** 清除上下文 */
  function clearContext() {
    contextList.value = []
  }

  return {
    currentSessionId,
    messages,
    isStreaming,
    commandType,
    contextList,
    setSession,
    setMessages,
    addUserMessage,
    addAssistantMessage,
    appendContent,
    appendThinking,
    finishStreaming,
    setStreaming,
    stopStreaming,
    clearContext
  }
})
