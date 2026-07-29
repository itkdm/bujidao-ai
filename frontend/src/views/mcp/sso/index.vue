<template>
  <div class="mcp-sso-page">
    <div class="brand-pane">
      <div class="brand-head">
        <img :src="logoUrl" alt="" class="brand-logo" />
        <span>{{ appStore.getTitle }}</span>
      </div>
      <div class="brand-copy">
        <h1>MCP 授权</h1>
        <p>授权后，AI 客户端可以在你允许的范围内调用本系统能力。</p>
      </div>
    </div>
    <div class="authorize-pane">
      <section class="authorize-box">
        <div class="authorize-header">
          <div>
            <h2>第三方授权</h2>
            <p>{{ client.name || queryParams.clientId }}</p>
          </div>
        </div>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
        <div v-else-if="completed" class="authorize-content">
          <el-alert :title="completedMessage" type="success" :closable="false" show-icon />
        </div>
        <div v-else class="authorize-content">
          <p class="scope-title">此第三方应用请求获得以下权限：</p>
          <el-checkbox-group v-model="formData.scopes" class="scope-list">
            <el-checkbox v-for="scope in availableScopes" :key="scope" :value="scope">
              {{ formatScope(scope) }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="authorize-actions">
            <el-button
              type="primary"
              :disabled="!canApprove || formLoading || initializing"
              :loading="formLoading || initializing"
              @click.prevent="handleAuthorize(true)"
            >
              同意授权
            </el-button>
            <el-button :disabled="formLoading || initializing" @click.prevent="handleAuthorize(false)">
              拒绝
            </el-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
import * as McpOAuthApi from '@/api/login/mcpOAuth'
import logoUrl from '@/assets/imgs/logo.png'
import { useAppStore } from '@/store/modules/app'

defineOptions({ name: 'McpSSOLogin' })

const route = useRoute()
const message = useMessage()
const appStore = useAppStore()

const client = ref<{ name: string; logo?: string }>({
  name: '',
  logo: undefined
})

interface QueryParams {
  responseType: string
  clientId: string
  redirectUri: string
  state?: string
  scopes: string[]
  codeChallenge: string
  codeChallengeMethod: string
  resource: string
}

const queryParams = reactive<QueryParams>({
  responseType: '',
  clientId: '',
  redirectUri: '',
  state: undefined,
  scopes: [],
  codeChallenge: '',
  codeChallengeMethod: '',
  resource: ''
})
const formData = reactive({
  scopes: [] as string[]
})
const availableScopes = ref<string[]>([])
const formLoading = ref(false)
const initializing = ref(false)
const errorMessage = ref('')
const completed = ref(false)
const completedMessage = ref('')
const canApprove = computed(() => formData.scopes.length > 0)

const queryValue = (name: string) => {
  const value = route.query[name]
  return Array.isArray(value) ? value[0] || '' : value || ''
}

const init = async () => {
  errorMessage.value = ''
  initializing.value = true
  try {
    queryParams.responseType = queryValue('response_type')
    queryParams.clientId = queryValue('client_id')
    queryParams.redirectUri = queryValue('redirect_uri')
    queryParams.state = queryValue('state') || undefined
    queryParams.codeChallenge = queryValue('code_challenge')
    queryParams.codeChallengeMethod = queryValue('code_challenge_method')
    queryParams.resource = queryValue('resource')
    queryParams.scopes = queryValue('scope')
      .split(' ')
      .map((scope) => scope.trim())
      .filter(Boolean)

    if (
      !queryParams.responseType ||
      !queryParams.clientId ||
      !queryParams.redirectUri ||
      !queryParams.codeChallenge ||
      !queryParams.codeChallengeMethod ||
      !queryParams.resource
    ) {
      errorMessage.value = '授权请求参数不完整'
      return
    }

    if (queryParams.scopes.length > 0) {
      const redirectUrl = await doAuthorize(true, queryParams.scopes, [])
      if (redirectUrl) {
        markCompleted('授权成功，正在返回客户端')
        redirectToClient(redirectUrl)
        return
      }
    }

    const data = await McpOAuthApi.getAuthorize(queryParams.clientId)
    client.value = data.client
    const scopes = queryParams.scopes.length > 0
      ? data.scopes.filter((scope) => queryParams.scopes.includes(scope.key))
      : data.scopes
    availableScopes.value = scopes.map((scope) => scope.key)
    if (queryParams.scopes.length === 0) {
      queryParams.scopes = availableScopes.value
    }
    formData.scopes = scopes.filter((scope) => scope.value).map((scope) => scope.key)
  } catch (error) {
    errorMessage.value = '授权请求初始化失败'
  } finally {
    initializing.value = false
  }
}

const handleAuthorize = async (approved: boolean) => {
  if (completed.value) {
    return
  }
  const checkedScopes = approved ? formData.scopes : []
  if (approved && checkedScopes.length === 0) {
    message.warning('请至少选择一个权限')
    return
  }
  const uncheckedScopes = queryParams.scopes.filter((scope) => !checkedScopes.includes(scope))
  formLoading.value = true
  try {
    const redirectUrl = await doAuthorize(false, checkedScopes, uncheckedScopes)
    if (redirectUrl) {
      const resultMessage = approved ? '授权成功，正在返回客户端' : '已拒绝授权，正在返回客户端'
      markCompleted(resultMessage)
      message.success(resultMessage)
      redirectToClient(redirectUrl)
    }
  } finally {
    formLoading.value = false
  }
}

const doAuthorize = (autoApprove: boolean, checkedScopes: string[], uncheckedScopes: string[]) => {
  return McpOAuthApi.authorize(
    queryParams.responseType,
    queryParams.clientId,
    queryParams.redirectUri,
    queryParams.state,
    autoApprove,
    checkedScopes,
    uncheckedScopes,
    queryParams.codeChallenge,
    queryParams.codeChallengeMethod,
    queryParams.resource
  )
}

const markCompleted = (message: string) => {
  completed.value = true
  completedMessage.value = message
}

const redirectToClient = async (url: string) => {
  await nextTick()
  window.location.href = url
}

const formatScope = (scope: string) => {
  switch (scope) {
    case 'mcp:access':
      return '访问 MCP 工具'
    default:
      return scope
  }
}

onMounted(init)
</script>

<style lang="scss" scoped>
.mcp-sso-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1fr);
  background: #f6f8fb;
}

