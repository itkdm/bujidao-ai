import request from '@/config/axios'

export interface AcfInvocationLogVO {
  id: number
  traceId: string
  userId?: number
  capabilityName: string
  capabilityVersion?: string
  source?: string
  consumerType?: string
  consumerId?: string
  clientRequestId?: string
  requestSummary?: string
  responseSummary?: string
  policySummary?: string
  runtimeSummary?: string
  status: string
  errorCode?: string
  errorMessage?: string
  latencyMs: number
  createTime: Date
}

export const AcfInvocationLogApi = {
  getInvocationLogPage: async (params: any) => {
    return await request.get({ url: '/acf/invocation-log/page', params })
  },

  getInvocationLog: async (id: number) => {
    return await request.get({ url: '/acf/invocation-log/get?id=' + id })
  }
}
