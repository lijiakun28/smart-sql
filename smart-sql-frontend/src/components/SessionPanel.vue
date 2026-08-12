<template>
  <div class="session-panel">
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
      >
        <div class="session-name">{{ s.sessionName || '新会话' }}</div>
        <div class="session-time">{{ formatTime(s.updateTime) }}</div>

        <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, s)">
          <el-icon class="more-icon"><MoreFilled /></el-icon>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="rename">
                <el-icon><Edit /></el-icon> 重命名
              </el-dropdown-item>
              <el-dropdown-item command="delete" divided>
                <el-icon><Delete /></el-icon> 删除
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-if="sessionStore.sessions.length === 0" class="empty-tip">
        暂无会话
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSessionStore } from '@/store/session'
import { renameSession, deleteSession } from '@/api/session'

const emit = defineEmits(['select', 'create'])
const sessionStore = useSessionStore()

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

/** 操作命令 */
const handleCommand = async (cmd, session) => {
  if (cmd === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
        inputValue: session.sessionName
      })
      if (value) {
        await renameSession(session.id, value)
        sessionStore.loadSessions()
      }
    } catch {}
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm('删除当前对话，将不保留任何信息和记录，确认删除吗？', '确认删除', {
        type: 'warning'
      })
      await deleteSession(session.id)
      ElMessage.success('已删除')
      sessionStore.loadSessions()
    } catch {}
  }
}
</script>

<style lang="scss" scoped>
.session-panel {
  width: 260px;
  background: #f0f2f5;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
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
    padding-right: 24px;
  }

  .session-time {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }

  .more-icon {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    display: none;
    color: #909399;
  }

  &:hover .more-icon { display: block; }
}

.empty-tip {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
  font-size: 13px;
}
</style>
