package openflash_admin.service;

import openflash_admin.client.AiRuntimeAdminClient.LoginSnapshot;
import openflash_admin.dto.AdminCodexResponse;

public interface AdminCodexService {

    AdminCodexResponse snapshot();

    void setEnabled(boolean enabled);

    LoginSnapshot startLogin();

    LoginSnapshot cancelLogin();

    void logoutAccount();
}
