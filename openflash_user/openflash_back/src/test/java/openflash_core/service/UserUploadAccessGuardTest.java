package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.mapper.UserUploadMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class UserUploadAccessGuardTest {

    private final UserUploadMapper userUploadMapper = mock(UserUploadMapper.class);
    private final UserUploadAccessGuard guard = new UserUploadAccessGuard(userUploadMapper);

    @Test
    void currentOwnerMayAttachDirectUpload() {
        when(userUploadMapper.lockOwnerIdByPath("/uploads/own.jpg")).thenReturn(7L);

        guard.requireMediaUrlsOwnedBy(7L, List.of("/uploads/own.jpg"));

        verify(userUploadMapper).lockOwnerIdByPath("/uploads/own.jpg");
    }

    @Test
    void remoteUrlsRemainAllowedWithoutOwnershipLookup() {
        guard.requireMediaUrlsOwnedBy(7L, List.of(
            "https://cdn.example/uploads/remote.jpg",
            "https://cdn.example/image.jpg"
        ));

        verifyNoInteractions(userUploadMapper);
    }

    @Test
    void localPathsAreDeduplicatedAndLockedInLexicalOrder() {
        when(userUploadMapper.lockOwnerIdByPath("/uploads/p1.jpg")).thenReturn(7L);
        when(userUploadMapper.lockOwnerIdByPath("/uploads/p2.jpg")).thenReturn(7L);

        guard.requireMediaUrlsOwnedBy(7L, List.of(
            "/uploads/p2.jpg",
            "/uploads/p1.jpg",
            "/uploads/p2.jpg"
        ));

        InOrder order = inOrder(userUploadMapper);
        order.verify(userUploadMapper).lockOwnerIdByPath("/uploads/p1.jpg");
        order.verify(userUploadMapper).lockOwnerIdByPath("/uploads/p2.jpg");
        order.verifyNoMoreInteractions();
    }

    @Test
    void missingInvalidAndCrossOwnerUploadsFailClosedWithSafeError() {
        when(userUploadMapper.lockOwnerIdByPath("/uploads/other.jpg")).thenReturn(8L);

        for (String url : List.of(
                "/uploads/missing.jpg",
                "/uploads/../escape.jpg",
                "/uploads/other.jpg")) {
            AppException error = assertThrows(AppException.class,
                () -> guard.requireMediaUrlsOwnedBy(7L, List.of(url)));
            assertEquals(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED, error.getErrorCode());
        }
    }
}
