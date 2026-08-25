package openflash_ai_runtime.mapper;

import openflash_ai_runtime.entity.PlatformAiSecret;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAiSecretMapper {

    PlatformAiSecret findByConnectionId(@Param("connectionId") long connectionId);

    int upsert(PlatformAiSecret secret);
}
