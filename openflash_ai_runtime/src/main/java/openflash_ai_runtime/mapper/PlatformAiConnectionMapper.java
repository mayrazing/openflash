package openflash_ai_runtime.mapper;

import java.util.List;
import openflash_ai_runtime.entity.PlatformAiConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAiConnectionMapper {

    List<PlatformAiConnection> findAll();

    PlatformAiConnection findByKey(@Param("connectionKey") String connectionKey);

    PlatformAiConnection findById(@Param("id") long id);

    int insert(PlatformAiConnection connection);

    int update(
            @Param("connectionKey") String connectionKey,
            @Param("baseUrl") String baseUrl,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);

    int setCredentialsConfigured(
            @Param("connectionKey") String connectionKey,
            @Param("configured") boolean configured);

    int deleteByKey(@Param("connectionKey") String connectionKey);
}
