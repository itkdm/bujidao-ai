package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityNamingConvention;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 扫描并注册标注了 {@link AgentCapability} 的 Spring Bean 方法
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityRegistry implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final CapabilitySchemaGenerator schemaGenerator;

    /** 非空表示注册完成；使用不可变快照保证读取线程看到完整结果 */
    private volatile Map<String, CapabilityRegistration> registrations;

    @Override
    public void afterSingletonsInstantiated() {
        initializeIfNecessary();
    }

    /**
     * 获取全部能力定义，按能力名称升序排列
     */
    public List<CapabilityDefinition> list() {
        return getRegistrations().values().stream()
                .map(CapabilityRegistration::definition)
                .sorted(Comparator.comparing(CapabilityDefinition::getName))
                .toList();
    }

    /**
     * 根据名称获取能力定义
     *
     * @throws IllegalArgumentException 能力不存在时抛出
     */
    public CapabilityDefinition get(String name) {
        return getRegistration(name).definition();
    }

    /** 后续执行器通过该内部入口获取方法绑定，不向协议和管理层暴露 Spring Bean */
    CapabilityRegistration getRegistration(String name) {
        CapabilityRegistration registration = getRegistrations().get(name);
        if (registration == null) {
            throw new IllegalArgumentException("Capability not found: " + name);
        }
        return registration;
    }

    private Map<String, CapabilityRegistration> getRegistrations() {
        initializeIfNecessary();
        return registrations;
    }

    private void initializeIfNecessary() {
        if (registrations != null) {
            return;
        }
        synchronized (this) {
            if (registrations != null) {
                return;
            }
            Map<String, CapabilityRegistration> scannedRegistrations = new LinkedHashMap<>();
            Set<Object> scannedBeans = Collections.newSetFromMap(new IdentityHashMap<>());
            for (String beanName : applicationContext.getBeanDefinitionNames()) {
                if (beanName.startsWith("scopedTarget.")) {
                    continue;
                }
                Class<?> beanType = applicationContext.getType(beanName);
                if (beanType == null || !hasCapabilityMethod(beanType)) {
                    continue;
                }
                Object bean = applicationContext.getBean(beanName);
                if (scannedBeans.add(bean)) {
                    scanBean(scannedRegistrations, beanName, bean);
                }
            }
            // 扫描全部成功后一次性发布，避免非法声明导致外部读到部分注册结果。
            registrations = Collections.unmodifiableMap(new LinkedHashMap<>(scannedRegistrations));
        }
    }

    private boolean hasCapabilityMethod(Class<?> beanType) {
        return Arrays.stream(ReflectionUtils.getUniqueDeclaredMethods(beanType))
                .anyMatch(method -> isCapabilityMethod(method, beanType));
    }

    private void scanBean(Map<String, CapabilityRegistration> targetRegistrations,
                          String beanName, Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : ReflectionUtils.getUniqueDeclaredMethods(targetClass)) {
            if (isCapabilityMethod(method, targetClass)) {
                register(targetRegistrations, beanName, bean, targetClass, method);
            }
        }
    }

    private void register(Map<String, CapabilityRegistration> targetRegistrations, String beanName,
                          Object bean, Class<?> targetClass, Method method) {
        AgentCapability annotation = findCapabilityAnnotation(method, targetClass);
        if (annotation == null) {
            return;
        }
        validate(annotation, method);

        Method invocableMethod;
        try {
            invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("ACF capability method is not invocable through Spring bean '"
                    + beanName + "': " + method.toGenericString(), exception);
        }
        ReflectionUtils.makeAccessible(invocableMethod);

        Type argumentType = method.getParameterCount() == 0 ? Void.class : method.getGenericParameterTypes()[0];
        Type returnType = method.getGenericReturnType();
        CapabilityDefinition definition = CapabilityDefinition.builder()
                .name(annotation.name())
                .title(annotation.title())
                .description(annotation.description())
                .category(annotation.category())
                .permissions(List.copyOf(Arrays.asList(annotation.permissions())))
                .permissionMode(annotation.permissionMode())
                .riskLevel(annotation.riskLevel())
                .sideEffect(annotation.sideEffect())
                .confirmationRequired(annotation.confirmationRequired())
                .version(annotation.version())
                .timeoutMs(annotation.timeoutMs())
                .argumentType(argumentType)
                .returnType(returnType)
                .inputSchema(schemaGenerator.generate(argumentType))
                .outputSchema(schemaGenerator.generate(returnType))
                .build();
        CapabilityRegistration registration = new CapabilityRegistration(definition, beanName, bean, invocableMethod);

        CapabilityRegistration existing = targetRegistrations.putIfAbsent(annotation.name(), registration);
        if (existing != null) {
            throw new IllegalStateException("Duplicate ACF capability name '" + annotation.name()
                    + "': bean '" + existing.beanName() + "' method " + existing.method().toGenericString()
                    + " and bean '" + beanName + "' method " + method.toGenericString());
        }
    }

    private boolean isCapabilityMethod(Method method, Class<?> targetClass) {
        return method != null && !method.isBridge() && !method.isSynthetic()
                && findCapabilityAnnotation(method, targetClass) != null;
    }

    private AgentCapability findCapabilityAnnotation(Method method, Class<?> targetClass) {
        AgentCapability annotation = AnnotatedElementUtils.findMergedAnnotation(method, AgentCapability.class);
        if (annotation != null) {
            return annotation;
        }
        // JDK 代理常把注解声明在接口方法上，需要沿目标类接口补充查找。
        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            Method interfaceMethod = ReflectionUtils.findMethod(interfaceType, method.getName(),
                    method.getParameterTypes());
            if (interfaceMethod == null) {
                continue;
            }
            annotation = AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, AgentCapability.class);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private void validate(AgentCapability annotation, Method method) {
        if (!StringUtils.hasText(annotation.name())) {
            throw invalid(method, "name is required");
        }
        if (!AcfCapabilityNamingConvention.isValid(annotation.name())) {
            throw invalid(method, "name must follow domain.resource.action: " + annotation.name());
        }
        if (!StringUtils.hasText(annotation.title())) {
            throw invalid(method, "title is required: " + annotation.name());
        }
        if (!StringUtils.hasText(annotation.description())) {
            throw invalid(method, "description is required: " + annotation.name());
        }
        if (annotation.permissions().length == 0
                || Arrays.stream(annotation.permissions()).anyMatch(permission -> !StringUtils.hasText(permission))) {
            throw invalid(method, "permissions must contain non-blank values: " + annotation.name());
        }
        if (Arrays.stream(annotation.permissions()).distinct().count() != annotation.permissions().length) {
            throw invalid(method, "permissions must not contain duplicates: " + annotation.name());
        }
        if (!StringUtils.hasText(annotation.version())) {
            throw invalid(method, "version is required: " + annotation.name());
        }
        if (annotation.timeoutMs() <= 0) {
            throw invalid(method, "timeoutMs must be greater than zero: " + annotation.name());
        }
        if (method.getParameterCount() > 1) {
            throw invalid(method, "supports zero or one argument: " + annotation.name());
        }
    }

    private IllegalStateException invalid(Method method, String reason) {
        return new IllegalStateException("Invalid @AgentCapability on " + method.toGenericString() + ": " + reason);
    }

}
