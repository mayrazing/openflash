package openflash_core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BrowserImportErrorCodeTest {

    /** 验证浏览器导入错误码保持稳定，前端可据此显示固定文案。 */
    @Test
    void browserImportInvalidImageUrlHasCorrectValue() {
        assertEquals(40090, ErrorCode.BROWSER_IMPORT_INVALID_IMAGE_URL.value());
    }

    @Test
    void browserImportEmptyContentHasCorrectValue() {
        assertEquals(40091, ErrorCode.BROWSER_IMPORT_EMPTY_CONTENT.value());
    }

    @Test
    void browserImportImageTransferFailedHasCorrectValue() {
        assertEquals(40092, ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED.value());
    }
}
