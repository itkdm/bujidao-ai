package cn.iocoder.yudao.module.mcp.framework.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
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
 * 限制 MCP 请求体的实际读取字节数，同时覆盖固定长度和 chunked 请求。
 *
 * @author bujidao
 */
public class McpRequestSizeLimitFilter extends OncePerRequestFilter {

    private static final String ERROR_MESSAGE = "MCP request body is too large";

    private final long maxRequestSize;

    public McpRequestSizeLimitFilter(long maxRequestSize) {
        if (maxRequestSize <= 0) {
            throw new IllegalArgumentException("maxRequestSize must be greater than zero");
        }
        this.maxRequestSize = maxRequestSize;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxRequestSize) {
            reject(response);
            return;
        }
        try {
            filterChain.doFilter(new LimitedBodyRequest(request, maxRequestSize), response);
        } catch (RequestBodyTooLargeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            reject(response);
        } catch (RuntimeException exception) {
            if (!isRequestBodyTooLarge(exception)) {
                throw exception;
            }
            if (response.isCommitted()) {
                throw exception;
            }
            reject(response);
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, ERROR_MESSAGE);
    }

    private static boolean isRequestBodyTooLarge(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RequestBodyTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long maxRequestSize;
        private ServletInputStream inputStream;
        private BufferedReader reader;

        private LimitedBodyRequest(HttpServletRequest request, long maxRequestSize) {
            super(request);
            this.maxRequestSize = maxRequestSize;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) {
                throw new IllegalStateException("getReader() has already been called for this request");
            }
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxRequestSize);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (inputStream != null && reader == null) {
                throw new IllegalStateException("getInputStream() has already been called for this request");
            }
            if (reader == null) {
                Charset charset = getCharacterEncoding() == null
                        ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxRequestSize);
                reader = new BufferedReader(new InputStreamReader(inputStream, charset));
            }
            return reader;
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxRequestSize;
        private long bytesRead;

        private LimitedServletInputStream(ServletInputStream delegate, long maxRequestSize) {
            this.delegate = delegate;
            this.maxRequestSize = maxRequestSize;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                recordRead(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = delegate.read(bytes, offset, length);
            if (count > 0) {
                recordRead(count);
            }
            return count;
        }

        private void recordRead(int count) throws RequestBodyTooLargeException {
            bytesRead += count;
            if (bytesRead > maxRequestSize) {
                throw new RequestBodyTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class RequestBodyTooLargeException extends IOException {
    }

}
