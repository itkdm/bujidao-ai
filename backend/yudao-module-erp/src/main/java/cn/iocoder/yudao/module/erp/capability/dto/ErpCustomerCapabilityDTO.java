package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

/**
 * ERP 客户能力视图
 *
 * 有意不暴露银行账号、开户行、纳税识别号等财务敏感字段，
 * 保证 Agent 只拿到完成业务判断所必需的信息。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpCustomerCapabilityDTO {

    @CapabilityField(description = "客户编号")
    private Long id;

    @CapabilityField(description = "客户名称")
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
