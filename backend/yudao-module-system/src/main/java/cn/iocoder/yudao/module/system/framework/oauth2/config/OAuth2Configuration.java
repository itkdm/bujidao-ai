package cn.iocoder.yudao.module.system.framework.oauth2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OAuth2 配置。
 *
 * @author bujidao
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuth2DynamicClientRegistrationProperties.class)
public class OAuth2Configuration {
}
