package cn.iocoder.yudao.module.acf.service.confirmation;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import cn.iocoder.yudao.module.acf.dal.dataobject.confirmation.AcfConfirmationChallengeDO;
import cn.iocoder.yudao.module.acf.dal.mysql.confirmation.AcfConfirmationChallengeMapper;
import cn.iocoder.yudao.module.acf.enums.AcfConfirmationChallengeStatus;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcfCapabilityConfirmationServiceTest {

    private final AcfConfirmationChallengeMapper mapper = mock(AcfConfirmationChallengeMapper.class);
    private final AcfCapabilityConfirmationService service = new AcfCapabilityConfirmationService(mapper);

    @Test
    void shouldReusePendingChallengeForSameRequest() {
        AcfConfirmationChallengeDO existing = challenge();
        when(mapper.selectReusablePending(eq("demo.create"), eq("1.0.0"), eq(20L), eq(10L),
                eq("MCP"), eq("user:10"), eq("codex"), eq("idem-001"), eq("digest-001"),
                any(LocalDateTime.class))).thenReturn(existing);

        CapabilityConfirmationChallenge result = service.createChallenge(definition(), context(),
                "idem-001", "digest-001");

        assertThat(result.getChallengeId()).isEqualTo("challenge-001");
        assertThat(result.getRequestDigest()).isEqualTo("digest-001");
    }

    @Test
    void shouldCreateChallengeWhenNoReusablePendingChallengeExists() {
        ArgumentCaptor<AcfConfirmationChallengeDO> captor =
                ArgumentCaptor.forClass(AcfConfirmationChallengeDO.class);

        CapabilityConfirmationChallenge result = service.createChallenge(definition(), context(),
                "idem-001", "digest-001");

        verify(mapper).insert(captor.capture());
        AcfConfirmationChallengeDO inserted = captor.getValue();
        assertThat(inserted.getChallengeId()).startsWith("acf-confirm-");
        assertThat(inserted.getTenantId()).isEqualTo(20L);
        assertThat(inserted.getUserId()).isEqualTo(10L);
        assertThat(inserted.getConsumerClientId()).isEqualTo("codex");
        assertThat(inserted.getStatus()).isEqualTo(AcfConfirmationChallengeStatus.PENDING.name());
        assertThat(result.getChallengeId()).isEqualTo(inserted.getChallengeId());
        assertThat(result.getRequestDigest()).isEqualTo("digest-001");
    }

    @Test
    void shouldConfirmPendingChallengeAndStoreTokenHash() {
        AcfConfirmationChallengeDO challenge = challenge();
        when(mapper.selectByChallengeIdForUpdate("challenge-001")).thenReturn(challenge);

        CapabilityConfirmationToken token = service.confirm("challenge-001", context(), "approved");

        assertThat(token.getChallengeId()).isEqualTo("challenge-001");
        assertThat(token.getConfirmationToken()).startsWith("acf-token-");
        assertThat(challenge.getStatus()).isEqualTo(AcfConfirmationChallengeStatus.CONFIRMED.name());
        assertThat(challenge.getTokenHash()).startsWith("sha256:");
        assertThat(challenge.getTokenHash()).doesNotContain(token.getConfirmationToken());
        assertThat(challenge.getConfirmedUserId()).isEqualTo(10L);
        verify(mapper).updateById(challenge);
    }

    @Test
    void shouldRejectConfirmWhenChallengeBelongsToAnotherClient() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setConsumerClientId("workbuddy");
        when(mapper.selectByChallengeIdForUpdate("challenge-001")).thenReturn(challenge);

        assertThatThrownBy(() -> service.confirm("challenge-001", context(), "approved"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAtomicallyConsumeMatchingToken() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setStatus(AcfConfirmationChallengeStatus.CONFIRMED.name());
        String token = "acf-token-test";
        challenge.setTokenHash(hashOf(token));
        when(mapper.selectByTokenHash(challenge.getTokenHash())).thenReturn(challenge);
        when(mapper.update(any(AcfConfirmationChallengeDO.class), any(Wrapper.class))).thenReturn(1);

        CapabilityConfirmationCheck check = service.verifyAndConsumeToken(definition(), context(),
                token, "idem-001", "digest-001");

        assertThat(check.isValid()).isTrue();
        assertThat(check.getChallengeId()).isEqualTo("challenge-001");
        ArgumentCaptor<AcfConfirmationChallengeDO> captor =
                ArgumentCaptor.forClass(AcfConfirmationChallengeDO.class);
        verify(mapper).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getStatus()).isEqualTo(AcfConfirmationChallengeStatus.USED.name());
        assertThat(captor.getValue().getUsedTraceId()).isEqualTo("trace-001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectAlreadyConsumedTokenWhenAtomicUpdateMisses() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setStatus(AcfConfirmationChallengeStatus.CONFIRMED.name());
        String token = "acf-token-test";
        challenge.setTokenHash(hashOf(token));
        when(mapper.selectByTokenHash(challenge.getTokenHash())).thenReturn(challenge);
        when(mapper.update(any(AcfConfirmationChallengeDO.class), any(Wrapper.class))).thenReturn(0);

        CapabilityConfirmationCheck check = service.verifyAndConsumeToken(definition(), context(),
                token, "idem-001", "digest-001");

        assertThat(check.isValid()).isFalse();
        assertThat(check.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResetConfirmedChallengeToPendingWhenInvocationMismatches() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setStatus(AcfConfirmationChallengeStatus.CONFIRMED.name());
        String token = "acf-token-test";
        challenge.setTokenHash(hashOf(token));
        when(mapper.selectByTokenHash(challenge.getTokenHash())).thenReturn(challenge);
        when(mapper.update(any(AcfConfirmationChallengeDO.class), any(Wrapper.class))).thenReturn(1);

        // 重试摘要与确认时绑定的摘要不一致：拒绝本次执行，但把 challenge 打回 PENDING，
        // 客户端可重新确认、换新 token 按原始摘要重试；否则状态卡在 CONFIRMED，
        // 重调 confirm() 会因“非 pending”被拒，challenge 就烧掉了。
        CapabilityConfirmationCheck check = service.verifyAndConsumeToken(definition(), context(),
                token, "idem-001", "digest-002");

        assertThat(check.isValid()).isFalse();
        assertThat(check.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID);
        ArgumentCaptor<AcfConfirmationChallengeDO> captor =
                ArgumentCaptor.forClass(AcfConfirmationChallengeDO.class);
        verify(mapper).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getStatus()).isEqualTo(AcfConfirmationChallengeStatus.PENDING.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotResetChallengeWhenAlreadyPendingOnMismatch() {
        AcfConfirmationChallengeDO challenge = challenge();
        String token = "acf-token-test";
        challenge.setTokenHash(hashOf(token));
        when(mapper.selectByTokenHash(challenge.getTokenHash())).thenReturn(challenge);

        // 已是 PENDING（例如打回后的旧 token T1 又拿错摘要来）：失配只返回 invalid，
        // 不重复打回、不调 update。
        CapabilityConfirmationCheck check = service.verifyAndConsumeToken(definition(), context(),
                token, "idem-001", "digest-002");

        assertThat(check.isValid()).isFalse();
        assertThat(check.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID);
        verify(mapper, never()).update(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotResetConfirmedChallengeWhenExpiredOnMismatch() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setStatus(AcfConfirmationChallengeStatus.CONFIRMED.name());
        challenge.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        String token = "acf-token-test";
        challenge.setTokenHash(hashOf(token));
        when(mapper.selectByTokenHash(challenge.getTokenHash())).thenReturn(challenge);
        when(mapper.update(any(AcfConfirmationChallengeDO.class), any(Wrapper.class))).thenReturn(0);

        // 已过期 + 失配：拒绝本次执行；过期 guard 在 wrapper 的 WHERE 里
        //（expires_at > now），由数据库求值，mock 层面 update 仍会被调一次
        //（返回 0 行），断言 wrapper 里确实带了过期条件。
        CapabilityConfirmationCheck check = service.verifyAndConsumeToken(definition(), context(),
                token, "idem-001", "digest-002");

        assertThat(check.isValid()).isFalse();
        assertThat(check.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID);
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(any(AcfConfirmationChallengeDO.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("expires_at");
    }

    @Test
    void shouldDeclinePendingChallengeAndBlockLaterConfirm() {
        AcfConfirmationChallengeDO challenge = challenge();
        when(mapper.selectByChallengeIdForUpdate("challenge-001")).thenReturn(challenge);

        service.decline("challenge-001", context());

        assertThat(challenge.getStatus()).isEqualTo(AcfConfirmationChallengeStatus.DECLINED.name());
        verify(mapper).updateById(challenge);
        // DECLINED 是终态：之后再 confirm 必须因“非 pending”被拒绝。
        assertThatThrownBy(() -> service.confirm("challenge-001", context(), "approved"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not pending");
    }

    @Test
    void shouldSilentlyIgnoreDeclineWhenChallengeNotFound() {
        when(mapper.selectByChallengeIdForUpdate("challenge-001")).thenReturn(null);

        service.decline("challenge-001", context());

        verify(mapper, never()).updateById(any(AcfConfirmationChallengeDO.class));
    }

    @Test
    void shouldMarkExpiredChallengeWhenDecliningAfterExpiry() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectByChallengeIdForUpdate("challenge-001")).thenReturn(challenge);

        service.decline("challenge-001", context());

        assertThat(challenge.getStatus()).isEqualTo(AcfConfirmationChallengeStatus.EXPIRED.name());
        verify(mapper).updateById(challenge);
    }

    @Test
    void shouldSilentlyIgnoreDeclineWhenChallengeBelongsToAnotherClient() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setConsumerClientId("workbuddy");
        when(mapper.selectByChallengeIdForUpdate("challenge-001")).thenReturn(challenge);

        service.decline("challenge-001", context());

        assertThat(challenge.getStatus()).isEqualTo(AcfConfirmationChallengeStatus.PENDING.name());
        verify(mapper, never()).updateById(any(AcfConfirmationChallengeDO.class));
    }

    @Test
    void shouldSilentlyIgnoreDeclineWhenChallengeAlreadyConfirmed() {
        AcfConfirmationChallengeDO challenge = challenge();
        challenge.setStatus(AcfConfirmationChallengeStatus.CONFIRMED.name());
        when(mapper.selectByChallengeIdForUpdate("challenge-001")).thenReturn(challenge);

        service.decline("challenge-001", context());

        assertThat(challenge.getStatus()).isEqualTo(AcfConfirmationChallengeStatus.CONFIRMED.name());
        verify(mapper, never()).updateById(any(AcfConfirmationChallengeDO.class));
    }

    private static CapabilityDefinition definition() {
        return CapabilityDefinition.builder()
                .name("demo.create")
                .version("1.0.0")
                .riskLevel(CapabilityRiskLevel.HIGH)
                .build();
    }

    private static CapabilityContext context() {
        return CapabilityContext.builder()
                .traceId("trace-001")
                .userId(10L)
                .tenantId(20L)
                .source("MCP")
                .consumerType(CapabilityConsumerType.MCP)
                .consumerId("user:10")
                .attributes(Map.of("oauthClientId", "codex"))
                .build();
    }

    private static AcfConfirmationChallengeDO challenge() {
        AcfConfirmationChallengeDO challenge = AcfConfirmationChallengeDO.builder()
                .id(1L)
                .challengeId("challenge-001")
                .capabilityName("demo.create")
                .capabilityVersion("1.0.0")
                .riskLevel(CapabilityRiskLevel.HIGH.name())
                .userId(10L)
                .source("MCP")
                .consumerType("MCP")
                .consumerId("user:10")
                .consumerClientId("codex")
                .idempotencyKey("idem-001")
                .requestDigest("digest-001")
                .status(AcfConfirmationChallengeStatus.PENDING.name())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        challenge.setTenantId(20L);
        return challenge;
    }

    private static String hashOf(String token) {
        return "sha256:" + java.util.HexFormat.of().formatHex(sha256(token));
    }

    private static byte[] sha256(String token) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