.brand-pane {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  background: #23324a;
  color: #fff;
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 22px;
  font-weight: 700;
}

.brand-logo {
  width: 48px;
  height: 48px;
  border-radius: 6px;
}

.brand-copy {
  max-width: 520px;
  padding-bottom: 80px;
}

.brand-copy h1 {
  margin: 0 0 16px;
  font-size: 42px;
  line-height: 1.15;
  letter-spacing: 0;
}

.brand-copy p {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 17px;
  line-height: 1.8;
}

.authorize-pane {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.authorize-box {
  width: min(440px, 100%);
  padding: 0;
}

.authorize-header {
  margin-bottom: 28px;
}

.authorize-header h2 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 32px;
  letter-spacing: 0;
}

.authorize-header p {
  margin: 0;
  color: #64748b;
  font-size: 15px;
  word-break: break-all;
}

.authorize-content {
  border-top: 1px solid #d9dee8;
  padding-top: 24px;
}

.scope-title {
  margin: 0 0 14px;
  color: #1f2937;
  font-size: 15px;
}

.scope-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 28px;
}

.authorize-actions {
  display: flex;
  gap: 12px;
}

.authorize-actions .el-button {
  min-width: 112px;
}

@media (max-width: 860px) {
  .mcp-sso-page {
    grid-template-columns: 1fr;
  }

  .brand-pane {
    min-height: 220px;
    padding: 28px;
  }

  .brand-copy {
    padding-bottom: 0;
  }

  .brand-copy h1 {
    font-size: 32px;
  }

  .authorize-pane {
    align-items: flex-start;
  }
}
</style>
