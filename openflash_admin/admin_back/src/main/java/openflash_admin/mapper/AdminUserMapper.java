package openflash_admin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import openflash_admin.entity.AdminUser;

@Mapper
public interface AdminUserMapper {

    AdminUser findByUsername(@Param("username") String username);

    AdminUser findById(@Param("id") Long id);

    AdminUser lockById(@Param("id") Long id);

    List<AdminUser> search(
        @Param("query") String query,
        @Param("limit") int limit
    );

    List<Long> lockActiveAdminIds();

    int updateRole(
        @Param("userId") Long userId,
        @Param("role") String role
    );

}
