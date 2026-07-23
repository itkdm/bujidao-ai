package cn.iocoder.yudao.framework.acf.core.runtime;

/**
 * ACF 运行指标记录 SPI
 *
 * Starter 只发布一次调用的指标事件，不绑定 Micrometer、数据库或远程监控平台。
 * 业务模块可替换默认空实现，将事件转换为自身使用的计数器、计时器或日志指标。
 *
 * @author bujidao
 */
@FunctionalInterface
public interface CapabilityRuntimeMetricsRecorder {

    void record(CapabilityRuntimeMetricRecord record);

    static CapabilityRuntimeMetricsRecorder noop() {
        return record -> {
        };
    }

}
