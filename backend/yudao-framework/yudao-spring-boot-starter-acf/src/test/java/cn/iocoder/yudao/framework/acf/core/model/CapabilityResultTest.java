package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityResultTest {

    @Test
    void shouldBuildSuccessResultWithImmutableEvidenceAndNextActions() {
        CapabilityResult original = CapabilityResult.success(Map.of("ok", true), "done");
        CapabilityEvidence evidence = CapabilityEvidence.of(
                "stock_snapshot", "当前库存快照", Map.of("count", 12));
        CapabilityNextAction nextAction = CapabilityNextAction.of(
                "erp.stock.query", "刷新库存", Map.of("productId", 1L));

        CapabilityResult enriched = original.withEvidence(evidence).withSuggestedNextAction(nextAction);

        assertThat(original.getEvidence()).isEmpty();
        assertThat(original.getSuggestedNextActions()).isEmpty();
        assertThat(enriched.getStatus()).isEqualTo(CapabilityStatus.SUCCESS);
        assertThat(enriched.getMessage()).isEqualTo("done");
        assertThat(enriched.getEvidence()).containsExactly(evidence);
        assertThat(enriched.getSuggestedNextActions()).containsExactly(nextAction);
        assertThat(enriched.getEvidence()).isUnmodifiable();
        assertThat(enriched.getSuggestedNextActions()).isUnmodifiable();
    }

    @Test
    void shouldBuildRetryableFailureWithEmptyCollections() {
        CapabilityResult result = CapabilityResult.failure(
                AcfCapabilityErrorCodes.DATA_INCOMPLETE, "data incomplete", true);

        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.FAILURE);
        assertThat(result.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.DATA_INCOMPLETE);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getEvidence()).isEqualTo(List.of());
        assertThat(result.getSuggestedNextActions()).isEqualTo(List.of());
    }

    @Test
    void shouldCopyCallerProvidedCollections() {
        CapabilityEvidence evidence = CapabilityEvidence.of("source", "来源", Map.of());
        List<CapabilityEvidence> source = new java.util.ArrayList<>(List.of(evidence));

        CapabilityResult result = CapabilityResult.builder().status(CapabilityStatus.SUCCESS).evidence(source).build();
        source.clear();

        assertThat(result.getEvidence()).containsExactly(evidence);
    }

}
