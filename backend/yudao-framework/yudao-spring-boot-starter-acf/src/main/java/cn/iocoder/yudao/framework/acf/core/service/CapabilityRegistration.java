package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;

import java.lang.reflect.Method;

/**
 * 能力定义与 Spring 方法的内部运行时绑定
 *
 * 定义模型不直接持有 Spring Bean，避免能力描述在序列化或协议转换时泄露运行时对象。
 *
 * @author bujidao
 */
record CapabilityRegistration(CapabilityDefinition definition, String beanName, Object bean, Method method) {
}
