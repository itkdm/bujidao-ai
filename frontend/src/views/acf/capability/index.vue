<template>
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="92px"
    >
      <el-form-item label="能力名称" prop="capabilityName">
        <el-input
          v-model="queryParams.capabilityName"
          placeholder="请输入能力名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-input
          v-model="queryParams.category"
          placeholder="请输入分类"
          clearable
          @keyup.enter="handleQuery"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="风险等级" prop="riskLevel">
        <el-select
          v-model="queryParams.riskLevel"
          placeholder="请选择风险等级"
          clearable
          class="!w-160px"
        >
          <el-option label="LOW" value="LOW" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="HIGH" value="HIGH" />
          <el-option label="CRITICAL" value="CRITICAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="运行状态" prop="runtimeStatus">
        <el-select
          v-model="queryParams.runtimeStatus"
          placeholder="请选择运行状态"
          clearable
          class="!w-160px"
        >
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="MISSING" value="MISSING" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="handleSync"
          :loading="syncLoading"
          v-hasPermi="['acf:capability:sync']"
        >
          <Icon icon="ep:refresh-right" class="mr-5px" /> 同步能力
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="能力名称" align="center" prop="capabilityName" min-width="180" />
      <el-table-column label="标题" align="center" prop="title" min-width="140" />
      <el-table-column label="分类" align="center" prop="category" width="120" />
      <el-table-column label="风险" align="center" prop="riskLevel" width="100">
        <template #default="scope">
          <el-tag :type="riskTagType(scope.row.riskLevel)">{{ scope.row.riskLevel || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="副作用" align="center" prop="sideEffect" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.sideEffect ? 'warning' : 'success'">
            {{ scope.row.sideEffect ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="需确认" align="center" prop="confirmationRequired" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.confirmationRequired ? 'warning' : 'info'">
            {{ scope.row.confirmationRequired ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="runtimeStatus" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.runtimeStatus === 'ACTIVE' ? 'success' : 'danger'">
            {{ scope.row.runtimeStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="最后扫描时间"
        align="center"
        prop="lastScanTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="操作" align="center" fixed="right" width="80">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openDetail(scope.row.id)"
            v-hasPermi="['acf:capability:query']"
          >
            详细
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="detailVisible" :max-height="620" :scroll="true" title="能力详情" width="900">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="能力名称">{{ detailData.capabilityName }}</el-descriptions-item>
      <el-descriptions-item label="标题">{{ detailData.title }}</el-descriptions-item>
      <el-descriptions-item label="描述">{{ detailData.description }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ detailData.capabilityVersion }}</el-descriptions-item>
      <el-descriptions-item label="权限模式">{{ detailData.permissionMode }}</el-descriptions-item>
      <el-descriptions-item label="权限标识">
        <pre class="acf-json">{{ formatJson(detailData.permissionsJson) }}</pre>
      </el-descriptions-item>
      <el-descriptions-item label="入参类型">{{ detailData.argumentType }}</el-descriptions-item>
      <el-descriptions-item label="返回类型">{{ detailData.returnType }}</el-descriptions-item>
      <el-descriptions-item label="入参 Schema">
        <pre class="acf-json">{{ formatJson(detailData.inputSchemaJson) }}</pre>
      </el-descriptions-item>
      <el-descriptions-item label="出参 Schema">
        <pre class="acf-json">{{ formatJson(detailData.outputSchemaJson) }}</pre>
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import { AcfCapabilityApi, AcfCapabilityVO } from '@/api/acf/capability'

defineOptions({ name: 'AcfCapability' })

const message = useMessage()
const loading = ref(true)
const syncLoading = ref(false)
const total = ref(0)
const list = ref<AcfCapabilityVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  capabilityName: undefined,
  category: undefined,
  riskLevel: undefined,
  runtimeStatus: undefined
})
const queryFormRef = ref()
const detailVisible = ref(false)
const detailData = ref({} as AcfCapabilityVO)

const getList = async () => {
  loading.value = true
  try {
    const data = await AcfCapabilityApi.getCapabilityPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const handleSync = async () => {
  syncLoading.value = true
  try {
    const data = await AcfCapabilityApi.syncCapabilityDefinitions()
    message.success(
      `同步完成：扫描 ${data.scannedCount}，新增 ${data.createdCount}，更新 ${data.updatedCount}，缺失 ${data.missingCount}`
    )
    await getList()
  } finally {
    syncLoading.value = false
  }
}

const openDetail = async (id: number) => {
  detailData.value = await AcfCapabilityApi.getCapability(id)
  detailVisible.value = true
}

const riskTagType = (riskLevel?: string) => {
  if (riskLevel === 'HIGH' || riskLevel === 'CRITICAL') return 'danger'
  if (riskLevel === 'MEDIUM') return 'warning'
  if (riskLevel === 'LOW') return 'success'
  return 'info'
}

const formatJson = (text?: string) => {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.acf-json {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
