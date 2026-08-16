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
        <!-- 思考过程（用户反馈：展开图标不可见——自定义可见箭头按钮） -->
        <div v-if="message.thinking" class="thinking-collapse">
          <div class="thinking-header" @click="showThinking = !showThinking">
            <el-icon class="arrow" :class="{ rotated: showThinking }">
              <ArrowRight />
            </el-icon>
            <span>💭 思考过程</span>
          </div>
          <div v-if="showThinking">
            <pre class="thinking-text">{{ message.thinking }}</pre>

            <!-- 知识库引用（可点击查看详情，每个知识库有序号+点击浮框展示只读页面） -->
            <div v-if="message.sources && message.sources.length" class="knowledge-refs">
              <div class="refs-title">📚 知识库引用</div>
              <div
                v-for="src in message.sources"
                :key="src.index"
                class="knowledge-ref"
                @click="openKnowledgeDetail(src)"
              >
                <el-tag size="small" type="info">[{{ src.index }}]</el-tag>
                <span class="ref-name">{{ src.name }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 知识详情浮框（修复：布尔管开关、对象管数据，两个变量单一语义） -->
        <el-dialog
          v-model="knowledgeDialogVisible"
          :title="'知识详情 - ' + (currentKnowledge ? currentKnowledge.name : '')"
          width="520px"
          append-to-body
        >
          <div class="knowledge-detail">
            <p class="detail-label">业务描述</p>
            <p class="detail-text">{{ currentKnowledge ? (currentKnowledge.description || '无') : '' }}</p>
            <p v-if="currentKnowledge && currentKnowledge.sql" class="detail-label">SQL 逻辑</p>
            <pre v-if="currentKnowledge && currentKnowledge.sql" class="detail-sql">{{ currentKnowledge.sql }}</pre>
          </div>
        </el-dialog>

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

        <!-- 普通文本：v-text 渲染，杜绝 XSS（修复） -->
        <div class="text-content" v-text="message.content ? stripSqlBlock(message.content) : ''"></div>

        <!-- 流式加载指示 -->
        <span v-if="message.isStreaming" class="streaming-cursor">▊</span>

        <!-- 操作按钮（用户反馈：图标纯白与背景融合——加可见颜色和背景） -->
        <div v-if="!message.isStreaming && message.role === 'assistant'" class="action-buttons">
          <el-button size="small" class="feedback-btn" @click="$emit('feedback', index, 'like')">
            <el-icon><ThumbUp /></el-icon> 有用
          </el-button>
          <el-button size="small" class="feedback-btn" @click="$emit('feedback', index, 'dislike')">
            <el-icon><ThumbDown /></el-icon> 没用
          </el-button>
          <!-- 重新回答（针对回答过的问题，再次进行回答） -->
          <el-button size="small" class="feedback-btn" @click="$emit('regenerate', index)">
            <el-icon><Refresh /></el-icon> 重新回答
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import hljs from 'highlight.js/lib/core'
import sql from 'highlight.js/lib/languages/sql'
// 用户反馈：github.css 是浅色主题（黑字），SQL 块是深色背景 → 换深色主题
import 'highlight.js/styles/github-dark.css'

hljs.registerLanguage('sql', sql)

/** 思考过程展开状态 */
const showThinking = ref(false)
/** 知识详情浮框（修复：拆分状态，布尔管开关、对象管数据） */
const knowledgeDialogVisible = ref(false)
const currentKnowledge = ref(null)

/** 打开知识详情 */
const openKnowledgeDetail = (src) => {
  currentKnowledge.value = src
  knowledgeDialogVisible.value = true
}

const props = defineProps({
  message: { type: Object, required: true },
  index: { type: Number, required: true }
})

defineEmits(['feedback', 'regenerate'])

/** 命令类型标签 */
const commandLabel = (type) => ({
  generate: '代码生成',
  explain: '代码注释',
  find_table: '快捷找表',
  find_code: '快捷找代码'
}[type] || type)

/**
 * SQL 代码块正则（修复：忽略大小写 + 允许空格）
 * ⚠️ 提取与删除必须用不同标志：
 *   - match()[1] 取捕获组需要【无 g 标志】（带 g 时 match 只返回完整匹配数组）
 *   - replace 删除全部代码块需要【g 标志】
 * 浏览器实测发现：共用带 g 的正则导致 match[1] === undefined，SQL 永不渲染
 */
const SQL_BLOCK_EXTRACT = /```\s*sql\s*([\s\S]*?)```/i
const SQL_BLOCK_REMOVE_ALL = /```\s*sql\s*[\s\S]*?```/gi

/** 提取 SQL 代码块 */
const extractSql = (content) => {
  if (!content) return ''
  const match = content.match(SQL_BLOCK_EXTRACT)
  return match ? match[1] : ''
}

/** 去掉 SQL 代码块，返回纯文本 */
const stripSqlBlock = (content) => {
  return (content || '').replace(SQL_BLOCK_REMOVE_ALL, '').trim()
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
    code {
      font-size: 13px;
      /* 基础色设为白色：highlight.js 只给关键字上色，
         表名/列名/条件等标识符继承此颜色，深色背景上清晰可读 */
      color: #ffffff;
    }
  }
}

.thinking-collapse {
  margin-bottom: 12px;

  .thinking-header {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    font-size: 13px;
    color: #606266;
    user-select: none;

    .arrow {
      transition: transform 0.2s;
      color: #409eff;
      &.rotated { transform: rotate(90deg); }
    }
  }

  .thinking-text {
    margin-top: 8px;
    font-size: 12px;
    color: #909399;
    white-space: pre-wrap;
    background: #fafafa;
    padding: 10px;
    border-radius: 6px;
  }
}

/* 知识库引用 */
.knowledge-refs {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f0f7ff;
  border-radius: 6px;

  .refs-title {
    font-size: 12px;
    color: #409eff;
    margin-bottom: 6px;
  }

  .knowledge-ref {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 0;
    cursor: pointer;
    font-size: 13px;

    .ref-name {
      color: #409eff;
      text-decoration: underline;
      &:hover { color: #337ecc; }
    }
  }
}

/* 知识详情浮框 */
.knowledge-detail {
  .detail-label {
    font-size: 13px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 4px;
  }
  .detail-text {
    font-size: 13px;
    color: #303133;
    line-height: 1.8;
    margin-bottom: 12px;
  }
  .detail-sql {
    background: #f5f7fa;
    padding: 10px;
    border-radius: 6px;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

.text-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  /* 修复：v-text 渲染后靠 CSS 保留换行 */
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
  display: flex;
  gap: 8px;

  /* 修复：图标纯白与背景融合 → 明确颜色+边框可见 */
  .feedback-btn {
    color: #606266;
    border: 1px solid #dcdfe6;
    background: #fff;

    .el-icon { color: #909399; }
    &:hover {
      color: #409eff;
      border-color: #409eff;
      .el-icon { color: #409eff; }
    }
  }
}
</style>
