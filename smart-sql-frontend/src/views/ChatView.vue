<template>
  <div class="chat-container">
    <!-- 左侧会话面板 -->
    <SessionPanel
      @select="handleSelectSession"
      @create="handleCreateSession"
    />

    <!-- 右侧问答区 -->
    <div class="chat-main">
      <!-- 顶部：知识库入口 -->
      <div class="chat-header">
        <el-button type="text" @click="$router.push('/knowledge')">
          <el-icon><FolderOpened /></el-icon> 知识库管理
        </el-button>
        <span class="header-title">智能SQL 问答助手</span>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="chatStore.messages.length === 0" class="welcome">
          <h2>👋 欢迎使用智能SQL</h2>
          <p>我可以帮你：</p>
          <ul>
            <li>🔍 <b>代码生成</b> — 用自然语言描述需求，自动生成SQL</li>
            <li>📝 <b>代码注释</b> — 为SQL添加业务注释</li>
            <li>📊 <b>快捷找表</b> — 搜索匹配的数据表</li>
            <li>📋 <b>快捷找代码</b> — 搜索计算口径和任务代码</li>
          </ul>
        </div>

        <ChatMessage
          v-for="(msg, index) in chatStore.messages"
          :key="index"
          :message="msg"
          :index="index"
          @feedback="handleFeedback"
        />
      </div>

      <!-- 底部输入区 -->
      <div class="chat-input-area">
        <!-- 上下文标签 -->
        <div v-if="chatStore.contextList.length > 0" class="context-tags">
          <el-tag
            v-for="(ctx, i) in chatStore.contextList"
            :key="i"
            closable
            size="small"
            @close="chatStore.contextList.splice(i, 1)"
          >
            {{ ctx.name }}
          </el-tag>
        </div>

        <!-- 命令快捷键 -->
        <div class="command-bar">
          <el-radio-group v-model="chatStore.commandType" size="small">
            <el-radio-button value="generate">代码生成</el-radio-button>
            <el-radio-button value="explain">代码注释</el-radio-button>
            <el-radio-button value="find_table">快捷找表</el-radio-button>
            <el-radio-button value="find_code">快捷找代码</el-radio-button>
          </el-radio-group>

          <el-button size="small" @click="showContextSelector = !showContextSelector">
            <el-icon><Plus /></el-icon> 添加上下文
          </el-button>
        </div>

        <!-- 输入框 -->
        <div class="input-row">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="请直接输入要提问的问题，亦可先输入 / 指定需要我完成的任务类型。"
            :maxlength="99999"
            show-word-limit
            @keydown.enter.exact="handleSend"
          />
          <el-button
            type="primary"
            :disabled="!inputText.trim() || chatStore.isStreaming"
            @click="handleSend"
            class="send-btn"
          >
            <el-icon><Promotion /></el-icon> 发送
          </el-button>

          <!-- 作业题2修复：生成中显示停止按钮 -->
          <el-button
            v-if="chatStore.isStreaming"
            type="danger"
            @click="handleStop"
            class="send-btn"
          >
            <el-icon><VideoPause /></el-icon> 停止
          </el-button>
        </div>
      </div>

      <!-- 上下文选择弹窗 -->
      <ContextSelector
        v-if="showContextSelector"
        @close="showContextSelector = false"
        @select="handleAddContext"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/store/chat'
import { useSessionStore } from '@/store/session'
import { chatStream } from '@/api/chat'
import { listMessages } from '@/api/session'
import SessionPanel from '@/components/SessionPanel.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ContextSelector from '@/components/ContextSelector.vue'

const chatStore = useChatStore()
const sessionStore = useSessionStore()

const inputText = ref('')
const showContextSelector = ref(false)
const messagesContainer = ref(null)
let abortController = null
let streamingIndex = -1   // 作业题2修复：记录正在生成的 AI 消息索引，停止时使用

/** 处理发送 */
const handleSend = async () => {
  const question = inputText.value.trim()
  if (!question || chatStore.isStreaming) return

  // 如果没有会话，自动创建
  if (!chatStore.currentSessionId) {
    await sessionStore.create()
    chatStore.setSession(sessionStore.currentId)
  }

  // 添加用户消息
  chatStore.addUserMessage(question)
  inputText.value = ''
  chatStore.setStreaming(true)

  // 添加 AI 消息壳
  const aiMsgIndex = chatStore.addAssistantMessage()
  streamingIndex = aiMsgIndex

  await scrollToBottom()

  // 发送 SSE 请求
  abortController = chatStream(
    chatStore.currentSessionId,
    {
      question,
      commandType: chatStore.commandType,
      context: chatStore.contextList
    },
    // onMessage
    (data) => {
      if (data.type === 'content') {
        chatStore.appendContent(aiMsgIndex, data.content)
        scrollToBottom()
      } else if (data.type === 'thinking') {
        chatStore.appendThinking(aiMsgIndex, data.content)
      } else if (data.type === 'done') {
        chatStore.finishStreaming(aiMsgIndex)
      } else if (data.type === 'error') {
        chatStore.appendContent(aiMsgIndex, `\n\n❌ 错误：${data.content}`)
        chatStore.finishStreaming(aiMsgIndex)
      }
    },
    // onError
    (err) => {
      chatStore.appendContent(aiMsgIndex, `\n\n❌ 请求失败：${err.message}`)
      chatStore.finishStreaming(aiMsgIndex)
    },
    // onComplete
    () => {
      chatStore.finishStreaming(aiMsgIndex)
      streamingIndex = -1
    }
  )
}

/** 停止生成（作业题2修复：调用 AbortController.abort() 真正中断请求） */
const handleStop = () => {
  if (abortController) {
    abortController.abort()   // 中断 fetch 流（AbortError 不会触发 onError 的报错分支）
    abortController = null
  }
  if (streamingIndex >= 0) {
    chatStore.stopStreaming(streamingIndex)
    streamingIndex = -1
  }
}

/** 选择会话 */
const handleSelectSession = async (sessionId) => {
  chatStore.setSession(sessionId)
  const res = await listMessages(sessionId)
  chatStore.setMessages(res.data || [])
  await scrollToBottom()
}

/** 创建会话 */
const handleCreateSession = async () => {
  const session = await sessionStore.create()
  chatStore.setSession(session.id)
  chatStore.setMessages([])
  inputText.value = ''
}

/** 添加上下文 */
const handleAddContext = (item) => {
  if (chatStore.contextList.length >= 20) {
    ElMessage.warning('最多添加20个上下文')
    return
  }
  if (!chatStore.contextList.find(c => c.id === item.id)) {
    chatStore.contextList.push(item)
  }
}

/** 反馈 */
const handleFeedback = (index, type) => {
  ElMessage.success(type === 'like' ? '感谢您的点赞！' : '感谢您的反馈，我们会持续改进！')
}

/** 滚动到底部 */
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}
</script>

<style lang="scss" scoped>
.chat-container {
  display: flex;
  height: 100vh;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #ebeef5;
  .header-title {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.welcome {
  text-align: center;
  margin-top: 80px;
  h2 { margin-bottom: 16px; color: #409eff; }
  p { color: #606266; margin-bottom: 12px; }
  ul {
    list-style: none;
    text-align: left;
    max-width: 500px;
    margin: 0 auto;
    li {
      padding: 8px 0;
      color: #606266;
      font-size: 14px;
    }
  }
}

.chat-input-area {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.context-tags {
  margin-bottom: 8px;
  .el-tag { margin-right: 6px; }
}

.command-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  .el-textarea { flex: 1; }
  .send-btn {
    height: 60px;
    width: 80px;
    font-size: 16px;
  }
}
</style>
