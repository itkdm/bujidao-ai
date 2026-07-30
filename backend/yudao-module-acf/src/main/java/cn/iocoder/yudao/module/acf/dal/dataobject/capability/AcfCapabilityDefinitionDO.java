package cn.iocoder.yudao.module.acf.dal.dataobject.capability;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ACF 能力定义 DO
 *
 * @author bujidao
 */
@TableName("acf_capability_definition")
@KeySequence("acf_capability_definition_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcfCapabilityDefinitionDO extends BaseDO {

    @TableId
    private Long id;
    private String capabilityName;
    private String capabilityVersion;
    private String title;
    private String description;
    private String category;
    private String riskLevel;
    private Boolean sideEffect;
    private Boolean confirmationRequired;
    private String permissionMode;
    private String permissionsJson;
    private Integer timeoutMs;
    private String argumentType;
    private String returnType;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private String definitionDigest;
    private String runtimeStatus;
    private LocalDateTime lastScanTime;
    private Long tenantId;

}
