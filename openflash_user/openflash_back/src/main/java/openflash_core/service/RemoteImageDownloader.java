package openflash_core.service;

import java.net.InetAddress;
import java.net.URI;

/** 下载浏览器导入提供的远程图片 URL。 */
@FunctionalInterface
public interface RemoteImageDownloader {

    /** 下载单张图片，返回原始字节和 Content-Type。 */
    DownloadedImage download(ResolvedImageUrl url);

    record DownloadedImage(String contentType, byte[] bytes) {
    }

    record ResolvedImageUrl(URI uri, InetAddress address) {
    }
}
