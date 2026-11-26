package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.acf.config.YudaoAcfAutoConfiguration;
import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityIdempotencyService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityIdempotencyCheck;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Modern 客户端集成测试：无 initialize，首次调用即发请求；确认流程走 MRTR
 * elicitation（首轮 {@code callOnce()} 拿 InputRequiredResult，再带密封的
 * {@code requestState} 与答案重试）。
 */
@SpringBootTest(classes = McpAcfExecutionIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"yudao.mcp.server.enabled=true",
                "yudao.acf.confirmation.enabled=true",
                "yudao.web.admin-ui.url=http://localhost"})
class McpAcfExecutionIntegrationTest {

    private static final Long USER_ID = 1001L;
    private static final String REQUIRED_PERMISSION = "mcp:test:echo";

    @LocalServerPort
    private int port;

    @Autowired
    private PermissionCommonApi permissionCommonApi;

    @Autowired
    private ConfirmEchoCapability confirmEchoCapability;

    @Autowired
    private InMemoryConfirmationService confirmationService;

    @Test
    void shouldInvokeRegisteredCapabilityThroughMcpAndAcfGovernance() {
        HttpClientMcpTransport transport = HttpClientMcpTransport
                .builder("http://127.0.0.1:" + port + "/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("acf-integration-test", "1.0.0").build())
                .roundTimeout(Duration.ofSeconds(10))
                .build();

        McpSchema.CallToolResult result = client.tool("test.mcp.echo")
                .arguments(Map.of(McpSchemaAdapter.INPUT_VALUE_PROPERTY, "hello"))
                .call();

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent())
                .isEqualTo(Map.of(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY, "tenant:2001:acf:hello"));
        assertThat(result.content().get(0).toString())
                .contains("{\"result\":\"tenant:2001:acf:hello\"}");
        // 与确认测试共享同一个 permission mock：确认能力同样走权限校验，
        // 用 atLeastOnce 而不用默认的恰好一次，避免用例执行顺序影响断言。
        verify(permissionCommonApi, org.mockito.Mockito.atLeastOnce())
                .hasAnyPermissions(USER_ID, REQUIRED_PERMISSION);
    }

    @Test
    void shouldRejectTamperedArgumentsAfterConfirmation() {
        // 清单 G：首轮确认时摘要按 "hello" 生成，重试时把参数改成 "tampered"，
        // confirm() 能换到 token，但执行器验 token 时摘要对不上，必须以
        // CONFIRM_TOKEN_INVALID 拒绝，且目标方法一次也不能执行。
        HttpClientMcpTransport transport = HttpClientMcpTransport
                .builder("http://127.0.0.1:" + port + "/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("acf-confirmation-test", "1.0.0").build())
                .formElicitation(request -> McpSchema.ElicitResult.accept(
                        Map.of("confirmRemark", "ok")))
                .roundTimeout(Duration.ofSeconds(10))
                .build();

        McpSchema.CallToolOutcome first = client.tool("test.mcp.confirmecho")
                .arguments(Map.of(McpSchemaAdapter.INPUT_VALUE_PROPERTY, "hello",
                        CapabilityToolContractSupport.IDEMPOTENCY_KEY, "idem-g-001"))
                .meta(Map.of("bujidao.ai/idempotencyKey", "idem-g-001"))
                .callOnce();
        assertThat(first).isInstanceOf(McpSchema.InputRequiredResult.class);
        McpSchema.InputRequiredResult required = (McpSchema.InputRequiredResult) first;
        assertThat(required.requestState()).isNotBlank();
        assertThat(required.inputRequests())
                .containsOnlyKeys("acf.confirmation");
        assertThat(confirmationService.getCreateCalls()).isEqualTo(1);

        McpSchema.CallToolOutcome retried = client.tool("test.mcp.confirmecho")
                .arguments(Map.of(McpSchemaAdapter.INPUT_VALUE_PROPERTY, "tampered",
                        CapabilityToolContractSupport.IDEMPOTENCY_KEY, "idem-g-001"))
                .meta(Map.of("bujidao.ai/idempotencyKey", "idem-g-001"))
                .requestState(required.requestState())
                .inputResponses(Map.of("acf.confirmation", Map.of("action", "accept",
                        "content", Map.of("confirmRemark", "ok"))))
                .callOnce();

        assertThat(retried).isInstanceOf(McpSchema.CallToolResult.class);
        McpSchema.CallToolResult result = (McpSchema.CallToolResult) retried;
        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry("bujidao.ai/errorCode",
                AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID);
        assertThat(confirmationService.getConfirmCalls()).isEqualTo(1);
        assertThat(confirmEchoCapability.getInvocations()).isZero();
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class, ValidationAutoConfiguration.class,
            YudaoAcfAutoConfiguration.class, YudaoMcpAutoConfiguration.class, YudaoMcpAcfAutoConfiguration.class})
    static class TestApplication {

        @Bean
        EchoCapability echoCapability() {
            return new EchoCapability();
        }

        @Bean
        ConfirmEchoCapability confirmEchoCapability() {
            return new ConfirmEchoCapability();
        }

        @Bean
        InMemoryConfirmationService confirmationService() {
            return new InMemoryConfirmationService();
        }

        @Bean
        CapabilityIdempotencyService idempotencyService() {
            // 确认重试要先过幂等关：测试里恒放行，只验证确认摘要这一层。
            CapabilityIdempotencyService idempotencyService = mock(CapabilityIdempotencyService.class);
            when(idempotencyService.acquire(any(), any(), any(), any()))
                    .thenReturn(CapabilityIdempotencyCheck.acquired());
            return idempotencyService;
        }

