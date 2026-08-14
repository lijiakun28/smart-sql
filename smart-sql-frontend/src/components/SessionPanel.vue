<template>
  <div class="session-panel" @click="contextMenu.visible = false">
    <div class="panel-header">
      <el-button type="primary" size="small" @click="$emit('create')">
        <el-icon><Plus /></el-icon> 新建会话
      </el-button>
    </div>

    <div class="session-list">
      <div
        v-for="s in sessionStore.sessions"
        :key="s.id"
        :class="['session-item', { active: s.id === sessionStore.currentId }]"
        @click="$emit('select', s.id)"
        @contextmenu.prevent="openContextMenu($event, s)"
      >
        <div class="session-name">{{ s.sessionName || '新会话' }}</div>
        <div class="session-time">{{ formatTime(s.updateTime) }}</div>
        <div class="hint-text">右键菜单</div>
      </div>

      <div v-if="sessionStore.sessions.length === 0" class="empty-tip">
        暂无会话
      </div>
    </div>

    <!-- 右键菜单（用户反馈：重命名/删除操作不便，改为右键呼出） -->
    <div
      v-if="contextMenu.visible"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <div class="menu-item" @click="handleRename(contextMenu.session)">
        <el-icon><Edit /></el-icon> 重命名
      </div>
      <div class="menu-item danger" @click="handleDelete(contextMenu.session)">
        <el-icon><Delete /></el-icon> 删除
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSessionStore } from '@/store/session'
import { renameSession, deleteSession } from '@/api/session'

const emit = defineEmits(['select', 'create'])
const sessionStore = useSessionStore()

/** 右键菜单状态 */
const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  session: null
})

onMounted(() => {
  sessionStore.loadSessions()
})

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

/** 打开右键菜单 */
const openContextMenu = (event, session) => {
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.session = session
}

/** 重命名 */
const handleRename = async (session) => {
  contextMenu.visible = false
  if (!session) return
  try {
    const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
      inputValue: session.sessionName
    })
    if (value) {
      await renameSession(session.id, value)
      ElMessage.success('已保存')
      sessionStore.loadSessions()
    }
  } catch { /* 用户取消 */ }
}

/** 删除 */
const handleDelete = async (session) => {
  contextMenu.visible = false
  if (!session) return
  try {
    await ElMessageBox.confirm('删除当前对话，将不保留任何信息和记录，确认删除吗？', '确认删除', {
      type: 'warning'
    })
    await deleteSession(session.id)
    ElMessage.success('已删除')
    sessionStore.loadSessions()
  } catch { /* 用户取消 */ }
}
</script>

<style lang="scss" scoped>
.session-panel {
  width: 260px;
  background: #f0f2f5;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  position: relative;
}

.panel-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
  cursor: pointer;
  position: relative;
  transition: background 0.2s;

  &:hover { background: #e6f0ff; }
  &.active { background: #d9ecff; border-left: 3px solid #409eff; }

  .session-name {
    font-size: 14px;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    padding-right: 8px;
  }

  .session-time {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }

  .hint-text {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    font-size: 11px;
    color: #c0c4cc;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .hint-text { opacity: 1; }
}

.empty-tip {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
  font-size: 13px;
}

/* 右键菜单 */
.context-menu {
  position: fixed;
  z-index: 2000;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 120px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  transition: background 0.15s;

  &:hover { background: #f0f5ff; }

  &.danger {
    color: #f56c6c;
    border-top: 1px solid #f2f3f5;
    &:hover { background: #fef0f0; }
  }
}
</style>
