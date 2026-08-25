package openflash_admin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只读取平台 AI 非敏感 metadata 和权限, 不接触 secret 表. */
@Mapper
public interface AdminPlatformAiMapper {

    List<CatalogRow> findCatalogRows();

    List<EnabledOfferingRow> findEnabledOfferings();

    List<UserAccessOverrideRow> findUserAccessOverrides(
        @Param("userIds") List<Long> userIds);

    EnabledOfferingRow findEnabledOfferingByKey(
        @Param("offeringKey") String offeringKey);

    CatalogRow findCliOffering(@Param("cliKey") String cliKey);

    record CatalogRow(
        long connectionId,
        String connectionKey,
        String kind,
        String protocol,
        String cliKey,
        String displayName,
        String baseUrl,
        boolean credentialsConfigured,
        boolean connectionEnabled,
        int connectionSortOrder,
        Long offeringId,
        String offeringKey,
        String modelKey,
        Boolean offeringEnabled,
        Boolean defaultAccess,
        Integer offeringSortOrder
    ) {
        public CatalogRow(
                long connectionId, String connectionKey, String kind, String protocol,
                String cliKey, String baseUrl, boolean credentialsConfigured,
                boolean connectionEnabled, int connectionSortOrder, Long offeringId,
                String offeringKey, String modelKey, Boolean offeringEnabled,
                Boolean defaultAccess, Integer offeringSortOrder) {
            this(connectionId, connectionKey, kind, protocol, cliKey, null, baseUrl,
                    credentialsConfigured, connectionEnabled, connectionSortOrder, offeringId,
                    offeringKey, modelKey, offeringEnabled, defaultAccess, offeringSortOrder);
        }
    }

    record EnabledOfferingRow(
        long offeringId,
        String offeringKey,
        String modelKey,
        boolean defaultAccess,
        int offeringSortOrder,
        String connectionKey,
        String kind,
        String protocol,
        String cliKey
    ) {
    }

    record UserAccessOverrideRow(long userId, String offeringKey, boolean enabled) {
    }
}
