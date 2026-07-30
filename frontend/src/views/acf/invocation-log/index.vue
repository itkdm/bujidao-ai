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
      <el-form-item label="消费者类型" prop="consumerType">
        <el-input
          v-model="queryParams.consumerType"
          placeholder="请输入消费者类型"
          clearable
          @keyup.enter="handleQuery"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="消费者编号" prop="consumerId">
        <el-input
          v-model="queryParams.consumerId"
          placeholder="请输入消费者编号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-160px">
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAILURE" value="FAILURE" />
          <el-option label="DENIED" value="DENIED" />
          <el-option label="CONFIRM_REQUIRED" value="CONFIRM_REQUIRED" />
        </el-select>
      </el-form-item>
      <el-form-item label="调用时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="Trace ID" align="center" prop="traceId" min-width="180" />
      <el-table-column label="能力名称" align="center" prop="capabilityName" min-width="180" />
      <el-table-column label="消费者" align="center" min-width="170">
        <template #default="scope">
          {{ scope.row.consumerType || '-' }} / {{ scope.row.consumerId || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="用户编号" align="center" prop="userId" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="130">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'">
            {{ scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" align="center" prop="latencyMs" width="100">
        <template #default="scope">{{ scope.row.latencyMs }} ms</template>
      </el-table-column>
      <el-table-column
        label="调用时间"
        align="center"
        prop="createTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="操作" align="center" fixed="right" width="80">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openDetail(scope.row.id)"
            v-hasPermi="['acf:invocation-log:query']"
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

  <Dialog v-model="detailVisible" :max-height="620" :scroll="true" title="调用日志详情" width="900">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="Trace ID">{{ detailData.traceId }}</el-descriptions-item>
      <el-descriptions-item label="能力">{{ detailData.capabilityName }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ detailData.capabilityVersion }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ detailData.source }}</el-descriptions-item>
      <el-descriptions-item label="消费者">
        {{ detailData.consumerType }} / {{ detailData.consumerId }}
      </el-descriptions-item>
      <el-descriptions-item label="客户端请求">{{ detailData.clientRequestId }}</el-descriptions-item>
      <el-descriptions-item label="用户编号">{{ detailData.userId }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ detailData.status }}</el-descriptions-item>
      <el-descriptions-item label="请求摘要">{{ detailData.requestSummary }}</el-descriptions-item>
      <el-descriptions-item label="响应摘要">{{ detailData.responseSummary }}</el-descriptions-item>
      <el-descriptions-item label="治理摘要">{{ detailData.policySummary }}</el-descriptions-item>
      <el-descriptions-item label="运行摘要">{{ detailData.runtimeSummary }}</el-descriptions-item>
      <el-descriptions-item label="错误码">{{ detailData.errorCode }}</el-descriptions-item>
      <el-descriptions-item label="错误信息">{{ detailData.errorMessage }}</el-descriptions-item>
      <el-descriptions-item label="耗时">{{ detailData.latencyMs }} ms</el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import { AcfInvocationLogApi, AcfInvocationLogVO } from '@/api/acf/invocationLog'

defineOptions({ name: 'AcfInvocationLog' })

const loading = ref(true)
const total = ref(0)
const list = ref<AcfInvocationLogVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  capabilityName: undefined,
  consumerType: undefined,
  consumerId: undefined,
  status: undefined,
  createTime: []
})
const queryFormRef = ref()
const detailVisible = ref(false)
const detailData = ref({} as AcfInvocationLogVO)

const getList = async () => {
  loading.value = true
  try {
    const data = await AcfInvocationLogApi.getInvocationLogPage(queryParams)
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

const openDetail = async (id: number) => {
  detailData.value = await AcfInvocationLogApi.getInvocationLog(id)
  detailVisible.value = true
}

onMounted(() => {
  getList()
})
</script>
