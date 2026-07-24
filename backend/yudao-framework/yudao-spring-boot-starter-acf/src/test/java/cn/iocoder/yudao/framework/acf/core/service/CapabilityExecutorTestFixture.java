package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationCompletion;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationExecutor;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationHandle;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationInterruptResult;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardChain;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeMetricsRecorder;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityRuntimePolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class CapabilityExecutorTestFixture {

    private CapabilityExecutorTestFixture() {
    }

    public static CapabilityExecutor create(CapabilityRegistry registry,
                                            CapabilityGovernanceService governanceService,
                                            ObjectMapper objectMapper, Validator validator) {
        return create(registry, governanceService, null, null, null,
                new DefaultCapabilityExceptionClassifier(), objectMapper, validator);
    }

    public static CapabilityInvocationExecutor immediateInvocationExecutor() {
        return new ImmediateInvocationExecutor();
    }

    public static CapabilityExecutor create(CapabilityRegistry registry,
                                            CapabilityGovernanceService governanceService,
                                            CapabilityConfirmationService confirmationService,
                                            CapabilityIdempotencyService idempotencyService,
                                            CapabilityAuditService auditService,
                                            CapabilityExceptionClassifier exceptionClassifier,
                                            ObjectMapper objectMapper, Validator validator) {
        return new CapabilityExecutor(registry, governanceService, confirmationService, idempotencyService,
                auditService, exceptionClassifier, new DefaultCapabilityRuntimePolicyService(),
                new CapabilityRuntimeGuardChain(List.of()), new ImmediateInvocationExecutor(),
                CapabilityRuntimeMetricsRecorder.noop(), new CapabilityRequestDigestGenerator(objectMapper),
                objectMapper, validator);
    }

    private static final class ImmediateInvocationExecutor implements CapabilityInvocationExecutor {

        @Override
        public CapabilityInvocationHandle submit(Callable<CapabilityResult> invocation) {
            CapabilityInvocationCompletion completion;
            try {
                completion = CapabilityInvocationCompletion.completed(invocation.call());
            } catch (Throwable throwable) {
                completion = CapabilityInvocationCompletion.failed(throwable);
            }
            return new CompletedInvocationHandle(completion);
        }
    }

    private static final class CompletedInvocationHandle implements CapabilityInvocationHandle {

        private final CapabilityInvocationCompletion completion;

        private CompletedInvocationHandle(CapabilityInvocationCompletion completion) {
            this.completion = completion;
        }

        @Override
        public CapabilityResult await(int timeoutMs) throws ExecutionException, TimeoutException {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("timeoutMs must be greater than zero");
            }
            if (completion.getFailure() != null) {
                throw new ExecutionException(completion.getFailure());
            }
            return completion.getResult();
        }

        @Override
        public CapabilityInvocationInterruptResult interrupt() {
            return CapabilityInvocationInterruptResult.ALREADY_TERMINATED;
        }

        @Override
        public CompletionStage<CapabilityInvocationCompletion> completion() {
            return CompletableFuture.completedFuture(completion);
        }
    }

}
