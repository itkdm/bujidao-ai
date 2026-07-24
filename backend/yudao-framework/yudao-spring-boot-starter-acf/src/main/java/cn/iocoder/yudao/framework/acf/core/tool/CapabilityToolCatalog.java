package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.service.CapabilityRegistry;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * ACF 声明工具目录
 *
 * 该目录只描述代码中已经注册的能力工具，不执行用户可见性或调用权限判断。
 * Agent、MCP 等适配器可据此构建部署期工具定义，但单次调用仍必须经过
 * {@link CapabilityToolInvoker} 和完整的 ACF 治理链。
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityToolCatalog {

    private final CapabilityRegistry capabilityRegistry;

    /**
     * 获取全部已声明的工具描述，按能力名称升序排列。
     */
    public List<CapabilityToolDescriptor> listDeclared() {
        return capabilityRegistry.list().stream()
                .map(CapabilityToolDescriptor::from)
                .toList();
    }

    /**
     * 根据能力名称获取已声明的工具描述。
     *
     * @throws IllegalArgumentException 能力不存在时抛出
     */
    public CapabilityToolDescriptor getDeclared(String capabilityName) {
        return CapabilityToolDescriptor.from(capabilityRegistry.get(capabilityName));
    }

}
