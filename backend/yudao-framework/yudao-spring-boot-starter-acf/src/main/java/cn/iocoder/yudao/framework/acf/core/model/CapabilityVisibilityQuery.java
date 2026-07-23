package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 能力可见性查询条件
 *
 * @author bujidao
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityVisibilityQuery {

    private String category;
    private CapabilityRiskLevel riskLevel;
    private Boolean sideEffect;
    private CapabilityContext context;

}
