package cn.iocoder.yudao.module.mcp.framework.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class YudaoMcpServerAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    YudaoMcpServerAutoConfiguration.class))
            .withBean(CapabilityToolCatalog.class, () -> mock(CapabilityToolCatalog.class))
            .withBean(CapabilityToolInvoker.class, () -> mock(CapabilityToolInvoker.class));

    @Test
    void shouldRemainDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);
            assertThat(context).doesNotHaveBean(HttpServletStatelessServerTransport.class);
        });
    }

    @Test
    void shouldRegisterStatelessServerWhenEnabled() {
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpStatelessSyncServer.class);
                    assertThat(context).hasSingleBean(HttpServletStatelessServerTransport.class);
                    assertThat(context).hasSingleBean(YudaoMcpServerProperties.class);
                    assertThat(context).hasSingleBean(YudaoMcpToolProperties.class);
                });
    }

}
