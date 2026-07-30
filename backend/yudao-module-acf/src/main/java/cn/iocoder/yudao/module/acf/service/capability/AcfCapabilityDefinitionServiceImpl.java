package cn.iocoder.yudao.module.acf.service.capability;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityRegistry;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilityPageReqVO;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilitySyncRespVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.capability.AcfCapabilityDefinitionDO;
import cn.iocoder.yudao.module.acf.dal.mysql.capability.AcfCapabilityDefinitionMapper;
import cn.iocoder.yudao.module.acf.enums.AcfCapabilityRuntimeStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ACF 能力定义 Service 实现类
 *
 * @author bujidao
 */
@Service
public class AcfCapabilityDefinitionServiceImpl implements AcfCapabilityDefinitionService {

    @Resource
    private AcfCapabilityDefinitionMapper capabilityDefinitionMapper;
    @Resource
    private CapabilityRegistry capabilityRegistry;

    @Override
    public PageResult<AcfCapabilityDefinitionDO> getCapabilityDefinitionPage(AcfCapabilityPageReqVO pageReqVO) {
        return capabilityDefinitionMapper.selectPage(pageReqVO);
    }

    @Override
    public AcfCapabilityDefinitionDO getCapabilityDefinition(Long id) {
        return capabilityDefinitionMapper.selectById(id);
    }

    @Override
    public AcfCapabilityDefinitionDO getRuntimeDefinition(String capabilityName, String capabilityVersion) {
        return capabilityDefinitionMapper.selectByNameVersion(capabilityName, capabilityVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcfCapabilitySyncRespVO syncCapabilityDefinitions() {
        LocalDateTime now = LocalDateTime.now();
        List<CapabilityDefinition> definitions = capabilityRegistry.list();
        int createdCount = 0;
        int updatedCount = 0;

        for (CapabilityDefinition definition : definitions) {
            AcfCapabilityDefinitionDO newDO = buildDefinitionDO(definition, now);
            AcfCapabilityDefinitionDO oldDO = capabilityDefinitionMapper.selectByNameVersion(
                    newDO.getCapabilityName(), newDO.getCapabilityVersion());
            if (oldDO == null) {
                capabilityDefinitionMapper.insert(newDO);
                createdCount++;
                continue;
            }
            if (isChanged(oldDO, newDO)) {
                newDO.setId(oldDO.getId());
                capabilityDefinitionMapper.updateById(newDO);
                updatedCount++;
            } else if (!AcfCapabilityRuntimeStatus.ACTIVE.equals(oldDO.getRuntimeStatus())) {
                oldDO.setRuntimeStatus(AcfCapabilityRuntimeStatus.ACTIVE);
                oldDO.setLastScanTime(now);
                capabilityDefinitionMapper.updateById(oldDO);
                updatedCount++;
            }
        }

        Set<String> scannedKeys = definitions.stream()
                .map(definition -> key(definition.getName(), definition.getVersion()))
                .collect(Collectors.toSet());
        int missingCount = 0;
        for (AcfCapabilityDefinitionDO oldDO : capabilityDefinitionMapper.selectListByGlobalTenant()) {
            if (scannedKeys.contains(key(oldDO.getCapabilityName(), oldDO.getCapabilityVersion()))) {
                continue;
            }
            if (!AcfCapabilityRuntimeStatus.MISSING.equals(oldDO.getRuntimeStatus())) {
                oldDO.setRuntimeStatus(AcfCapabilityRuntimeStatus.MISSING);
                oldDO.setLastScanTime(now);
                capabilityDefinitionMapper.updateById(oldDO);
            }
            missingCount++;
        }
        return new AcfCapabilitySyncRespVO(definitions.size(), createdCount, updatedCount, missingCount);
    }

    private AcfCapabilityDefinitionDO buildDefinitionDO(CapabilityDefinition definition, LocalDateTime now) {
        String inputSchemaJson = JsonUtils.toJsonString(definition.getInputSchema());
        String outputSchemaJson = JsonUtils.toJsonString(definition.getOutputSchema());
        String permissionsJson = JsonUtils.toJsonString(definition.getPermissions());
        String digest = digest(definition, permissionsJson, inputSchemaJson, outputSchemaJson);
        return AcfCapabilityDefinitionDO.builder()
                .tenantId(AcfCapabilityDefinitionMapper.GLOBAL_TENANT_ID)
                .capabilityName(definition.getName())
                .capabilityVersion(definition.getVersion())
                .title(definition.getTitle())
                .description(definition.getDescription())
                .category(definition.getCategory())
                .riskLevel(definition.getRiskLevel() == null ? null : definition.getRiskLevel().name())
                .sideEffect(definition.isSideEffect())
                .confirmationRequired(definition.isConfirmationRequired())
                .permissionMode(definition.getPermissionMode() == null ? null : definition.getPermissionMode().name())
                .permissionsJson(permissionsJson)
                .timeoutMs(definition.getTimeoutMs())
                .argumentType(definition.getArgumentType() == null ? null : definition.getArgumentType().getTypeName())
                .returnType(definition.getReturnType() == null ? null : definition.getReturnType().getTypeName())
                .inputSchemaJson(inputSchemaJson)
                .outputSchemaJson(outputSchemaJson)
                .definitionDigest(digest)
                .runtimeStatus(AcfCapabilityRuntimeStatus.ACTIVE)
                .lastScanTime(now)
                .build();
    }

    private boolean isChanged(AcfCapabilityDefinitionDO oldDO, AcfCapabilityDefinitionDO newDO) {
        return !Objects.equals(oldDO.getDefinitionDigest(), newDO.getDefinitionDigest())
                || !AcfCapabilityRuntimeStatus.ACTIVE.equals(oldDO.getRuntimeStatus());
    }

    private String digest(CapabilityDefinition definition, String permissionsJson,
                          String inputSchemaJson, String outputSchemaJson) {
        Map<String, Object> digestSource = new LinkedHashMap<>();
        digestSource.put("name", definition.getName());
        digestSource.put("version", definition.getVersion());
        digestSource.put("title", definition.getTitle());
        digestSource.put("description", definition.getDescription());
        digestSource.put("category", definition.getCategory());
        digestSource.put("permissions", permissionsJson);
        digestSource.put("permissionMode", definition.getPermissionMode() == null ? null : definition.getPermissionMode().name());
        digestSource.put("riskLevel", definition.getRiskLevel() == null ? null : definition.getRiskLevel().name());
        digestSource.put("sideEffect", definition.isSideEffect());
        digestSource.put("confirmationRequired", definition.isConfirmationRequired());
        digestSource.put("timeoutMs", definition.getTimeoutMs());
        digestSource.put("argumentType", definition.getArgumentType() == null ? null : definition.getArgumentType().getTypeName());
        digestSource.put("returnType", definition.getReturnType() == null ? null : definition.getReturnType().getTypeName());
        digestSource.put("inputSchema", inputSchemaJson);
        digestSource.put("outputSchema", outputSchemaJson);
        return sha256(JsonUtils.toJsonString(digestSource));
    }

    private String sha256(String text) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to calculate ACF capability definition digest", exception);
        }
    }

    private String key(String capabilityName, String capabilityVersion) {
        return capabilityName + "#" + capabilityVersion;
    }

}
