package openflash_core.controller;

import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.ImportResult;
import openflash_core.service.ImportService;

/**
 * 处理备份文件导入接口。
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * 接收备份 zip，并恢复到数据库。
     */
    @PostMapping("/backup")
    public ApiResponse<ImportResult> importBackup(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(importService.importBackupZip(file));
    }

    /**
     * 接收卡包 zip，合并导入（不清空现有数据）。
     */
    @PostMapping("/deck")
    public ApiResponse<ImportResult> importDeck(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(importService.importDeckZip(file));
    }
}
