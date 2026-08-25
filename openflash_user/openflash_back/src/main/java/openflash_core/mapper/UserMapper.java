package openflash_core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_core.entity.User;

@Mapper
public interface UserMapper {

    User findByUsername(String username);

    User findById(Long id);

    User lockById(@Param("id") Long id);

    List<Long> lockActiveAdminIds();

    int insert(User user);

    int updatePasswordHashAndIncrementAuthVersion(
        @Param("id") Long id,
        @Param("expectedHash") String expectedHash,
        @Param("passwordHash") String passwordHash
    );

    int updateBannedAndIncrementAuthVersion(
        @Param("id") Long id,
        @Param("banned") boolean banned
    );

    int deleteById(@Param("id") Long id);
}
