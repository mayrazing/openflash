package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.PlatformAiOffering;

@Mapper
public interface PlatformAiOfferingMapper {
    List<PlatformAiOffering> findVisibleByUserId(@Param("userId") Long userId);

    PlatformAiOffering findByKeyAndUserId(
            @Param("offeringKey") String offeringKey, @Param("userId") Long userId);

    PlatformAiOffering findByIdAndUserId(
            @Param("offeringId") Long offeringId, @Param("userId") Long userId);
}
