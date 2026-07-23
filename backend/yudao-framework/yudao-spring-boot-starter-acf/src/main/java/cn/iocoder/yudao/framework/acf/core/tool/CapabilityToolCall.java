package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import lombok.Builder;
import lombok.Getter;

/**
 * 协议适配器提交给 ACF 的标准工具调用
 *
 * context 必须由可信适配器根据当前认证环境构建，不得直接采用外部请求声明的用户或租户身份。
 * 幂等键和确认令牌作为调用控制信息独立传递，不混入能力的业务参数与 JSON Schema。
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityToolCall {

    private final String capabilityName;
    private final Object arguments;
    private final CapabilityContext context;
    private final String idempotencyKey;
    private final String confirmationToken;

}
