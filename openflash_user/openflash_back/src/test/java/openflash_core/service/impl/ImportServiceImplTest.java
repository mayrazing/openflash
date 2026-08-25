package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import openflash_core.entity.Deck;
import openflash_core.entity.DeckSettings;
import openflash_core.entity.ImportResult;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.mapper.CardMapper;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.mapper.UserSettingsMapper;
import org.springframework.context.ApplicationEventPublisher;
import openflash_core.service.CurrentUserService;
import openflash_core.service.DeckService;
import openflash_core.service.UserUploadAccessGuard;
import openflash_core.service.SystemConfigService;
import tools.jackson.databind.ObjectMapper;

class ImportServiceImplTest {

    @Test
    void importRejectsUnauthenticatedRequestBeforeReadingArchive() throws Exception {
        Fixture fixture = new Fixture();
        doThrow(new AppException(ErrorCode.UNAUTHORIZED))
            .when(fixture.currentUserService)
            .getCurrentUserId();
        org.springframework.web.multipart.MultipartFile file = mock(
            org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

        AppException error = assertThrows(AppException.class,
            () -> fixture.service.importDeckZip(file));

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        verify(file, never()).getInputStream();
    }

    @Test
    void importRejectsZipEntryLargerThanFiftyMebibytes() throws Exception {
        Fixture fixture = new Fixture();
        String oversizedJson = " ".repeat(50 * 1024 * 1024 + 1);
        MockMultipartFile file = new MockMultipartFile(
            "file", "deck.zip", "application/zip", deckZip(oversizedJson));

        assertThrows(AppException.class, () -> fixture.service.importDeckZip(file));
    }

    @Test
    void importUsesConfiguredZipEntryLimit() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.systemConfigService.getLong("import.zip.max-entry-bytes", 50L * 1024 * 1024))
            .thenReturn(32L);
        MockMultipartFile file = new MockMultipartFile(
            "file", "deck.zip", "application/zip", deckZip(" ".repeat(33)));

