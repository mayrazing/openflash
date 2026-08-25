package openflash_core.service;

import java.util.regex.Pattern;

/** 定义可登记和可删除的直属上传文件路径。 */
public final class UploadPathPolicy {

    private static final String UPLOAD_PREFIX = "/uploads/";
    private static final int MAX_PATH_LENGTH = 255;
    private static final Pattern DIRECT_UPLOAD_PATH =
        Pattern.compile("^/uploads/[A-Za-z0-9._-]+$");

    private UploadPathPolicy() {}

    /** 返回路径是否声明为本服务直属上传文件引用。 */
    public static boolean isUploadReference(String path) {
        return path != null && path.startsWith(UPLOAD_PREFIX);
    }

    /** 返回路径是否完整符合直属上传文件策略。 */
    public static boolean isDirectUploadPath(String path) {
        try {
            requireDirectUploadPath(path);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 返回符合上传文件名 ASCII 子集和存储列长度的完整相对路径。 */
    public static String requireDirectUploadPath(String relativePath) {
        if (relativePath == null || relativePath.length() > MAX_PATH_LENGTH
                || !DIRECT_UPLOAD_PATH.matcher(relativePath).matches()) {
            throw new IllegalArgumentException("relativePath must be a direct upload path");
        }
        String filename = relativePath.substring(UPLOAD_PREFIX.length());
        if (filename.equals(".") || filename.equals("..")) {
            throw new IllegalArgumentException("relativePath must be a direct upload path");
        }
        return relativePath;
    }
}
