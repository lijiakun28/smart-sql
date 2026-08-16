<template>
  <div class="chat-container">
    <!-- 左侧会话面板 -->
    <SessionPanel
      @select="handleSelectSession"
      @create="handleCreateSession"
    />

    <!-- 右侧问答区 -->
    <div class="chat-main">
      <!-- 顶部：知识库入口 + 历史对话 -->
      <div class="chat-header">
        <div class="header-left">
          <el-button type="text" @click="$router.push('/knowledge')">
            <el-icon><FolderOpened /></el-icon> 知识库管理
          </el-button>
        </div>
        <span class="header-title">智能SQL 问答助手</span>
        <div class="header-right">
          <el-button type="text" @click="showHistory = true">
            <el-icon><Clock /></el-icon> 历史对话
          </el-button>
        </div>
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
          @regenerate="handleRegenerate"
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
            @keydown.enter.exact="handleEnterKey"
          />
          <el-button
            type="primary"
            :disabled="!inputText.trim() || chatStore.isStreaming"
            @click="handleSend()"
            class="send-btn"
          >
            <el-icon><Promotion /></el-icon> 发送
          </el-button>

          <!-- 修复：生成中显示停止按钮 -->
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

      <!-- 上下文选择弹窗（修复：传 selectedIds 供子组件置灰已添加条目） -->
      <ContextSelector
        v-if="showContextSelector"
        :selectedIds="chatStore.contextList.map(c => c.id)"
        @close="showContextSelector = false"
        @select="handleAddContext"
      />

      <!-- 历史会话浮框 -->
      <HistorySessions
        v-if="showHistory"
        @close="showHistory = false"
        @open="handleOpenHistory"
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
import HistorySessions from '@/components/HistorySessions.vue'

const chatStore = useChatStore()
const sessionStore = useSessionStore()

const inputText = ref('')
const showContextSelector = ref(false)
const showHistory = ref(false)
const messagesContainer = ref(null)
let abortController = null
let streamingIndex = -1   // 修复：记录正在生成的 AI 消息索引，停止时使用
let sessionRequestSeq = 0   // 修复：会话切换请求令牌，消除竞态

/** 回车发送（修复：中文输入法组词中按 Enter 确认候选词也会触发
 * keydown.enter——此时文字还没提交进 v-model，handleSend 读到空串静默返回，
 * 表现为"敲了字按发送键没反应"。用 isComposing 过滤组词中的 Enter） */
const handleEnterKey = (e) => {
  if (e.isComposing) return   // 输入法组词确认，不是发送意图
  handleSend()
}

/** 处理发送
 * @param questionOverride 重新回答时传入原问题：跳过添加用户消息、不覆盖输入框
 *
 * 修复：模板 @click 会自动把 MouseEvent 作为第一个参数传入，
 * 导致 questionOverride 变成事件对象、.trim() 报 TypeError。
 * 防御性判断 + 模板显式 @click="handleSend()" 双保险
 */
const handleSend = async (questionOverride) => {
  const override = typeof questionOverride === 'string' ? questionOverride : null
  const question = (override || inputText.value).trim()
  if (!question || chatStore.isStreaming) return

  // 如果没有会话，自动创建
  if (!chatStore.currentSessionId) {
    await sessionStore.create()
    chatStore.setSession(sessionStore.currentId)
  }

  if (!override) {
    // 正常发送：添加用户消息 + 清空输入框
    chatStore.addUserMessage(question)
    inputText.value = ''
  }
  // 重新回答（修复）：复用原问题，不重复落库用户消息、不覆盖输入框
  chatStore.setStreaming(true)

  // 添加 AI 消息壳
  const aiMsgIndex = chatStore.addAssistantMessage()
  streamingIndex = aiMsgIndex

  scrollToBottom(true)

  // 发送 SSE 请求
  // 修复：context 必须传对象（后端/Python 期望 dict），不能传数组
  abortController = chatStream(
    chatStore.currentSessionId,
    {
      question,
      commandType: chatStore.commandType,
      context: chatStore.contextList.length > 0
        ? { items: chatStore.contextList }
        : {},
      // 重新回答标记：后端跳过重复保存用户消息
      isRegenerate: !!override
    },
    // onMessage
    (data) => {
      if (data.type === 'content') {
        chatStore.appendContent(aiMsgIndex, data.content)
        scrollToBottom()
      } else if (data.type === 'thinking') {
        chatStore.appendThinking(aiMsgIndex, data.content)
      } else if (data.type === 'knowledge') {
        chatStore.appendKnowledge(aiMsgIndex, data)
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

/** 停止生成（修复：调用 AbortController.abort() 真正中断请求） */
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

/** 选择会话（修复：请求令牌消除竞态——只有最新请求允许更新消息列表） */
const handleSelectSession = async (sessionId) => {
  const seq = ++sessionRequestSeq   // 发起时取号
  chatStore.setSession(sessionId)

  const res = await listMessages(sessionId)
  if (seq !== sessionRequestSeq) {
    return   // 期间用户点了别的会话，本次响应作废
  }
  chatStore.setMessages(res.data || [])
  scrollToBottom(true)   // 切换会话必须定位到底部
}

/** 创建会话 */
const handleCreateSession = async () => {
  const session = await sessionStore.create()
  chatStore.setSession(session.id)
  chatStore.setMessages([])
  inputText.value = ''
}

/** 打开历史会话（需求：支持继续对选中的会话进行问答） */
const handleOpenHistory = async (sessionId) => {
  showHistory.value = false
  await handleSelectSession(sessionId)
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

/** 重新回答（修复：复用原问题直接发送，不重复落库、不覆盖输入框）
 * 历史形态变为：用户X → AI旧 → AI新（一问多答，与 ChatGPT 语义一致） */
const handleRegenerate = (aiMsgIndex) => {
  for (let i = aiMsgIndex - 1; i >= 0; i--) {
    if (chatStore.messages[i]?.role === 'user') {
      handleSend(chatStore.messages[i].content)
      return
    }
  }
  ElMessage.warning('未找到可重新回答的问题')
}

/** 滚动到底部
 * 修复（双武器）：
 * ① rAF 节流：多个 chunk 的滚动请求合并到每帧一次，消除 Layout Thrashing
 * ② 滚动锁：用户主动上滑查看历史时（距底部 >40px）暂停自动滚动，尊重用户意图
 */
let scrollPending = false
const scrollToBottom = (force = false) => {
  // 滚动锁：用户主动上滑查看历史时（距底部 >40px）暂停自动滚动
  // force=true 用于切换会话/首条消息等必须定位到底部的场景
  const el = messagesContainer.value
  if (!force && el && el.scrollHeight - el.scrollTop - el.clientHeight > 40) {
    return
  }
  if (scrollPending) return   // 一帧内只排队一次
  scrollPending = true
  requestAnimationFrame(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
    scrollPending = false
  })
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
  .header-left, .header-right { min-width: 110px; }
  .header-right { display: flex; justify-content: flex-end; }
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
