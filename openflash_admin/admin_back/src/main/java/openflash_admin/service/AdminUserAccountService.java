package openflash_admin.service;

public interface AdminUserAccountService {

    void setBanned(Long userId, boolean banned);

    void deleteUser(Long userId);
}
