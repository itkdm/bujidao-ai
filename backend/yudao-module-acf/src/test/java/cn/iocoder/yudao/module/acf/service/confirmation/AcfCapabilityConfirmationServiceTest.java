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
