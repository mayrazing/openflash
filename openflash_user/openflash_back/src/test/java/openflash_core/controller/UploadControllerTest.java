package openflash_core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import openflash_core.mapper.UserUploadMapper;
import openflash_core.service.CurrentUserService;
import openflash_core.service.UserUploadRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class UploadControllerTest {

    @TempDir
    Path uploadDir;

    @Test
    void uploadRecordsCreatedFileForCurrentUser() throws Exception {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserUploadRegistry uploadRegistry = mock(UserUploadRegistry.class);
        when(currentUserService.getCurrentUserId()).thenReturn(17L);
        UploadController controller = new UploadController(currentUserService, uploadRegistry, uploadDir);

        UploadController.UploadResponse response = controller.upload(image()).getData();

        assertTrue(response.url().startsWith("/uploads/"));
        verify(uploadRegistry).record(17L, response.url());
        assertTrue(Files.exists(uploadDir.resolve(Path.of(response.url()).getFileName())));
    }

    @Test
    void uploadDeletesNewFileWhenOwnershipInsertFails() throws Exception {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserUploadRegistry uploadRegistry = mock(UserUploadRegistry.class);
        RuntimeException insertFailure = new RuntimeException("owner insert failed");
        when(currentUserService.getCurrentUserId()).thenReturn(17L);
        doThrow(insertFailure).when(uploadRegistry).record(eq(17L), anyString());
        UploadController controller = new UploadController(currentUserService, uploadRegistry, uploadDir);

        RuntimeException actual = assertThrows(RuntimeException.class, () -> controller.upload(image()));

        assertSame(insertFailure, actual);
        try (var files = Files.list(uploadDir)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void uploadCollisionAndRegistryFailurePreserveExistingFile() throws Exception {
        UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID newId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Path existingFile = uploadDir.resolve(existingId + ".jpg");
        Path newFile = uploadDir.resolve(newId + ".jpg");
        Files.writeString(existingFile, "sentinel");
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserUploadRegistry uploadRegistry = mock(UserUploadRegistry.class);
        RuntimeException insertFailure = new RuntimeException("owner insert failed");
        when(currentUserService.getCurrentUserId()).thenReturn(17L);
        doThrow(insertFailure).when(uploadRegistry).record(eq(17L), anyString());
        ArrayDeque<UUID> ids = new ArrayDeque<>(List.of(existingId, newId));
        UploadController controller = new UploadController(
            currentUserService, uploadRegistry, uploadDir, ids::removeFirst);

        RuntimeException actual = assertThrows(RuntimeException.class, () -> controller.upload(image()));

        assertSame(insertFailure, actual);
        assertTrue(Files.exists(existingFile));
        assertEquals("sentinel", Files.readString(existingFile));
        assertFalse(Files.exists(newFile));
    }

    @Test
    void ownershipRegistryRejectsDotSegmentBeforeInsert() {
        UserUploadMapper mapper = mock(UserUploadMapper.class);
        when(mapper.insert(17L, "/uploads/..")).thenReturn(1);
        UserUploadRegistry registry = new UserUploadRegistry(mapper);

        assertThrows(IllegalArgumentException.class, () -> registry.record(17L, "/uploads/.."));

        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/uploads/emoji😀.jpg",
        "/uploads/\u00a0",
        "/uploads/\u2007",
        "/uploads/\u202f",
        "/uploads/.",
        "/uploads/.."
    })
    void ownershipRegistryRejectsPathsOutsideAsciiFilenamePolicy(String relativePath) {
        UserUploadMapper mapper = mock(UserUploadMapper.class);
        UserUploadRegistry registry = new UserUploadRegistry(mapper);

        assertThrows(IllegalArgumentException.class, () -> registry.record(17L, relativePath));

        verifyNoInteractions(mapper);
    }

    @Test
    void ownershipRegistryAcceptsUuidAndAsciiLengthBoundary() {
        UserUploadMapper mapper = mock(UserUploadMapper.class);
        UserUploadRegistry registry = new UserUploadRegistry(mapper);
        String uuidPath = "/uploads/00000000-0000-0000-0000-000000000001.jpg";
        String maxPath = "/uploads/" + "a".repeat(246);
        when(mapper.insert(17L, uuidPath)).thenReturn(1);
        when(mapper.insert(17L, maxPath)).thenReturn(1);

        registry.record(17L, uuidPath);
        registry.record(17L, maxPath);

        verify(mapper).insert(17L, uuidPath);
        verify(mapper).insert(17L, maxPath);
    }

    @Test
    void ownershipRegistryRejectsAsciiPathOverLengthBoundary() {
        UserUploadMapper mapper = mock(UserUploadMapper.class);
        UserUploadRegistry registry = new UserUploadRegistry(mapper);

        assertThrows(IllegalArgumentException.class,
            () -> registry.record(17L, "/uploads/" + "a".repeat(247)));

        verifyNoInteractions(mapper);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[] {1, 2, 3});
    }
}
