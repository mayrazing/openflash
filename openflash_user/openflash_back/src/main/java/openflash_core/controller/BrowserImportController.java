package openflash_core.controller;

import openflash_core.dto.ApiResponse;
import openflash_core.entity.Card;
import openflash_core.service.impl.BrowserImportServiceImpl;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供浏览器插件导入专用接口，隔离插件入口与主站普通建卡。 */
@RestController
@RequestMapping("/api/browser-import")
public class BrowserImportController {

    private final BrowserImportServiceImpl browserImportService;

    public BrowserImportController(BrowserImportServiceImpl browserImportService) {
        this.browserImportService = browserImportService;
    }

    /** 转存远程图片 URL，返回每项成功路径或失败原因。 */
    @PostMapping("/images/transfer")
    public ApiResponse<BrowserImportServiceImpl.TransferImagesResponse> transferImages(
        @RequestBody BrowserImportServiceImpl.TransferImagesRequest request
    ) {
        return ApiResponse.success(browserImportService.transferImages(request));
    }

    /** 为指定卡包创建浏览器导入卡片。 */
    @PostMapping("/decks/{deckId}/cards")
    public ApiResponse<Card> createImportedCard(
        @PathVariable Long deckId,
        @RequestBody BrowserImportServiceImpl.ImportCardRequest request
    ) {
        return ApiResponse.success(browserImportService.createImportedCard(deckId, request));
    }
}
