<template>
  <!-- 历史会话浮框：从底部浮起（需求文档：历史会话框从底部浮框显示） -->
  <div class="history-overlay" @click.self="$emit('close')">
    <div class="history-panel">
      <!-- 头部 -->
      <div class="history-header">
        <h4>历史会话</h4>
        <el-button text @click="$emit('close')">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>

      <!-- 查询框 -->
      <div class="history-search">
        <el-input
          v-model="keyword"
          placeholder="请输入关键词搜索历史记录"
          size="small"
          clearable
          @keyup.enter="loadSessions"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #append>
            <el-button @click="loadSessions">搜索</el-button>
          </template>
        </el-input>
      </div>

      <!-- 内容展示 -->
      <div class="history-list" v-loading="loading">
        <el-empty v-if="sessionStore.sessions.length === 0" description="没有数据" :image-size="60" />

        <div
          v-for="s in sessionStore.sessions"
          :key="s.id"
          :class="['history-item', { current: s.id === sessionStore.currentId }]"
          @click="$emit('open', s.id)"
        >
          <div class="item-main">
            <div class="item-name">
              <el-tag v-if="s.id === sessionStore.currentId" size="small" type="success" class="current-tag">
                当前
              </el-tag>
              <span class="name-text">{{ s.sessionName || '新会话' }}</span>
            </div>
            <div class="item-time">{{ formatTime(s.updateTime) }}</div>
          </div>

          <!-- 悬浮显示操作按钮 -->
          <div class="item-actions" @click.stop>
            <el-button size="small" text @click="handleRename(s)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button size="small" text type="danger" @click="handleDelete(s)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 底部提示（需求原文：仅保留7天内的对话，当前总共xxx条记录） -->
      <div class="history-footer">
        仅保留7天内的对话，当前总共 {{ sessionStore.sessions.length }} 条记录
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSessionStore } from '@/store/session'
import { useChatStore } from '@/store/chat'
import { renameSession, deleteSession } from '@/api/session'

const emit = defineEmits(['close', 'open'])

const sessionStore = useSessionStore()
const chatStore = useChatStore()
const keyword = ref('')
const loading = ref(false)

// 作业题2修复：删除本地 sessionList 快照，模板直接消费响应式 store
// （快照副本制造"两份数据要同步"的问题，问题本不该存在）
const loadSessions = async () => {
  loading.value = true
  try {
    await sessionStore.loadSessions(keyword.value)
  } finally {
    loading.value = false
  }
}

/** 格式化时间 */
const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now - d
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString()
}

/** 重命名 */
const handleRename = async (session) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
      inputValue: session.sessionName
    })
    if (value) {
      await renameSession(session.id, value)
      ElMessage.success('已保存')
      loadSessions()
    }
  } catch { /* 用户取消 */ }
}

/** 删除 */
const handleDelete = async (session) => {
  try {
    await ElMessageBox.confirm('删除当前对话，将不保留任何信息和记录，确认删除吗？', '确认删除', {
      type: 'warning'
    })
    await deleteSession(session.id)
    ElMessage.success('已删除')

    // 作业题3修复：删除的是当前会话时，重置聊天区状态并关闭浮框
    // （否则聊天区残留已删除会话的消息，用户还能继续向"已删除会话"发问）
    if (session.id === sessionStore.currentId) {
      chatStore.setSession(null)
      chatStore.setMessages([])
      emit('close')
    }
    loadSessions()
  } catch { /* 用户取消 */ }
}

onMounted(loadSessions)
</script>

<style lang="scss" scoped>
.history-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: flex-end;
  z-index: 1000;
}

.history-panel {
  background: #fff;
  border-radius: 12px 12px 0 0;
  width: 640px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  animation: slide-up 0.25s ease;
}

@keyframes slide-up {
  from { transform: translateY(30px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid #ebeef5;
  h4 { margin: 0; font-size: 15px; }
}

.history-search {
  padding: 12px 20px;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px;
  min-height: 200px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: #f0f5ff;
    .item-actions { display: flex; }
  }

  &.current {
    background: #ecf5ff;
    border: 1px solid #b3d8ff;
  }

  .item-name {
    display: flex;
    align-items: center;
    gap: 6px;
    .name-text {
      font-size: 14px;
      color: #303133;
      max-width: 420px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .item-time {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}

.item-actions {
  display: none;
  gap: 2px;
}

.history-footer {
  padding: 10px 20px;
  border-top: 1px solid #ebeef5;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
