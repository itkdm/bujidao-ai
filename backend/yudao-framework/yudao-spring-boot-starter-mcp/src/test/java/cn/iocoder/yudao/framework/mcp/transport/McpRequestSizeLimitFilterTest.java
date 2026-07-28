package cn.iocoder.yudao.framework.mcp.transport;

import cn.hutool.core.io.IORuntimeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class McpRequestSizeLimitFilterTest {

    private final McpRequestSizeLimitFilter filter = new McpRequestSizeLimitFilter(8);

    @Test
    void shouldRejectDeclaredContentLengthBeforeReadingBody() throws Exception {
        MockHttpServletRequest request = requestWithBody("123456789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getErrorMessage()).isEqualTo("MCP request body is too large");
        verifyNoInteractions(chain);
    }

    @Test
    void shouldRejectOversizedBodyWhenContentLengthIsUnknown() throws Exception {
        MockHttpServletRequest source = requestWithBody("123456789");
        HttpServletRequest request = new HttpServletRequestWrapper(source) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (wrappedRequest, wrappedResponse) ->
                ((HttpServletRequest) wrappedRequest).getInputStream().readAllBytes();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getErrorMessage()).isEqualTo("MCP request body is too large");
    }

    @Test
    void shouldAllowBodyAtConfiguredLimit() throws Exception {
        MockHttpServletRequest source = requestWithBody("12345678");
        HttpServletRequest request = new HttpServletRequestWrapper(source) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[][] body = new byte[1][];
        FilterChain chain = (wrappedRequest, wrappedResponse) ->
                body[0] = ((HttpServletRequest) wrappedRequest).getInputStream().readAllBytes();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body[0]).containsExactly("12345678".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldLimitReaderAndReturnSameReaderInstance() throws Exception {
        MockHttpServletRequest source = requestWithBody("123456789");
        HttpServletRequest request = new HttpServletRequestWrapper(source) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            HttpServletRequest limitedRequest = (HttpServletRequest) wrappedRequest;
            assertThat(limitedRequest.getReader()).isSameAs(limitedRequest.getReader());
            limitedRequest.getReader().readLine();
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void shouldRejectOversizedBodyWhenDownstreamWrapsReadException() throws Exception {
        MockHttpServletRequest source = requestWithBody("123456789");
        HttpServletRequest request = new HttpServletRequestWrapper(source) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            try {
                ((HttpServletRequest) wrappedRequest).getInputStream().readAllBytes();
            } catch (Exception exception) {
                throw new IORuntimeException(exception);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getErrorMessage()).isEqualTo("MCP request body is too large");
    }

    private static MockHttpServletRequest requestWithBody(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return request;
    }

}