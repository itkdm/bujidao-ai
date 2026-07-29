package cn.iocoder.yudao.framework.mcp.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Ensures the MCP SDK reads JSON request bodies with a deterministic charset.
 *
 * @author bujidao
 */
public class McpRequestBodyCharsetFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(new Utf8DefaultReaderRequest(request), response);
    }

    private static final class Utf8DefaultReaderRequest extends HttpServletRequestWrapper {

        private Utf8DefaultReaderRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

}
