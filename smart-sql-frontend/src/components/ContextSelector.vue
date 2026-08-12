<template>
  <div class="context-selector-overlay" @click.self="$emit('close')">
    <div class="context-selector">
      <div class="selector-header">
        <h4>添加上下文</h4>
        <el-button text @click="$emit('close')">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>

      <el-tabs v-model="activeTab">
        <!-- 表 -->
        <el-tab-pane label="表" name="table">
          <el-input
            v-model="tableKeyword"
            placeholder="请输入关键词搜索数据表"
            size="small"
            clearable
            @keyup.enter="searchTable"
          >
            <template #append>
              <el-button @click="searchTable">搜索</el-button>
            </template>
          </el-input>
          <div class="result-list">
            <div
              v-for="item in tableResults"
              :key="item.id"
              class="result-item"
              @click="$emit('select', item)"
            >
              <span>{{ item.name }}</span>
              <el-icon><Plus /></el-icon>
            </div>
            <el-empty v-if="tableResults.length === 0 && tableSearched" description="未找到匹配表" />
          </div>
        </el-tab-pane>

        <!-- 代码 -->
        <el-tab-pane label="代码" name="code">
          <el-input
            v-model="codeKeyword"
            placeholder="请输入关键词搜索代码"
            size="small"
            clearable
            @keyup.enter="searchCode"
          >
            <template #append>
              <el-button @click="searchCode">搜索</el-button>
            </template>
          </el-input>
          <div class="result-list">
            <div
              v-for="item in codeResults"
              :key="item.id"
              class="result-item"
              @click="$emit('select', item)"
            >
              <span>{{ item.name }}</span>
              <el-icon><Plus /></el-icon>
            </div>
            <el-empty v-if="codeResults.length === 0 && codeSearched" description="未找到匹配代码" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { searchKnowledge } from '@/api/knowledge'

defineEmits(['close', 'select'])

const activeTab = ref('table')
const tableKeyword = ref('')
const codeKeyword = ref('')
const tableResults = ref([])
const codeResults = ref([])
const tableSearched = ref(false)
const codeSearched = ref(false)

const searchTable = async () => {
  if (!tableKeyword.value.trim()) return
  const res = await searchKnowledge(tableKeyword.value)
  tableResults.value = (res.data || []).filter(i => i.type === 'table' || i.type === 'term')
  tableSearched.value = true
}

const searchCode = async () => {
  if (!codeKeyword.value.trim()) return
  const res = await searchKnowledge(codeKeyword.value)
  codeResults.value = (res.data || []).filter(i => i.type === 'code' || i.type === 'caliber')
  codeSearched.value = true
}
</script>

<style lang="scss" scoped>
.context-selector-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.context-selector {
  background: #fff;
  border-radius: 12px;
  width: 560px;
  max-height: 480px;
  overflow: hidden;
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
  h4 { margin: 0; }
}

.el-tabs {
  padding: 0 20px;
}

.result-list {
  max-height: 280px;
  overflow-y: auto;
  margin-top: 10px;
}

.result-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 6px;
  &:hover { background: #f0f5ff; }
}
</style>
