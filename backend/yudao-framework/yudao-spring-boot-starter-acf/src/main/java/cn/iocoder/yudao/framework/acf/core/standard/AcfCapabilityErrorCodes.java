package cn.iocoder.yudao.framework.acf.core.standard;

/**
 * ACF 公共错误码
 *
 * 业务模块应定义自己的稳定错误码，仅在语义完全一致时复用这里的公共错误码。
 *
 * @author bujidao
 */
public final class AcfCapabilityErrorCodes {

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String CAPABILITY_NOT_FOUND = "CAPABILITY_NOT_FOUND";
    public static final String POLICY_ERROR = "POLICY_ERROR";
    public static final String POLICY_DENIED = "POLICY_DENIED";
    public static final String CONFIRMATION_ERROR = "CONFIRMATION_ERROR";
    public static final String CONFIRMATION_UNAVAILABLE = "CONFIRMATION_UNAVAILABLE";
    public static final String CONFIRM_TOKEN_INVALID = "CONFIRM_TOKEN_INVALID";
    public static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    public static final String IDEMPOTENCY_UNAVAILABLE = "IDEMPOTENCY_UNAVAILABLE";
    public static final String IDEMPOTENCY_ERROR = "IDEMPOTENCY_ERROR";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String RUNTIME_TIMEOUT = "RUNTIME_TIMEOUT";
    public static final String INVOKE_ERROR = "INVOKE_ERROR";

    public static final String PARAMETER_MISSING = "ACF_PARAMETER_MISSING";
    public static final String TARGET_NOT_FOUND = "ACF_TARGET_NOT_FOUND";
    public static final String RESULT_AMBIGUOUS = "ACF_RESULT_AMBIGUOUS";
    public static final String PRECONDITION_NOT_MET = "ACF_PRECONDITION_NOT_MET";
    public static final String DATA_INCOMPLETE = "ACF_DATA_INCOMPLETE";

    private AcfCapabilityErrorCodes() {
    }

}