        assertThrows(AppException.class, () -> fixture.service.importDeckZip(file));
    }

    @Test
    void importCountsUnknownZipEntriesAgainstExpansionLimit() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.systemConfigService.getLong("import.zip.max-entry-bytes", 50L * 1024 * 1024))
            .thenReturn(32L);
        MockMultipartFile file = new MockMultipartFile(
            "file", "deck.zip", "application/zip",
            zipWithEntries("ignored.bin", "x".repeat(33), "decks.json", "{\"decks\":[],\"cards\":[]}"));

        AppException error = assertThrows(AppException.class, () -> fixture.service.importDeckZip(file));

        assertEquals(ErrorCode.IMPORT_ZIP_LIMIT_EXCEEDED, error.getErrorCode());
    }

    @Test
    void backupImportValidatesEveryMediaUrlBeforeClearingExistingDecks() throws Exception {
        Fixture fixture = new Fixture();
        doThrow(new AppException(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED))
            .when(fixture.userUploadAccessGuard)
            .requireMediaUrlsOwnedBy(7L, java.util.List.of("/uploads/other.jpg"));
        MockMultipartFile file = new MockMultipartFile(
            "file", "backup.zip", "application/zip", backupZip("""
                {"decks":[{"id":"old-1","name":"Imported"}],
                 "cards":[{"deckId":"old-1","sideA":"a","sideB":"b",
                           "sideAImage":["/uploads/other.jpg"]}]}
                """));

        AppException error = assertThrows(AppException.class,
            () -> fixture.service.importBackupZip(file));

        assertEquals(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED, error.getErrorCode());
        verify(fixture.deckService, never()).listDecks();
        verify(fixture.deckMapper, never()).insert(any(Deck.class));
    }

    @Test
    void deckImportValidatesEveryMediaUrlBeforeWritingDecks() throws Exception {
        Fixture fixture = new Fixture();
        doThrow(new AppException(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED))
            .when(fixture.userUploadAccessGuard)
            .requireMediaUrlsOwnedBy(7L, java.util.List.of("/uploads/other.jpg"));
        MockMultipartFile file = new MockMultipartFile(
            "file", "deck.zip", "application/zip", deckZip("""
                {"decks":[{"id":"old-1","name":"Imported"}],
                 "cards":[{"deckId":"old-1","sideA":"a","sideB":"b",
                           "sideBImage":["/uploads/other.jpg"]}]}
                """));

        AppException error = assertThrows(AppException.class,
            () -> fixture.service.importDeckZip(file));

        assertEquals(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED, error.getErrorCode());
        verify(fixture.deckMapper, never()).insert(any(Deck.class));
    }

    @Test
    void importDeckZipCreatesSettingsForImportedDeck() throws Exception {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckService deckService = mock(DeckService.class);
        DeckMapper deckMapper = mock(DeckMapper.class);
        CardMapper cardMapper = mock(CardMapper.class);
        CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        UserSettingsMapper userSettingsMapper = mock(UserSettingsMapper.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        ImportServiceImpl service = new ImportServiceImpl(
            currentUserService,
            deckService,
            deckMapper,
            cardMapper,
            cardMediaMapper,
            cardProgressMapper,
            userSettingsMapper,
            deckSettingsMapper,
            new ObjectMapper(),
            mock(ApplicationEventPublisher.class),
            mock(UserUploadAccessGuard.class),
            mock(SystemConfigService.class)
        );
        when(currentUserService.getCurrentUserId()).thenReturn(7L);
        when(deckMapper.insert(any(Deck.class))).thenAnswer(invocation -> {
            Deck deck = invocation.getArgument(0);
            deck.setId(99L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "deck.zip",
            "application/zip",
            deckZip("""
                {
                  "decks": [{"id": "old-1", "name": "Imported"}],
                  "cards": []
                }
                """)
        );

        service.importDeckZip(file);

        verify(deckSettingsMapper).insert(any(DeckSettings.class));
    }

    @Test
    void importDeckZipPreservesExportedDeckSettings() throws Exception {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckService deckService = mock(DeckService.class);
        DeckMapper deckMapper = mock(DeckMapper.class);
        CardMapper cardMapper = mock(CardMapper.class);
        CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        UserSettingsMapper userSettingsMapper = mock(UserSettingsMapper.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        ImportServiceImpl service = new ImportServiceImpl(
            currentUserService,
            deckService,
            deckMapper,
            cardMapper,
            cardMediaMapper,
            cardProgressMapper,
            userSettingsMapper,
            deckSettingsMapper,
            new ObjectMapper(),
            mock(ApplicationEventPublisher.class),
            mock(UserUploadAccessGuard.class),
            mock(SystemConfigService.class)
        );
        when(currentUserService.getCurrentUserId()).thenReturn(7L);
        when(deckMapper.insert(any(Deck.class))).thenAnswer(invocation -> {
            Deck deck = invocation.getArgument(0);
            deck.setId(99L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "deck.zip",
            "application/zip",
            deckZip("""
                {
                  "decks": [{
                    "id": "old-1",
                    "name": "Imported",
                    "settings": {
                      "newCardsPerDay": 22,
                      "targetRetention": 0.8300,
                      "reviewLoadProfile": "relaxed",
                      "duplicateSideAEnabled": false,
                      "duplicateSideBEnabled": true
                    }
                  }],
                  "cards": []
                }
                """)
        );

        ImportResult result = service.importDeckZip(file);

        var captor = org.mockito.ArgumentCaptor.forClass(DeckSettings.class);
        verify(deckSettingsMapper).insert(captor.capture());
        DeckSettings settings = captor.getValue();
        assertEquals(true, result.getSettingsImported());
        assertEquals(99L, settings.getDeckId());
        assertEquals(22, settings.getNewCardsPerDay());
        assertEquals(0, new BigDecimal("0.8300").compareTo(settings.getTargetRetention()));
        assertEquals("relaxed", settings.getReviewLoadProfile());
        assertEquals(false, settings.getDuplicateSideAEnabled());
        assertEquals(true, settings.getDuplicateSideBEnabled());
    }

    @Test
    void importDeckZipNormalizesExportedDeckSettings() throws Exception {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckService deckService = mock(DeckService.class);
        DeckMapper deckMapper = mock(DeckMapper.class);
        CardMapper cardMapper = mock(CardMapper.class);
        CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        UserSettingsMapper userSettingsMapper = mock(UserSettingsMapper.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        ImportServiceImpl service = new ImportServiceImpl(
            currentUserService,
            deckService,
            deckMapper,
            cardMapper,
            cardMediaMapper,
            cardProgressMapper,
            userSettingsMapper,
            deckSettingsMapper,
            new ObjectMapper(),
            mock(ApplicationEventPublisher.class),
            mock(UserUploadAccessGuard.class),
            mock(SystemConfigService.class)
        );
        when(currentUserService.getCurrentUserId()).thenReturn(7L);
        when(deckMapper.insert(any(Deck.class))).thenAnswer(invocation -> {
            Deck deck = invocation.getArgument(0);
            deck.setId(99L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "deck.zip",
            "application/zip",
            deckZip("""
                {
                  "decks": [{
                    "id": "old-1",
                    "name": "Imported",
                    "settings": {
                      "newCardsPerDay": 999,
                      "targetRetention": 1.5000,
                      "reviewLoadProfile": "unknown"
                    }
                  }],
                  "cards": []
                }
                """)
        );

        service.importDeckZip(file);

        var captor = org.mockito.ArgumentCaptor.forClass(DeckSettings.class);
        verify(deckSettingsMapper).insert(captor.capture());
        DeckSettings settings = captor.getValue();
        assertEquals(50, settings.getNewCardsPerDay());
        assertEquals(0, new BigDecimal("0.9700").compareTo(settings.getTargetRetention()));
        assertEquals("standard", settings.getReviewLoadProfile());
    }

    private static byte[] deckZip(String decksJson) throws Exception {
        return zipWithEntries("decks.json", decksJson);
    }

    private static byte[] zipWithEntries(String... namesAndContent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (int index = 0; index < namesAndContent.length; index += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContent[index]));
                zip.write(namesAndContent[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] backupZip(String cardsJson) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("cards.json"));
            zip.write(cardsJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static class Fixture {
        final CurrentUserService currentUserService = mock(CurrentUserService.class);
        final DeckService deckService = mock(DeckService.class);
        final DeckMapper deckMapper = mock(DeckMapper.class);
        final CardMapper cardMapper = mock(CardMapper.class);
        final CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        final CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        final UserSettingsMapper userSettingsMapper = mock(UserSettingsMapper.class);
        final DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        final UserUploadAccessGuard userUploadAccessGuard = mock(UserUploadAccessGuard.class);
        final SystemConfigService systemConfigService = mock(SystemConfigService.class);
        final ImportServiceImpl service = new ImportServiceImpl(
            currentUserService,
            deckService,
            deckMapper,
            cardMapper,
            cardMediaMapper,
            cardProgressMapper,
            userSettingsMapper,
            deckSettingsMapper,
            new ObjectMapper(),
            mock(ApplicationEventPublisher.class),
            userUploadAccessGuard,
            systemConfigService
        );

        Fixture() {
            when(currentUserService.getCurrentUserId()).thenReturn(7L);
        }
    }
}
