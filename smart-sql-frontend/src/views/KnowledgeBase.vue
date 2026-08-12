<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h3>知识库管理</h3>
      <el-button @click="$router.push('/')">
        <el-icon><ArrowLeft /></el-icon> 返回问答
      </el-button>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 业务术语库 -->
      <el-tab-pane label="业务术语库" name="term">
        <div class="toolbar">
          <el-input
            v-model="termKeyword"
            placeholder="搜索术语名称..."
            style="width: 240px"
            size="small"
            clearable
            @keyup.enter="loadTerms"
          />
          <el-button type="primary" size="small" @click="showTermDialog = true">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
        </div>

        <el-table :data="termList" stripe v-loading="termLoading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="termName" label="术语名称" min-width="160" />
          <el-table-column prop="businessDesc" label="业务描述" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="170" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="editTerm(row)">修改</el-button>
              <el-button
                v-if="row.status !== 'active'"
                size="small" text type="success"
                @click="changeTermStatus(row.id, 'active')"
              >生效</el-button>
              <el-button
                v-if="row.status === 'active'"
                size="small" text type="warning"
                @click="changeTermStatus(row.id, 'inactive')"
              >失效</el-button>
              <el-button
                size="small" text type="danger"
                @click="handleDeleteTerm(row)"
                :disabled="row.status === 'active'"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="termPage"
          :total="termTotal"
          :page-size="10"
          layout="total, prev, pager, next"
          @current-change="loadTerms"
          style="margin-top: 16px; justify-content: flex-end;"
        />
      </el-tab-pane>

      <!-- 计算口径库 -->
      <el-tab-pane label="计算口径库" name="caliber">
        <div class="toolbar">
          <el-input
            v-model="caliberKeyword"
            placeholder="搜索口径名称..."
            style="width: 240px"
            size="small"
            clearable
            @keyup.enter="loadCalibers"
          />
          <el-button type="primary" size="small" @click="showCaliberDialog = true">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
        </div>

        <el-table :data="caliberList" stripe v-loading="caliberLoading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="caliberName" label="口径名称" min-width="160" />
          <el-table-column prop="sqlLogic" label="SQL逻辑" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="170" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="editCaliber(row)">修改</el-button>
              <el-button
                v-if="row.status !== 'active'"
                size="small" text type="success"
                @click="changeCaliberStatus(row.id, 'active')"
              >生效</el-button>
              <el-button
                v-if="row.status === 'active'"
                size="small" text type="warning"
                @click="changeCaliberStatus(row.id, 'inactive')"
              >失效</el-button>
              <el-button
                size="small" text type="danger"
                @click="handleDeleteCaliber(row)"
                :disabled="row.status === 'active'"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="caliberPage"
          :total="caliberTotal"
          :page-size="10"
          layout="total, prev, pager, next"
          @current-change="loadCalibers"
          style="margin-top: 16px; justify-content: flex-end;"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 术语编辑弹窗 -->
    <el-dialog
      v-model="showTermDialog"
      :title="editingTerm ? '修改业务术语' : '新增业务术语'"
      width="560px"
      @closed="editingTerm = null"
    >
      <el-form :model="termForm" label-width="80px">
        <el-form-item label="术语名称" required>
          <el-input v-model="termForm.termName" maxlength="200" />
        </el-form-item>
        <el-form-item label="业务描述">
          <el-input v-model="termForm.businessDesc" type="textarea" :rows="4" maxlength="99999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTermDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTerm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 口径编辑弹窗 -->
    <el-dialog
      v-model="showCaliberDialog"
      :title="editingCaliber ? '修改计算口径' : '新增计算口径'"
      width="640px"
      @closed="editingCaliber = null"
    >
      <el-form :model="caliberForm" label-width="80px">
        <el-form-item label="口径名称" required>
          <el-input v-model="caliberForm.caliberName" maxlength="100" />
        </el-form-item>
        <el-form-item label="SQL逻辑">
          <el-input v-model="caliberForm.sqlLogic" type="textarea" :rows="6" maxlength="99999" />
        </el-form-item>
        <el-form-item label="业务描述">
          <el-input v-model="caliberForm.businessDesc" type="textarea" :rows="3" maxlength="99999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCaliberDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCaliber">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listTerms, createTerm, updateTerm, deleteTerm, changeTermStatus,
  listCalibers, createCaliber, updateCaliber, deleteCaliber, changeCaliberStatus
} from '@/api/knowledge'

