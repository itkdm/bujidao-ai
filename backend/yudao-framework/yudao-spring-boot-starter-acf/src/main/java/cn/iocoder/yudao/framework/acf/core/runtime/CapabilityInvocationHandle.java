package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Handle for one submitted capability invocation.
 *
 * A wait timeout does not mean the target has terminated. The terminal completion
 * only completes after the target exits or cancellation before start is certain.
 *
 * @author bujidao
 */
public interface CapabilityInvocationHandle {

    CapabilityResult await(int timeoutMs) throws InterruptedException, ExecutionException, TimeoutException;

    CapabilityInvocationInterruptResult interrupt();

    CompletionStage<CapabilityInvocationCompletion> completion();

}
