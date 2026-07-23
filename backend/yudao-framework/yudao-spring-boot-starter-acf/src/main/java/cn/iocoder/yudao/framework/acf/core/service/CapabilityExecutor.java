package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * ACF 基础能力执行器
 *
 * 负责把外部参数转换为能力方法声明的 Java 类型，完成 Bean Validation 校验并调用已注册方法。
 * 权限、确认、幂等、审计与运行时策略由后续治理链在该执行入口外扩展。
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityExecutor {

    public static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    public static final String ERROR_CAPABILITY_NOT_FOUND = "CAPABILITY_NOT_FOUND";
    public static final String ERROR_INVOKE = "INVOKE_ERROR";

    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CapabilityResult invoke(CapabilityInvokeCommand command) {
        String name = command == null ? null : command.getName();
        if (!StringUtils.hasText(name)) {
            return CapabilityResult.failure(name, ERROR_BAD_REQUEST, "Capability name is required");
        }

        CapabilityRegistration registration;
        try {
            registration = capabilityRegistry.getRegistration(name);
        } catch (IllegalArgumentException exception) {
            return CapabilityResult.failure(name, ERROR_CAPABILITY_NOT_FOUND, exception.getMessage());
        }

        try {
            Object argument = convertArgument(registration.definition(), command.getArguments());
            validateArgument(argument);
            Object data = invokeTarget(registration, argument);
            return CapabilityResult.success(name, data);
        } catch (IllegalArgumentException exception) {
            return CapabilityResult.failure(name, ERROR_BAD_REQUEST, readableMessage(exception));
        } catch (InvocationTargetException exception) {
            return CapabilityResult.failure(name, ERROR_INVOKE, readableMessage(exception.getTargetException()));
        } catch (ReflectiveOperationException exception) {
            return CapabilityResult.failure(name, ERROR_INVOKE, readableMessage(exception));
        }
    }

    private Object convertArgument(CapabilityDefinition definition, Object arguments) {
        Type argumentType = definition.getArgumentType();
        if (argumentType == Void.class || argumentType == Void.TYPE) {
            if (arguments == null || arguments instanceof Map<?, ?> map && map.isEmpty()) {
                return null;
            }
            throw new IllegalArgumentException("Capability does not accept arguments: " + definition.getName());
        }
        Object source = arguments == null ? Map.of() : arguments;
        JavaType javaType = objectMapper.getTypeFactory().constructType(argumentType);
        return objectMapper.convertValue(source, javaType);
    }

    private void validateArgument(Object argument) {
        if (argument == null) {
            return;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(argument);
        if (violations.isEmpty()) {
            return;
        }
        ConstraintViolation<Object> violation = violations.stream()
                .min(Comparator.comparing(item -> item.getPropertyPath() + " " + item.getMessage()))
                .orElseThrow();
        throw new IllegalArgumentException(violation.getPropertyPath() + " " + violation.getMessage());
    }

    private Object invokeTarget(CapabilityRegistration registration, Object argument)
            throws InvocationTargetException, IllegalAccessException {
        if (registration.method().getParameterCount() == 0) {
            return registration.method().invoke(registration.bean());
        }
        return registration.method().invoke(registration.bean(), argument);
    }

    private String readableMessage(Throwable throwable) {
        return StringUtils.hasText(throwable.getMessage())
                ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

}
