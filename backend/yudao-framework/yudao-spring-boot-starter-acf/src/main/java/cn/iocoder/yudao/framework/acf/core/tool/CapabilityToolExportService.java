package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityVisibilityService;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 将治理后可见的 ACF 能力导出为协议无关的工具描述
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityToolExportService {

    private final CapabilityVisibilityService visibilityService;

    public List<CapabilityToolDescriptor> export(CapabilityVisibilityQuery query) {
        return visibilityService.listVisible(query).stream()
                .map(this::toDescriptor)
                .toList();
    }

    private CapabilityToolDescriptor toDescriptor(CapabilityDefinition definition) {
        return CapabilityToolDescriptor.builder()
                .capabilityName(definition.getName())
                .version(definition.getVersion())
                .title(definition.getTitle())
                .description(definition.getDescription())
                .category(definition.getCategory())
                .inputSchema(definition.getInputSchema())
                .outputSchema(definition.getOutputSchema())
                .permissionMode(definition.getPermissionMode())
                .permissions(definition.getPermissions())
                .riskLevel(definition.getRiskLevel())
                .sideEffect(definition.isSideEffect())
                .confirmationRequired(definition.isConfirmationRequired())
                .build();
    }

}
