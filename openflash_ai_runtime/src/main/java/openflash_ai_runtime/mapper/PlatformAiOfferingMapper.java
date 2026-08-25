package openflash_ai_runtime.mapper;

import java.util.List;
import openflash_ai_runtime.entity.PlatformAiOffering;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAiOfferingMapper {

    List<PlatformAiOffering> findByConnectionId(@Param("connectionId") long connectionId);

    PlatformAiOffering findByKey(@Param("offeringKey") String offeringKey);

    List<UsableOfferingRow> findUsableByUserId(@Param("userId") long userId);

    UsableOfferingRow findUsableByKeyAndUserId(
            @Param("offeringKey") String offeringKey,
            @Param("userId") long userId);

    int insert(PlatformAiOffering offering);

    int update(
            @Param("offeringKey") String offeringKey,
            @Param("modelKey") String modelKey,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int updateEnabledByConnectionId(
            @Param("connectionId") long connectionId,
            @Param("enabled") boolean enabled);

    int updateDefaultAccess(
            @Param("offeringKey") String offeringKey,
            @Param("enabled") boolean enabled);

    int deleteByKey(@Param("offeringKey") String offeringKey);

    /** 权限、连接和 offering 均在当前查询中确认可用的权威行. */
    record UsableOfferingRow(
            long offeringId,
            String offeringKey,
            String modelKey,
            boolean offeringEnabled,
            boolean defaultAccess,
            int offeringSortOrder,
            long connectionId,
            String connectionKey,
            String kind,
            String protocol,
            String cliKey,
            String baseUrl,
            boolean credentialsConfigured,
            boolean connectionEnabled,
            int connectionSortOrder) {
    }
}
