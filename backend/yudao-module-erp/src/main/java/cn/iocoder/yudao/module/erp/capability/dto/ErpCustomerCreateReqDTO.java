package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ERP 客户创建能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpCustomerCreateReqDTO {

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 64)
    @CapabilityField(description = "客户名称", example = "布吉岛测试客户")
    private String name;

    @Size(max = 32)
    @CapabilityField(description = "联系人", example = "张三")
    private String contact;

    @Size(max = 32)
    @CapabilityField(description = "手机号码", sensitive = true, example = "13800000000")
    private String mobile;

    @Size(max = 32)
    @CapabilityField(description = "联系电话", sensitive = true, example = "021-88888888")
    private String telephone;

    @Size(max = 255)
    @CapabilityField(description = "备注", example = "MCP 创建客户")
    private String remark;

}
