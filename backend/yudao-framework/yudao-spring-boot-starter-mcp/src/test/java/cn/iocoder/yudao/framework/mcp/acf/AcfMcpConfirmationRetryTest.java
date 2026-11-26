package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MRTR 确认重试用例（任务清单 B–I 及补充项）：首轮 InputRequiredResult 本身见
 * {@link AcfMcpToolCallHandlerTest#shouldReturnInputRequiredResultForConfirmationChallenge}
 *（清单 A），这里覆盖带签名 {@code requestState} 的重试分支。
 *
 * <p>重试请求在单测里直接拼：{@code requestState} 是原始 challengeId（真实链路上
 * SDK 在发送前才用 HMAC 密封、重试前先验签开封，handler 拿到的是开封后的值）。</p>
 */
class AcfMcpConfirmationRetryTest {

    @Test
    void shouldExchangeAcceptForTokenAndExecuteWithSameArguments() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture())).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(2L);
            return CapabilityResult.success("demo.echo", Map.of("message", "hello"));
        });
        when(confirmationService.confirm(eq("challenge-001"), any(), eq("checked ok")))
                .thenReturn(CapabilityConfirmationToken.builder()
                        .challengeId("challenge-001")
                        .confirmationToken("token-issued")
                        .build());
        McpRequestContext requestContext = AcfMcpToolCallHandlerTest.requestContext(Map.of(
                AcfMcpToolProtocolMetadata.IDEMPOTENCY_KEY, "idem-001"));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .inputResponses(Map.of(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY,
                        Map.of("action", "accept",
                                "content", Map.of(AcfMcpToolCallHandler.CONFIRM_REMARK_FIELD, "checked ok"))))
                .requestState("challenge-001")
                .build();

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                requestContext, descriptor, request);

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).isEqualTo(Map.of("message", "hello"));
        verify(confirmationService).confirm(eq("challenge-001"), any(), eq("checked ok"));
        verify(invoker).invoke(callCaptor.getValue());
        CapabilityToolCall call = callCaptor.getValue();
        assertThat(call.getArguments()).isEqualTo(Map.of("message", "hello"));
        assertThat(call.getConfirmationToken()).isEqualTo("token-issued");
        assertThat(call.getIdempotencyKey()).isEqualTo("idem-001");
    }

    @Test
    void shouldPersistDeclineAndRejectLaterConfirm() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        InMemoryConfirmationService confirmationService = new InMemoryConfirmationService();
        confirmationService.seedPending("challenge-001");
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        McpRequestContext requestContext = AcfMcpToolCallHandlerTest.formRequestContext();

        McpSchema.CallToolResult declined = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                requestContext, descriptor,
                retryRequest(Map.of("action", "decline")));

        assertThat(declined.isError()).isTrue();
        assertThat(declined.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "DENIED")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                        AcfCapabilityErrorCodes.CONFIRMATION_DECLINED);
        assertThat(declined.content().get(0).toString()).contains("declined");
        verifyNoInteractions(invoker);
        assertThat(confirmationService.statusOf("challenge-001")).isEqualTo("DECLINED");

        // 同一 challenge 再接受：decline 已把它置为 DECLINED 终态，confirm 必须因
        // “非 pending”失败，返回 CONFIRM_TOKEN_INVALID，且仍不执行。
        McpSchema.CallToolResult afterDecline = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                requestContext, descriptor,
                retryRequest(Map.of("action", "accept")));

        assertThat(afterDecline.isError()).isTrue();
        assertThat(afterDecline.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID);
        verifyNoInteractions(invoker);
    }

    @Test
    void shouldReturnDeniedWhenUserCancels() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor,
                retryRequest(Map.of("action", "cancel")));

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "DENIED")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                        AcfCapabilityErrorCodes.CONFIRMATION_DECLINED);
        assertThat(result.content().get(0).toString()).contains("cancelled");
        verify(confirmationService).decline(eq("challenge-001"), any());
        verify(confirmationService, never()).confirm(any(), any(), any());
        verifyNoInteractions(invoker);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReissueElicitationWithSameChallengeIdWhenNoAnswer() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .requestState("challenge-001")
                .build();

        McpSchema.CallToolOutcome outcome = AcfMcpToolCallHandlerTest.handle(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor, request);

        assertThat(outcome).isInstanceOf(McpSchema.InputRequiredResult.class);
        McpSchema.InputRequiredResult required = (McpSchema.InputRequiredResult) outcome;
        assertThat(required.requestState()).isEqualTo("challenge-001");
        assertThat(required.inputRequests()).containsOnlyKeys(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY);
        // _meta 与首轮一致：status + challenge 摘要（只有 challengeId 与能力名，
        // 重试时手头只有 challengeId，拿不到首轮才有的版本/风险/过期时间）。
        // 本用例没带 clientRequestId，所以 traceId 缺席；带了时 traceId 就是它
        // （见 shouldUseClientRequestIdAsTraceIdOnRetryWithoutAnswer）。
        assertThat(required.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "CONFIRM_REQUIRED")
                .doesNotContainKey(AcfMcpToolProtocolMetadata.TRACE_ID);
        Map<String, Object> challengeMetadata = (Map<String, Object>) required.meta()
                .get(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE);
        assertThat(challengeMetadata.keySet()).containsExactlyInAnyOrder("challengeId", "capabilityName");
        assertThat(challengeMetadata.get("challengeId")).isEqualTo("challenge-001");
        verifyNoInteractions(invoker, confirmationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseClientRequestIdAsTraceIdOnRetryWithoutAnswer() {
        // traceId 取客户端的关联 id：fromTransport 把 clientRequestId 同时作为
        // traceId，首轮执行器沿用它（见 CapabilityExecutor），重试 _meta 里也是它，
        // 首轮与重试的 traceId 才能对上；不带时 _meta 里没有 traceId。
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        McpRequestContext requestContext = AcfMcpToolCallHandlerTest.requestContext(Map.of(
                AcfMcpToolProtocolMetadata.CLIENT_REQUEST_ID, "request-001"));
        when(requestContext.clientCapabilities()).thenReturn(McpSchema.ClientCapabilities.builder()
                .elicitation(new McpSchema.ClientCapabilities.Elicitation(
                        new McpSchema.ClientCapabilities.Elicitation.Form(), null))
                .build());
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .requestState("challenge-001")
                .build();

        McpSchema.CallToolOutcome outcome = AcfMcpToolCallHandlerTest.handle(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                requestContext, descriptor, request);

        assertThat(outcome).isInstanceOf(McpSchema.InputRequiredResult.class);
        assertThat(((McpSchema.InputRequiredResult) outcome).meta())
                .containsEntry(AcfMcpToolProtocolMetadata.TRACE_ID, "request-001");
        verifyNoInteractions(invoker, confirmationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Confirmation challenge not found",
            "Confirmation challenge is expired",
            "Confirmation challenge is not pending",
            "Confirmation challenge does not belong to current caller"})
    void shouldMapInvalidChallengeToConfirmTokenInvalid(String failureMessage) {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        when(confirmationService.confirm(eq("challenge-001"), any(), any()))
                .thenThrow(new IllegalArgumentException(failureMessage));

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor,
                retryRequest(Map.of("action", "accept")));

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "FAILURE")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                        AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID);
        assertThat(result.content().get(0).toString()).contains(failureMessage);
        verifyNoInteractions(invoker);
    }

    @Test
    void shouldReturnConfirmationUnavailableWhenClientDoesNotDeclareElicitation() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CapabilityResult.confirmationRequired("demo.echo", challenge())
                        .withTraceId("trace-confirm"));

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                AcfMcpToolCallHandlerTest.requestContext(Map.of()), descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "FAILURE")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                        AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE);
        assertThat(result.content().get(0).toString()).contains("did not declare");
    }

    @Test
    void shouldReturnConfirmationUnavailableOnFirstRoundWhenServiceNotConfigured() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CapabilityResult.confirmationRequired("demo.echo", challenge())
                        .withTraceId("trace-confirm"));

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker),
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "FAILURE")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                        AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE)
                .containsEntry(AcfMcpToolProtocolMetadata.TRACE_ID, "trace-confirm");
        assertThat(result.content().get(0).toString())
                .contains("no confirmation service is configured");
        Map<?, ?> challengeMetadata = (Map<?, ?>) result.meta()
                .get(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE);
        assertThat(challengeMetadata.get("challengeId")).isEqualTo("challenge-001");
        assertThat(challengeMetadata.containsKey("requestDigest")).isFalse();
    }

    @Test
    void shouldReturnConfirmationUnavailableOnRetryWithoutAnswerWhenServiceNotConfigured() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .requestState("challenge-001")
                .build();

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker),
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor, request);

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "FAILURE")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                        AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE);
        verifyNoInteractions(invoker);
    }

    @Test
    void shouldReturnConfirmationUnavailableOnRetryWithoutAnswerWhenClientLacksElicitation() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .requestState("challenge-001")
                .build();

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                AcfMcpToolCallHandlerTest.requestContext(Map.of()), descriptor, request);

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE);
        verifyNoInteractions(invoker, confirmationService);
    }

    @Test
    void shouldReturnConfirmationUnavailableOnRetryAcceptWhenServiceNotConfigured() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker),
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor,
                retryRequest(Map.of("action", "accept")));

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "FAILURE")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                        AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE);
        verifyNoInteractions(invoker);
    }

    @Test
    void shouldPropagateMalformedAnswerAsInvalidParams() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        AcfMcpToolCallHandler handler =
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .inputResponses(Map.of(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY,
                        Map.of("action", Map.of("unexpected", "object"))))
                .requestState("challenge-001")
                .build();

        assertThatThrownBy(() -> AcfMcpToolCallHandlerTest.handle(handler,
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor, request))
                .isInstanceOf(McpException.class)
                .satisfies(exception -> assertThat(((McpException) exception).error().code())
                        .isEqualTo(-32602));
        verifyNoInteractions(invoker, confirmationService);
    }

    @ParameterizedTest
    @MethodSource("emptyTokens")
    void shouldReturnConfirmationErrorWhenConfirmReturnsNoToken(CapabilityConfirmationToken token) {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        CapabilityToolDescriptor descriptor = AcfMcpToolCallHandlerTest.descriptor(
                Map.of("type", "object"), Map.of("type", "object"));
        when(confirmationService.confirm(eq("challenge-001"), any(), any())).thenReturn(token);

        McpSchema.CallToolResult result = AcfMcpToolCallHandlerTest.callToolResult(
                AcfMcpToolCallHandlerTest.createHandler(invoker, confirmationService),
                AcfMcpToolCallHandlerTest.formRequestContext(), descriptor,
                retryRequest(Map.of("action", "accept")));

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE,
                AcfCapabilityErrorCodes.CONFIRMATION_ERROR);
        verify(confirmationService).confirm(eq("challenge-001"), any(), any());
        verifyNoInteractions(invoker);
    }

    static Stream<CapabilityConfirmationToken> emptyTokens() {
        return Stream.of(null,
                CapabilityConfirmationToken.builder().challengeId("challenge-001").build(),
                CapabilityConfirmationToken.builder().challengeId("challenge-001")
                        .confirmationToken("   ").build());
    }

    private static McpSchema.CallToolRequest retryRequest(Map<String, Object> answer) {
        return McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .inputResponses(Map.of(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY, answer))
                .requestState("challenge-001")
                .build();
    }

    private static CapabilityConfirmationChallenge challenge() {
        return CapabilityConfirmationChallenge.builder()
                .challengeId("challenge-001")
                .capabilityName("demo.echo")
                .capabilityVersion("1.0.0")
                .riskLevel(cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel.HIGH)
                .expiresAt(java.time.LocalDateTime.of(2026, 7, 24, 20, 0))
                .requestDigest("internal-digest")
                .build();
    }

    /**
     * 内存版确认服务：只复现状态机（PENDING → CONFIRMED / DECLINED，终态后
     * confirm 拒绝），不碰数据库；用于验证 handler 与确认服务的往返状态流转。
     * 真实服务的 decline 持久化见 module-acf 的
     * {@code AcfCapabilityConfirmationServiceTest}。
     */
    static class InMemoryConfirmationService implements CapabilityConfirmationService {

        private final Map<String, String> states = new ConcurrentHashMap<>();

        void seedPending(String challengeId) {
            states.put(challengeId, "PENDING");
        }

        String statusOf(String challengeId) {
            return states.get(challengeId);
        }

        @Override
        public CapabilityConfirmationChallenge createChallenge(CapabilityDefinition definition,
                                                               CapabilityContext context, String idempotencyKey,
                                                               String requestDigest) {
            throw new UnsupportedOperationException("not used in retry tests");
        }

        @Override
        public CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition,
                                                                  CapabilityContext context,
                                                                  String confirmationToken, String idempotencyKey,
                                                                  String requestDigest) {
            throw new UnsupportedOperationException("not used in retry tests");
        }

        @Override
        public CapabilityConfirmationToken confirm(String challengeId, CapabilityContext context,
                                                   String confirmRemark) {
            String status = states.get(challengeId);
            if (status == null) {
                throw new IllegalArgumentException("Confirmation challenge not found");
            }
            if (!"PENDING".equals(status)) {
                throw new IllegalArgumentException("Confirmation challenge is not pending");
            }
            states.put(challengeId, "CONFIRMED");
            return CapabilityConfirmationToken.builder()
                    .challengeId(challengeId)
                    .confirmationToken("token-" + challengeId)
                    .build();
        }

        @Override
        public void decline(String challengeId, CapabilityContext context) {
            states.computeIfPresent(challengeId, (id, status) ->
                    "PENDING".equals(status) ? "DECLINED" : status);
        }

    }

}
