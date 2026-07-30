import request from '@/config/axios'

export interface AcfCapabilityVO {
  id: number
  capabilityName: string
  capabilityVersion: string
  title: string
  description: string
  category?: string
  riskLevel?: string
  sideEffect: boolean
  confirmationRequired: boolean
  permissionMode?: string
  permissionsJson?: string
  timeoutMs: number
  argumentType?: string
  returnType?: string
  inputSchemaJson?: string
  outputSchemaJson?: string
  definitionDigest?: string
  runtimeStatus: string
  lastScanTime: Date
  createTime: Date
}

export interface AcfCapabilitySyncRespVO {
  scannedCount: number
  createdCount: number
  updatedCount: number
  missingCount: number
}

export const AcfCapabilityApi = {
  getCapabilityPage: async (params: any) => {
    return await request.get({ url: '/acf/capability/page', params })
  },

  getCapability: async (id: number) => {
    return await request.get({ url: '/acf/capability/get?id=' + id })
  },

  syncCapabilityDefinitions: async (): Promise<AcfCapabilitySyncRespVO> => {
    return await request.post({ url: '/acf/capability/sync' })
  }
}
