package cn.iocoder.yudao.framework.mcp.transport;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class McpRequestBodyCharsetFilterTest {

    private final McpRequestBodyCharsetFilter filter = new McpRequestBodyCharsetFilter();

    @Test
    void shouldUseDeclaredRequestCharsetWhenReadingBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("测试".getBytes(StandardCharsets.UTF_16LE));
        request.setCharacterEncoding(StandardCharsets.UTF_16LE.name());
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] body = new String[1];
        FilterChain chain = (wrappedRequest, wrappedResponse) ->
                body[0] = wrappedRequest.getReader().readLine();

        filter.doFilter(request, response, chain);

        assertThat(body[0]).isEqualTo("测试");
    }

    @Test
    void shouldDefaultToUtf8WhenRequestCharsetIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("测试".getBytes(StandardCharsets.UTF_8));
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] body = new String[1];
        FilterChain chain = (wrappedRequest, wrappedResponse) ->
                body[0] = wrappedRequest.getReader().readLine();

        filter.doFilter(request, response, chain);

        assertThat(body[0]).isEqualTo("测试");
    }

}
