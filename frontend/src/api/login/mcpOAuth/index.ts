import request from '@/config/axios'

export interface McpOAuthScope {
  key: string
  value: boolean
}

export interface McpOAuthAuthorizeInfo {
  client: {
    name: string
    logo?: string
  }
  scopes: McpOAuthScope[]
}

export const getAuthorize = (clientId: string) => {
  return request.get<McpOAuthAuthorizeInfo>({ url: '/mcp/oauth2/authorize?clientId=' + encodeURIComponent(clientId) })
}

export const authorize = (
  responseType: string,
  clientId: string,
  redirectUri: string,
  state: string | undefined,
  autoApprove: boolean,
  checkedScopes: string[],
  uncheckedScopes: string[],
  codeChallenge: string,
  codeChallengeMethod: string,
  // resource 为 undefined 时 axios 不发该参数（不发 resource= 空串），后端按“不带”处理。
  resource: string | undefined
) => {
  const scopes: Record<string, boolean> = {}
  for (const scope of checkedScopes) {
    scopes[scope] = true
  }
  for (const scope of uncheckedScopes) {
    scopes[scope] = false
  }
  return request.post<string>({
    url: '/mcp/oauth2/authorize',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    params: {
      response_type: responseType,
      client_id: clientId,
      redirect_uri: redirectUri,
      state,
      auto_approve: autoApprove,
      scope: JSON.stringify(scopes),
      code_challenge: codeChallenge,
      code_challenge_method: codeChallengeMethod,
      resource
    }
  })
}
