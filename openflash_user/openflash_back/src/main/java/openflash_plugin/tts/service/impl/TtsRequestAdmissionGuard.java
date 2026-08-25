package openflash_plugin.tts.service.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import openflash_core.common.AppException;
import openflash_plugin.tts.common.TtsErrorCode;

/**
 * 限制正在等待 TTS 结果的请求总数和单用户请求数, 饱和时立即拒绝.
 */
final class TtsRequestAdmissionGuard {

    private final Semaphore globalPermits;
    private final int perUserLimit;
    private final ConcurrentHashMap<Long, UserPermit> userPermits = new ConcurrentHashMap<>();

    TtsRequestAdmissionGuard(int globalLimit, int perUserLimit) {
        if (globalLimit < 1 || perUserLimit < 1) {
            throw new IllegalArgumentException("TTS 请求并发上限必须大于 0");
        }
        this.globalPermits = new Semaphore(globalLimit, true);
        this.perUserLimit = perUserLimit;
    }

    <T> T call(Long ownerUserId, Callable<T> action) throws Exception {
        if (!globalPermits.tryAcquire()) {
            throw new AppException(TtsErrorCode.TTS_BUSY);
        }

        UserPermit userPermit = null;
        boolean userPermitAcquired = false;
        try {
            if (ownerUserId != null) {
                userPermit = retainUserPermit(ownerUserId);
                userPermitAcquired = userPermit.permits.tryAcquire();
                if (!userPermitAcquired) {
                    throw new AppException(TtsErrorCode.TTS_BUSY);
                }
            }
            return action.call();
        } finally {
            if (userPermit != null) {
                if (userPermitAcquired) {
                    userPermit.permits.release();
                }
                releaseUserPermit(ownerUserId, userPermit);
            }
            globalPermits.release();
        }
    }

    int trackedUserCountForTest() {
        return userPermits.size();
    }

    private UserPermit retainUserPermit(Long ownerUserId) {
        return userPermits.compute(ownerUserId, (ignored, current) -> {
            UserPermit retained = current == null ? new UserPermit(perUserLimit) : current;
            retained.references++;
            return retained;
        });
    }

    private void releaseUserPermit(Long ownerUserId, UserPermit expected) {
        userPermits.computeIfPresent(ownerUserId, (ignored, current) -> {
            if (current != expected) {
                return current;
            }
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private static final class UserPermit {
        private final Semaphore permits;
        private int references;

        private UserPermit(int limit) {
            this.permits = new Semaphore(limit, true);
        }
    }
}
