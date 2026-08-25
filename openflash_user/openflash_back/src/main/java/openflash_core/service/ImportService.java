package openflash_core.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import openflash_core.entity.ImportResult;

/**
 * 负责把备份文件导入到数据库。
 */
public interface ImportService {

    /**
     * 导入当前备份 zip 文件。
     */
    ImportResult importBackupZip(MultipartFile file) throws IOException;

    /**
     * 导入卡包 zip 文件（合并模式，不清空现有数据）。
     */
    ImportResult importDeckZip(MultipartFile file) throws IOException;
}
