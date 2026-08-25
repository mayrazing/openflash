package openflash_core.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.RemoteImageDownloader;
import org.springframework.stereotype.Component;

/** 下载浏览器导入提供的远程图片 URL，并把连接目标绑定到已校验 IP。 */
@Component
public class HttpRemoteImageDownloader implements RemoteImageDownloader {

    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;

    /** 连接已校验地址，不重新解析 URL 主机名。 */
    @Override
    public DownloadedImage download(ResolvedImageUrl target) {
        try (Socket socket = openSocket(target)) {
            URI uri = target.uri();
            writeRequest(socket.getOutputStream(), uri);
            HttpResponseHead head = readResponseHead(socket.getInputStream());
            if (head.statusCode() < 200 || head.statusCode() >= 300 || !head.contentType().startsWith("image/")) {
                throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
            }
            byte[] bytes = head.chunked()
                ? readChunked(socket.getInputStream())
                : readLimited(socket.getInputStream(), head.contentLength());
            return new DownloadedImage(head.contentType(), bytes);
        } catch (IOException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
        }
    }

    /** 按已校验 IP 打开 socket；HTTPS 仍用原 host 做 SNI 和证书校验。 */
    private Socket openSocket(ResolvedImageUrl target) throws IOException {
        URI uri = target.uri();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(target.address(), port), CONNECT_TIMEOUT_MILLIS);
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory
                .createSocket(socket, uri.getHost(), port, true);
            sslSocket.setSoTimeout(READ_TIMEOUT_MILLIS);
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if (!isIpLiteral(uri.getHost())) {
                parameters.setServerNames(java.util.List.of(new SNIHostName(uri.getHost())));
            }
            sslSocket.setSSLParameters(parameters);
            sslSocket.startHandshake();
            return sslSocket;
        }
        return socket;
    }

    /** 写最小 HTTP/1.1 GET 请求，Host 保留原 URL 主机。 */
    private void writeRequest(OutputStream output, URI uri) throws IOException {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String host = formatHostHeaderName(uri.getHost());
        int port = uri.getPort();
        boolean defaultPort = port < 0
            || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
            || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
        String hostHeader = defaultPort ? host : host + ":" + port;
        String request = "GET " + path + query + " HTTP/1.1\r\n"
            + "Host: " + hostHeader + "\r\n"
            + "User-Agent: OpenFlash-Browser-Import/1.0\r\n"
            + "Accept: image/*\r\n"
            + "Connection: close\r\n\r\n";
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    /** 读取响应状态行和响应头。 */
    private HttpResponseHead readResponseHead(InputStream input) throws IOException {
        String statusLine = readAsciiLine(input);
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
        }
        int statusCode = Integer.parseInt(parts[1]);
        String contentType = "";
        long contentLength = -1;
        boolean chunked = false;
        String line;
        while ((line = readAsciiLine(input)) != null && !line.isEmpty()) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (name.equalsIgnoreCase("Content-Type")) {
                contentType = value.toLowerCase();
            } else if (name.equalsIgnoreCase("Content-Length")) {
                contentLength = Long.parseLong(value);
            } else if (name.equalsIgnoreCase("Transfer-Encoding") && hasTransferEncoding(value, "chunked")) {
                chunked = true;
            }
        }
        return new HttpResponseHead(statusCode, contentType, contentLength, chunked);
    }

    /** 读取一行 ASCII 头部，去掉 CRLF。 */
    private String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = output.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.US_ASCII);
            }
            output.write(current);
            previous = current;
            if (output.size() > 8192) {
                throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
            }
        }
        return output.size() == 0 ? null : output.toString(StandardCharsets.US_ASCII);
    }

    /** 读取非 chunked 响应体并限制最大大小。 */
    private byte[] readLimited(InputStream input, long contentLength) throws IOException {
        if (contentLength > MAX_BYTES) {
            throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    /** 读取 chunked 响应体并限制最大大小。 */
    private byte[] readChunked(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int total = 0;
            while (true) {
                String sizeLine = readAsciiLine(input);
                if (sizeLine == null) {
                    throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
                }
                int separator = sizeLine.indexOf(';');
                int size = Integer.parseInt(separator >= 0 ? sizeLine.substring(0, separator).trim() : sizeLine.trim(), 16);
                if (size == 0) {
                    while (true) {
                        String trailer = readAsciiLine(input);
                        if (trailer == null || trailer.isEmpty()) {
                            return output.toByteArray();
                        }
                    }
                }
                total += size;
                if (total > MAX_BYTES) {
                    throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
                }
                byte[] chunk = input.readNBytes(size);
                if (chunk.length != size) {
                    throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
                }
                output.write(chunk);
                String lineEnd = readAsciiLine(input);
                if (lineEnd == null || !lineEnd.isEmpty()) {
                    throw new AppException(ErrorCode.BROWSER_IMPORT_IMAGE_TRANSFER_FAILED);
                }
            }
        }
    }

    private boolean hasTransferEncoding(String value, String expected) {
        for (String token : value.split(",")) {
            if (token.trim().equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private String formatHostHeaderName(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private boolean isIpLiteral(String host) {
        return host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || host.contains(":");
    }

    private record HttpResponseHead(int statusCode, String contentType, long contentLength, boolean chunked) {
    }
}
