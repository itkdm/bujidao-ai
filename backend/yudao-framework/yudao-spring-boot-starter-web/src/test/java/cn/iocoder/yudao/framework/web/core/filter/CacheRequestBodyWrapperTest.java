package cn.iocoder.yudao.framework.web.core.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CacheRequestBodyWrapperTest {

    @Test
    void shouldUseDeclaredRequestCharsetWhenReadingBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("测试".getBytes(StandardCharsets.UTF_16LE));
        request.setCharacterEncoding(StandardCharsets.UTF_16LE.name());
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);

        CacheRequestBodyWrapper wrapper = new CacheRequestBodyWrapper(request);

        assertThat(wrapper.getReader().readLine()).isEqualTo("测试");
    }

    @Test
    void shouldDefaultToUtf8WhenRequestCharsetIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("测试".getBytes(StandardCharsets.UTF_8));
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);

        CacheRequestBodyWrapper wrapper = new CacheRequestBodyWrapper(request);

        assertThat(wrapper.getReader().readLine()).isEqualTo("测试");
    }

}
