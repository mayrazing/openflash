package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户上传文件归属数据访问。 */
@Mapper
public interface UserUploadMapper {

    int insert(@Param("userId") Long userId, @Param("relativePath") String relativePath);

    Long lockOwnerIdByPath(@Param("relativePath") String relativePath);

    List<String> findPathsByUserId(@Param("userId") Long userId);

    int deleteByUserId(@Param("userId") Long userId);
}
