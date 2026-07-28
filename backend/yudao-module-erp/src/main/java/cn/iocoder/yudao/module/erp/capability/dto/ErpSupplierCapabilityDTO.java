package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

/**
 * ERP 供应商能力视图
 *
 * 有意不暴露银行账号、开户行、纳税识别号等财务敏感字段。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpSupplierCapabilityDTO {

    @CapabilityField(description = "供应商编号")
    private Long id;

    @CapabilityField(description = "供应商名称")
    private String name;

    @CapabilityField(description = "联系人")
    private String contact;

    @CapabilityField(description = "手机号码", sensitive = true)
    private String mobile;

    @CapabilityField(description = "联系电话", sensitive = true)
    private String telephone;

    @CapabilityField(description = "开启状态：0 开启，1 关闭")
    private Integer status;

}
