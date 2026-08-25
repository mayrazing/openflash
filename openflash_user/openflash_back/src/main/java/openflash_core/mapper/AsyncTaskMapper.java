package openflash_core.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.AsyncTask;

@Mapper
public interface AsyncTaskMapper {

    int upsertTask(
        @Param("bizKey") String bizKey,
        @Param("taskType") String taskType,
        @Param("payload") String payload,
        @Param("ownerUserId") Long ownerUserId,
        @Param("maxRetryCount") int maxRetryCount,
        @Param("priority") int priority,
        @Param("rescheduleFailed") boolean rescheduleFailed
    );

    List<AsyncTask> findClaimableBatch(
        @Param("now") LocalDateTime now,
        @Param("limit") int limit
    );

    int claimById(
        @Param("id") Long id,
        @Param("now") LocalDateTime now,
        @Param("leaseUntil") LocalDateTime leaseUntil
    );

    int markCompleted(
        @Param("id") Long id,
        @Param("leaseUntil") LocalDateTime leaseUntil
    );

    int markRetry(
        @Param("id") Long id,
        @Param("leaseUntil") LocalDateTime leaseUntil,
        @Param("nextRetryAt") LocalDateTime nextRetryAt,
        @Param("lastError") String lastError
    );

    int markFailed(
        @Param("id") Long id,
        @Param("leaseUntil") LocalDateTime leaseUntil,
        @Param("lastError") String lastError
    );

    int deleteCompletedBefore(
        @Param("before") LocalDateTime before,
        @Param("limit") int limit
    );
}
