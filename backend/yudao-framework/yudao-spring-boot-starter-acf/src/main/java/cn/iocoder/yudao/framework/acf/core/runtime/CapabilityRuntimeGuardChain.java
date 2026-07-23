package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行时保护器执行链
 *
 * @author bujidao
 */
public final class CapabilityRuntimeGuardChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(CapabilityRuntimeGuardChain.class);

    private final List<CapabilityRuntimeGuard> guards;

    public CapabilityRuntimeGuardChain(List<CapabilityRuntimeGuard> guards) {
        List<CapabilityRuntimeGuard> candidates = guards == null ? List.of() : guards;
        validateCodes(candidates);
        this.guards = candidates.stream()
                .sorted(Comparator.comparingInt(CapabilityRuntimeGuard::order)
                        .thenComparing(CapabilityRuntimeGuard::code))
                .toList();
    }

    public Lease acquire(CapabilityRuntimeGuardContext context) {
        Objects.requireNonNull(context, "context");
        List<AcquiredGuard> acquired = new ArrayList<>();
        for (CapabilityRuntimeGuard guard : guards) {
            try {
                if (!guard.supports(context)) {
                    continue;
                }
                CapabilityRuntimeGuardResult result = Objects.requireNonNull(
                        guard.acquire(context), "Runtime guard result must not be null: " + guard.code());
                if (!guard.code().equals(result.getGuardCode())) {
                    throw new IllegalStateException("Runtime guard result code does not match guard: " + guard.code());
                }
                if (!result.isAllowed()) {
                    // 后置 Guard 拒绝时，前面已获取的资源必须立即逆序回滚。
                    releaseAcquired(acquired, context);
                    return Lease.rejected(context, result);
                }
                acquired.add(new AcquiredGuard(guard, result.getLeaseState()));
            } catch (RuntimeException exception) {
                releaseAcquired(acquired, context);
                CapabilityRuntimeGuardResult result = CapabilityRuntimeGuardResult.rejected(
                        guard.code(), AcfCapabilityErrorCodes.RUNTIME_GUARD_ERROR,
                        readableMessage(exception), false);
                return Lease.rejected(context, result);
            }
        }
        return Lease.acquired(context, acquired);
    }

    private void validateCodes(List<CapabilityRuntimeGuard> guards) {
        Set<String> codes = new HashSet<>();
        for (CapabilityRuntimeGuard guard : guards) {
            Objects.requireNonNull(guard, "Runtime guard must not be null");
            if (guard.code() == null || guard.code().isBlank()) {
                throw new IllegalArgumentException("Runtime guard code must not be blank");
            }
            if (!codes.add(guard.code())) {
                throw new IllegalArgumentException("Duplicate runtime guard code: " + guard.code());
            }
        }
    }

    private static void releaseAcquired(List<AcquiredGuard> acquired,
                                        CapabilityRuntimeGuardContext context) {
        for (int index = acquired.size() - 1; index >= 0; index--) {
            AcquiredGuard acquiredGuard = acquired.get(index);
            try {
                acquiredGuard.guard().release(context, acquiredGuard.leaseState());
            } catch (RuntimeException exception) {
                LOGGER.warn("释放 ACF 运行时保护器失败，guard={}，capability={}",
                        acquiredGuard.guard().code(), context.getDefinition().getName(), exception);
            }
        }
    }

    private String readableMessage(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    /**
     * 一次保护器获取租约，负责保证收口回调最多执行一次。
     */
    public static final class Lease {

        private final CapabilityRuntimeGuardContext context;
        private final List<AcquiredGuard> acquired;
        private final CapabilityRuntimeGuardResult rejection;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(CapabilityRuntimeGuardContext context, List<AcquiredGuard> acquired,
                      CapabilityRuntimeGuardResult rejection) {
            this.context = context;
            this.acquired = List.copyOf(acquired);
            this.rejection = rejection;
        }

        private static Lease acquired(CapabilityRuntimeGuardContext context,
                                      List<AcquiredGuard> acquired) {
            return new Lease(context, acquired, null);
        }

        private static Lease rejected(CapabilityRuntimeGuardContext context,
                                      CapabilityRuntimeGuardResult rejection) {
            return new Lease(context, List.of(), rejection);
        }

        public boolean isAllowed() {
            return rejection == null;
        }

        public CapabilityRuntimeGuardResult getRejection() {
            return rejection;
        }

        public void release() {
            close((guard, currentContext) -> guard.guard().release(currentContext, guard.leaseState()));
        }

        public void onSuccess(CapabilityResult result) {
            close((guard, currentContext) ->
                    guard.guard().onSuccess(currentContext, result, guard.leaseState()));
        }

        public void onFailure(CapabilityResult result, Throwable cause) {
            close((guard, currentContext) ->
                    guard.guard().onFailure(currentContext, result, cause, guard.leaseState()));
        }

        private void close(GuardCallback callback) {
            if (!isAllowed() || !closed.compareAndSet(false, true)) {
                return;
            }
            // 与获取顺序相反收口，保证存在依赖关系的 Guard 按栈语义释放资源。
            for (int index = acquired.size() - 1; index >= 0; index--) {
                AcquiredGuard guard = acquired.get(index);
                try {
                    callback.invoke(guard, context);
                } catch (RuntimeException exception) {
                    LOGGER.warn("收口 ACF 运行时保护器失败，guard={}，capability={}",
                            guard.guard().code(), context.getDefinition().getName(), exception);
                }
            }
        }
    }

    @FunctionalInterface
    private interface GuardCallback {

        void invoke(AcquiredGuard guard, CapabilityRuntimeGuardContext context);

    }

    private record AcquiredGuard(CapabilityRuntimeGuard guard, Object leaseState) {
    }

}
