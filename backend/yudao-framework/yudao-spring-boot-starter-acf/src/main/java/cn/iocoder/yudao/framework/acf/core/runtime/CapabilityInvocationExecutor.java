package cn.iocoder.yudao.framework.acf.core.runtime;

import java.util.concurrent.Callable;

/**
 * 能力目标方法执行器
 *
 * 负责在线程隔离边界内执行单次目标调用并施加等待超时。业务方可以替换该 SPI，
 * 接入自己的线程池、上下文传播或运行环境。
 *
 * @author bujidao
 */
@FunctionalInterface
public interface CapabilityInvocationExecutor {

    <T> T invoke(Callable<T> invocation, int timeoutMs) throws Exception;

}
