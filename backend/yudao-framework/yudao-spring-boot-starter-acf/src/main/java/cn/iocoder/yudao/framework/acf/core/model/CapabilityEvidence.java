package cn.iocoder.yudao.framework.acf.core.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 支撑能力执行结果的结构化证据
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityEvidence {

    private final String code;
    private final String summary;
    private final Object details;

    public static CapabilityEvidence of(String code, String summary, Object details) {
        return CapabilityEvidence.builder()
                .code(code)
                .summary(summary)
                .details(details)
                .build();
    }

}
