package cn.iocoder.yudao.framework.acf.core.runtime;

/**
 * 能力执行异常分类器
 *
 * 业务模块可以替换默认实现，将领域异常映射为稳定错误码和重试语义。
 *
 * @author bujidao
 */
@FunctionalInterface
public interface CapabilityExceptionClassifier {

    /**
     * The returned public message may be exposed through results, tools, and audit records. Custom
     * classifiers must return a reviewed and redacted message instead of the raw exception message.
     */

    CapabilityExceptionClassification classify(Throwable throwable);

}
