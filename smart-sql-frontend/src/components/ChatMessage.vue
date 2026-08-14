<template>
  <div :class="['message-item', message.role]">
    <div class="message-avatar">
      <el-avatar :size="32" v-if="message.role === 'user'">
        <el-icon><UserFilled /></el-icon>
      </el-avatar>
      <el-avatar :size="32" v-else style="background: #409eff">
        <el-icon><Cpu /></el-icon>
      </el-avatar>
    </div>

    <div class="message-body">
      <!-- 用户消息 -->
      <div v-if="message.role === 'user'" class="user-content">
        <el-tag v-if="message.commandType" size="small" type="info" class="cmd-tag">
          {{ commandLabel(message.commandType) }}
        </el-tag>
        <pre class="question-text">{{ message.content }}</pre>
      </div>

      <!-- AI 消息 -->
      <div v-else class="ai-content">
        <!-- 思考过程 -->
        <el-collapse v-if="message.thinking" class="thinking-collapse">
          <el-collapse-item title="💭 思考过程">
            <pre class="thinking-text">{{ message.thinking }}</pre>
          </el-collapse-item>
        </el-collapse>

        <!-- SQL 代码（带高亮） -->
        <div class="sql-block" v-if="extractSql(message.content)">
          <div class="sql-header">
            <span>📝 SQL</span>
            <el-button size="small" text @click="copySql">
              <el-icon><CopyDocument /></el-icon> 复制
            </el-button>
          </div>
          <pre><code v-html="highlightedSql"></code></pre>
        </div>

        <!-- 普通文本：v-text 渲染，杜绝 XSS（作业题1修复） -->
        <div class="text-content" v-text="message.content ? stripSqlBlock(message.content) : ''"></div>

        <!-- 流式加载指示 -->
        <span v-if="message.isStreaming" class="streaming-cursor">▊</span>

        <!-- 操作按钮 -->
        <div v-if="!message.isStreaming && message.role === 'assistant'" class="action-buttons">
          <el-button size="small" text @click="$emit('feedback', index, 'like')">
            <el-icon><ThumbUp /></el-icon>
          </el-button>
          <el-button size="small" text @click="$emit('feedback', index, 'dislike')">
            <el-icon><ThumbDown /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import hljs from 'highlight.js/lib/core'
import sql from 'highlight.js/lib/languages/sql'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('sql', sql)

const props = defineProps({
  message: { type: Object, required: true },
  index: { type: Number, required: true }
})

defineEmits(['feedback'])

/** 命令类型标签 */
const commandLabel = (type) => ({
  generate: '代码生成',
  explain: '代码注释',
  find_table: '快捷找表',
  find_code: '快捷找代码'
}[type] || type)

/** SQL 代码块正则（作业题4修复：忽略大小写 + 允许空格，提取与删除共用一份） */
const SQL_BLOCK_PATTERN = /```\s*sql\s*([\s\S]*?)```/gi

/** 提取 SQL 代码块 */
const extractSql = (content) => {
  if (!content) return ''
  const match = content.match(SQL_BLOCK_PATTERN)
  return match ? match[1] : ''
}

/** 去掉 SQL 代码块，返回纯文本 */
const stripSqlBlock = (content) => {
  return (content || '').replace(SQL_BLOCK_PATTERN, '').trim()
}

/** SQL 高亮 */
const highlightedSql = computed(() => {
  const sqlCode = extractSql(props.message.content)
  if (!sqlCode) return ''
  return hljs.highlight(sqlCode, { language: 'sql' }).value
})

/** 复制 SQL */
const copySql = () => {
  const sql = extractSql(props.message.content)
  if (sql) {
    navigator.clipboard.writeText(sql)
  }
}
</script>

<style lang="scss" scoped>
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding: 8px 0;

  &.user { flex-direction: row-reverse; }

  .message-avatar {
    flex-shrink: 0;
  }

  .message-body {
    max-width: 80%;
  }
}

.user-content {
  background: #409eff;
  color: #fff;
  padding: 12px 16px;
  border-radius: 12px 4px 12px 12px;
  .cmd-tag { margin-bottom: 6px; }
  .question-text {
    white-space: pre-wrap;
    word-break: break-word;
    font-family: inherit;
    font-size: 14px;
  }
}

.ai-content {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px 12px 12px 12px;
  border: 1px solid #e4e7ed;
}

.sql-block {
  background: #1e1e1e;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
  .sql-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 16px;
    background: #2d2d2d;
    color: #ccc;
    font-size: 13px;
  }
  pre {
    padding: 16px;
    margin: 0;
    overflow-x: auto;
    code { font-size: 13px; }
  }
}

.thinking-collapse {
  margin-bottom: 12px;
  .thinking-text {
    font-size: 12px;
    color: #909399;
    white-space: pre-wrap;
  }
}

.text-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  /* 作业题1修复：v-text 渲染后靠 CSS 保留换行 */
  white-space: pre-wrap;
  word-break: break-word;
}

.streaming-cursor {
  animation: blink 1s infinite;
  color: #409eff;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.action-buttons {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e4e7ed;
}
</style>