        @Bean
        PermissionCommonApi permissionCommonApi() {
            PermissionCommonApi permissionCommonApi = mock(PermissionCommonApi.class);
            when(permissionCommonApi.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
            return permissionCommonApi;
        }

        @Bean
        McpTransportContextExtractor<HttpServletRequest> mcpTransportContextExtractor() {
            return request -> McpTransportContext.create(Map.of(
                    McpTransportContextKeys.USER_ID, USER_ID,
                    McpTransportContextKeys.TENANT_ID, 2001L,
                    McpTransportContextKeys.CONSUMER_ID, "integration-test"));
        }

        @Bean
        WebProperties webProperties() {
            WebProperties properties = new WebProperties();
            properties.setAdminUi(new WebProperties.Ui());
            return properties;
        }

        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties();
        }

        @Bean
        OAuth2TokenCommonApi oauth2TokenCommonApi() {
            return mock(OAuth2TokenCommonApi.class);
        }

        @Bean("mcpSecurityFilterChain")
        SecurityFilterChain mcpSecurityFilterChain() {
            return mock(SecurityFilterChain.class);
        }
    }

    static class EchoCapability {

        @AgentCapability(name = "test.mcp.echo", title = "MCP ACF 回显",
                description = "验证 MCP 通过 ACF 治理与执行链调用能力", permissions = REQUIRED_PERMISSION)
        public String echo(String value) {
            return "tenant:" + TenantContextHolder.getRequiredTenantId() + ":acf:" + value;
        }
    }

    static class ConfirmEchoCapability {

        private final AtomicInteger invocations = new AtomicInteger();

        @AgentCapability(name = "test.mcp.confirmecho", title = "MCP ACF 确认回显",
                description = "验证改参数后确认摘要变化导致 token 校验失败",
                permissions = REQUIRED_PERMISSION, confirmationRequired = true)
        public String echo(String value) {
            invocations.incrementAndGet();
            return "tenant:" + TenantContextHolder.getRequiredTenantId() + ":acf:" + value;
        }

        int getInvocations() {
            return invocations.get();
        }
    }

    /**
     * 内存版确认服务：challenge 建在首轮调用的参数摘要上，confirm 只认
     * PENDING 的 challenge，verify 时比对当前请求的摘要——与真实服务的
     * 摘要绑定语义一致（见 AcfCapabilityConfirmationService）。
     */
    static class InMemoryConfirmationService implements CapabilityConfirmationService {

        private final Map<String, StoredChallenge> challenges = new ConcurrentHashMap<>();
        private final AtomicInteger createCalls = new AtomicInteger();
        private final AtomicInteger confirmCalls = new AtomicInteger();

        int getCreateCalls() {
            return createCalls.get();
        }

        int getConfirmCalls() {
            return confirmCalls.get();
        }

        @Override
        public CapabilityConfirmationChallenge createChallenge(CapabilityDefinition definition,
                                                               CapabilityContext context, String idempotencyKey,
                                                               String requestDigest) {
            createCalls.incrementAndGet();
            String challengeId = "challenge-g-" + createCalls.get();
            challenges.put(challengeId, new StoredChallenge(definition.getName(),
                    definition.getVersion(), requestDigest, "PENDING", null));
            return CapabilityConfirmationChallenge.builder()
                    .challengeId(challengeId)
                    .capabilityName(definition.getName())
                    .capabilityVersion(definition.getVersion())
                    .riskLevel(definition.getRiskLevel())
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .requestDigest(requestDigest)
                    .build();
        }

        @Override
        public CapabilityConfirmationToken confirm(String challengeId, CapabilityContext context,
                                                   String confirmRemark) {
            confirmCalls.incrementAndGet();
            StoredChallenge stored = challenges.get(challengeId);
            if (stored == null) {
                throw new IllegalArgumentException("Confirmation challenge not found");
            }
            if (!"PENDING".equals(stored.status())) {
                throw new IllegalArgumentException("Confirmation challenge is not pending");
            }
            String token = "token-" + challengeId;
            challenges.put(challengeId, new StoredChallenge(stored.capabilityName(),
                    stored.capabilityVersion(), stored.requestDigest(), "CONFIRMED", token));
            return CapabilityConfirmationToken.builder()
                    .challengeId(challengeId)
                    .confirmationToken(token)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
        }

        @Override
        public void decline(String challengeId, CapabilityContext context) {
            challenges.computeIfPresent(challengeId, (id, stored) ->
                    "PENDING".equals(stored.status()) ? new StoredChallenge(stored.capabilityName(),
                            stored.capabilityVersion(), stored.requestDigest(), "DECLINED", stored.token())
                            : stored);
        }

        @Override
        public CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition,
                                                                  CapabilityContext context,
                                                                  String confirmationToken, String idempotencyKey,
                                                                  String requestDigest) {
            for (Map.Entry<String, StoredChallenge> entry : challenges.entrySet()) {
                StoredChallenge stored = entry.getValue();
                if (!Objects.equals(stored.token(), confirmationToken)) {
                    continue;
                }
                if (!Objects.equals(stored.requestDigest(), requestDigest)) {
                    return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                            "Confirmation token does not match current request");
                }
                if (!"CONFIRMED".equals(stored.status())) {
                    return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                            "Confirmation token is not usable");
                }
                challenges.put(entry.getKey(), new StoredChallenge(stored.capabilityName(),
                        stored.capabilityVersion(), stored.requestDigest(), "USED", stored.token()));
                return CapabilityConfirmationCheck.valid(entry.getKey());
            }
            return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                    "Confirmation token is invalid");
        }

        private record StoredChallenge(String capabilityName, String capabilityVersion,
                                       String requestDigest, String status, String token) {
        }
    }

}