const activeTab = ref('term')

// 术语库状态
const termKeyword = ref('')
const termList = ref([])
const termTotal = ref(0)
const termPage = ref(1)
const termLoading = ref(false)
const showTermDialog = ref(false)
const editingTerm = ref(null)
const termForm = reactive({ termName: '', businessDesc: '' })

// 口径库状态
const caliberKeyword = ref('')
const caliberList = ref([])
const caliberTotal = ref(0)
const caliberPage = ref(1)
const caliberLoading = ref(false)
const showCaliberDialog = ref(false)
const editingCaliber = ref(null)
const caliberForm = reactive({ caliberName: '', sqlLogic: '', businessDesc: '' })

// 状态映射
const statusType = (s) => ({ active: 'success', inactive: 'warning', new: 'info', modified: '' }[s] || 'info')
const statusLabel = (s) => ({ active: '生效', inactive: '失效', new: '新增', modified: '修改' }[s] || s)

// ─── 术语库 CRUD ───
const loadTerms = async () => {
  termLoading.value = true
  const res = await listTerms({ keyword: termKeyword.value, page: termPage.value })
  termList.value = res.data?.records || []
  termTotal.value = res.data?.total || 0
  termLoading.value = false
}

const saveTerm = async () => {
  if (editingTerm.value) {
    await updateTerm(editingTerm.value.id, termForm)
  } else {
    await createTerm(termForm)
  }
  ElMessage.success('保存成功')
  showTermDialog.value = false
  loadTerms()
}

const editTerm = (row) => {
  editingTerm.value = row
  Object.assign(termForm, { termName: row.termName, businessDesc: row.businessDesc })
  showTermDialog.value = true
}

const handleDeleteTerm = async (row) => {
  if (row.status === 'active') { ElMessage.warning('生效的知识不允许被删除，请先失效！'); return }
  await ElMessageBox.confirm('删除当前知识，将会影响智能SQL回答问题的准确性，确认删除吗？', '确认删除', { type: 'warning' })
  await deleteTerm(row.id)
  ElMessage.success('已删除')
  loadTerms()
}

// ─── 口径库 CRUD ───
const loadCalibers = async () => {
  caliberLoading.value = true
  const res = await listCalibers({ keyword: caliberKeyword.value, page: caliberPage.value })
  caliberList.value = res.data?.records || []
  caliberTotal.value = res.data?.total || 0
  caliberLoading.value = false
}

const saveCaliber = async () => {
  if (editingCaliber.value) {
    await updateCaliber(editingCaliber.value.id, caliberForm)
  } else {
    await createCaliber(caliberForm)
  }
  ElMessage.success('保存成功')
  showCaliberDialog.value = false
  loadCalibers()
}

const editCaliber = (row) => {
  editingCaliber.value = row
  Object.assign(caliberForm, { caliberName: row.caliberName, sqlLogic: row.sqlLogic, businessDesc: row.businessDesc })
  showCaliberDialog.value = true
}

const handleDeleteCaliber = async (row) => {
  if (row.status === 'active') { ElMessage.warning('生效的知识不允许被删除，请先失效！'); return }
  await ElMessageBox.confirm('删除当前知识，将会影响智能SQL回答问题的准确性，确认删除吗？', '确认删除', { type: 'warning' })
  await deleteCaliber(row.id)
  ElMessage.success('已删除')
  loadCalibers()
}

onMounted(() => {
  loadTerms()
  loadCalibers()
})
</script>

<style lang="scss" scoped>
.knowledge-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 { margin: 0; }
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
