package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 根据查询条件和可见性治理策略筛选能力
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityVisibilityService {

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityGovernanceService governanceService;

    public List<CapabilityDefinition> listVisible() {
        return listVisible(null);
    }

    public List<CapabilityDefinition> listVisible(CapabilityVisibilityQuery query) {
        CapabilityVisibilityQuery safeQuery = query == null ? new CapabilityVisibilityQuery() : query;
        CapabilityContext context = safeQuery.getContext() == null
                ? CapabilityContext.empty() : safeQuery.getContext();
        return capabilityRegistry.list().stream()
                .filter(definition -> matchesQuery(definition, safeQuery))
                .map(definition -> evaluateVisibility(definition, context))
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<CapabilityDefinition> getVisible(String name) {
        return getVisible(name, null);
    }

    public Optional<CapabilityDefinition> getVisible(String name, CapabilityVisibilityQuery query) {
        CapabilityVisibilityQuery safeQuery = query == null ? new CapabilityVisibilityQuery() : query;
        CapabilityDefinition definition = capabilityRegistry.get(name);
        if (!matchesQuery(definition, safeQuery)) {
            return Optional.empty();
        }
        CapabilityContext context = safeQuery.getContext() == null
                ? CapabilityContext.empty() : safeQuery.getContext();
        return evaluateVisibility(definition, context);
    }

    private Optional<CapabilityDefinition> evaluateVisibility(CapabilityDefinition definition,
                                                              CapabilityContext context) {
        CapabilityPolicyResult result = Objects.requireNonNull(
                governanceService.evaluateVisibility(definition, context),
                "Capability visibility governance result must not be null");
        if (!result.isAllowed()) {
            return Optional.empty();
        }
        return Optional.ofNullable(result.getDefinition() == null ? definition : result.getDefinition());
    }

    private boolean matchesQuery(CapabilityDefinition definition, CapabilityVisibilityQuery query) {
        return (!StringUtils.hasText(query.getCategory())
                || Objects.equals(definition.getCategory(), query.getCategory()))
                && (query.getRiskLevel() == null
                || Objects.equals(definition.getRiskLevel(), query.getRiskLevel()))
                && (query.getSideEffect() == null
                || Objects.equals(definition.isSideEffect(), query.getSideEffect()));
    }

}
