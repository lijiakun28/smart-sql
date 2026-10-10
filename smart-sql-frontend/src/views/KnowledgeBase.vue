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
          <el-button type="primary" size="small" @click="openCreateTerm">
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
                @click="handleTermStatusChange(row.id, 'active')"
              >生效</el-button>
              <el-button
                v-if="row.status === 'active'"
                size="small" text type="warning"
                @click="handleTermStatusChange(row.id, 'inactive')"
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
          <el-button type="primary" size="small" @click="openCreateCaliber">
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
                @click="handleCaliberStatusChange(row.id, 'active')"
              >生效</el-button>
              <el-button
                v-if="row.status === 'active'"
                size="small" text type="warning"
                @click="handleCaliberStatusChange(row.id, 'inactive')"
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
      @closed="resetTermForm"
    >
      <el-form :model="termForm" :rules="termRules" ref="termFormRef" label-width="80px">
        <el-form-item label="术语名称" prop="termName">
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
      @closed="resetCaliberForm"
    >
      <el-form :model="caliberForm" :rules="caliberRules" ref="caliberFormRef" label-width="80px">
        <el-form-item label="口径名称" prop="caliberName">
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
const termFormRef = ref(null)

// 口径库状态
const caliberKeyword = ref('')
const caliberList = ref([])
const caliberTotal = ref(0)
const caliberPage = ref(1)
const caliberLoading = ref(false)
const showCaliberDialog = ref(false)
const editingCaliber = ref(null)
const caliberForm = reactive({ caliberName: '', sqlLogic: '', businessDesc: '' })
const caliberFormRef = ref(null)

// 修复：表单校验规则
const termRules = {
  termName: [
    { required: true, message: '术语名称不能为空', trigger: 'blur' },
    { max: 200, message: '名称不超过200字符', trigger: 'blur' }
  ]
}
const caliberRules = {
  caliberName: [
    { required: true, message: '口径名称不能为空', trigger: 'blur' },
    { max: 100, message: '名称不超过100字符', trigger: 'blur' }
  ]
}

// 状态映射
const statusType = (s) => ({ active: 'success', inactive: 'warning', new: 'info', modified: '' }[s] || 'info')
const statusLabel = (s) => ({ active: '生效', inactive: '失效', new: '新增', modified: '修改' }[s] || s)

// ─── 修复：表单生命周期管理 ───

/** 打开新增弹窗（进入时初始化：显式重置表单，防止残留上次数据） */
const openCreateTerm = () => {
  editingTerm.value = null
  Object.assign(termForm, { termName: '', businessDesc: '' })
  showTermDialog.value = true
}

/** 弹窗关闭统一清理（离开时清理：重置编辑标记和表单数据） */
const resetTermForm = () => {
  editingTerm.value = null
  Object.assign(termForm, { termName: '', businessDesc: '' })
}

/** 打开新增口径弹窗（同术语） */
const openCreateCaliber = () => {
  editingCaliber.value = null
  Object.assign(caliberForm, { caliberName: '', sqlLogic: '', businessDesc: '' })
  showCaliberDialog.value = true
}

/** 口径弹窗关闭统一清理 */
const resetCaliberForm = () => {
  editingCaliber.value = null
  Object.assign(caliberForm, { caliberName: '', sqlLogic: '', businessDesc: '' })
}

// ─── 术语库 CRUD ───
const loadTerms = async () => {
  termLoading.value = true
  try {
    const res = await listTerms({ keyword: termKeyword.value, page: termPage.value })
    termList.value = res.data?.records || []
    termTotal.value = res.data?.total || 0
  } catch {
    // 错误提示由 request 拦截器统一给出
  } finally {
    // 必须在 finally 复位：请求抛错时若不复位，表格会一直转圈卡死
    termLoading.value = false
  }
}

const saveTerm = async () => {
  try {
    // 第1层：前端表单校验（体验层），失败抛出
    await termFormRef.value.validate()
    // 第2层：响应码检查（真实性层）已上移到 request 拦截器 ——
    // code !== 200 会 reject 并在那里统一提示，这里只走成功分支
    if (editingTerm.value) {
      await updateTerm(editingTerm.value.id, termForm)
    } else {
      await createTerm(termForm)
    }
  } catch {
    return   // 校验失败 / 接口失败：提示已分别由 Element 表单与 request 拦截器给出
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
  try {
    await ElMessageBox.confirm('删除当前知识，将会影响智能SQL回答问题的准确性，确认删除吗？', '确认删除', { type: 'warning' })
    await deleteTerm(row.id)
  } catch {
    return   // 用户取消，或接口失败（后者提示由 request 拦截器给出）
  }
  ElMessage.success('已删除')
  loadTerms()
}

/** 状态变更（实测修复：原模板直接调 API 函数，成功后不刷新列表） */
const handleTermStatusChange = async (id, status) => {
  try {
    await changeTermStatus(id, status)
  } catch {
    return
  }
  ElMessage.success(status === 'active' ? '已生效' : '已失效')
  loadTerms()
}

// ─── 口径库 CRUD ───
const loadCalibers = async () => {
  caliberLoading.value = true
  try {
    const res = await listCalibers({ keyword: caliberKeyword.value, page: caliberPage.value })
    caliberList.value = res.data?.records || []
    caliberTotal.value = res.data?.total || 0
  } catch {
    // 错误提示由 request 拦截器统一给出
  } finally {
    // 同 loadTerms：必须在 finally 复位，否则表格一直转圈卡死
    caliberLoading.value = false
  }
}

const saveCaliber = async () => {
  try {
    // 第1层：前端表单校验；第2层响应码检查已上移到 request 拦截器（同术语库）
    await caliberFormRef.value.validate()
    if (editingCaliber.value) {
      await updateCaliber(editingCaliber.value.id, caliberForm)
    } else {
      await createCaliber(caliberForm)
    }
  } catch {
    return   // 提示已分别由 Element 表单与 request 拦截器给出
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
  try {
    await ElMessageBox.confirm('删除当前知识，将会影响智能SQL回答问题的准确性，确认删除吗？', '确认删除', { type: 'warning' })
    await deleteCaliber(row.id)
  } catch {
    return   // 用户取消，或接口失败（后者提示由 request 拦截器给出）
  }
  ElMessage.success('已删除')
  loadCalibers()
}

/** 状态变更（实测修复：同术语库问题） */
const handleCaliberStatusChange = async (id, status) => {
  try {
    await changeCaliberStatus(id, status)
  } catch {
    return
  }
  ElMessage.success(status === 'active' ? '已生效' : '已失效')
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
