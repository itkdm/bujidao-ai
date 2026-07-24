package cn.iocoder.yudao.framework.acf.core.tool;

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
                .map(CapabilityToolDescriptor::from)
                .toList();
    }

}
