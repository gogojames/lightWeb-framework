package com.lightweb.framework.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.lightweb.framework.util.FilePart;
import com.lightweb.framework.util.RepeatableInputStream;
import com.lightweb.framework.util.TempFileHandler;

/**
 * HTTP请求解析器
 * 使用Java 25新特性如模式匹配、文本块等实现高效解析
 * 
 * 资源管理规范：
 * 1. 所有实现 AutoCloseable 的资源（如 InputStream, OutputStream）必须在 try-with-resources 块中管理。
 * 2. 传入 RequestParser 的 RepeatableInputStream 不会被本类关闭，以允许调用者复用或重置流。
 * 3. 内部创建的流（如文件输出流）必须在操作完成后立即关闭。
 * 4. 异常处理需确保资源被正确释放，并在必要时清理临时文件。
 */
public final class RequestParser {
    private static final Pattern HEADER_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("([^&=]+)=([^&]*)");
    
    // 安全配置
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_BODY_SIZE = 50 * 1024 * 1024; // 50MB
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(".exe", ".sh", ".bat", ".cmd", ".com", ".scr");
    private static int readedSize = 0;
    
    private RequestParser() {}
    
    /**
     * 解析HTTP请求
     */
    public static Request parse(RepeatableInputStream inputStream) throws Exception {
        readedSize = 0;
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        // 基于字节解析以避免 Reader 预读导致主体丢失
        String requestHeader = inputStream.readHeaderString(); //readLineBytes(inputStream);
       readedSize = inputStream.getReadedSize();

        if (requestHeader == null || requestHeader.isBlank()) {
            throw new IllegalArgumentException("Invalid request line");
        }
        String[] reqHeadArry = requestHeader.split("\n");
        var requestParts = Optional.ofNullable(reqHeadArry[0]).orElse("").split(" ");
        if (requestParts.length != 3) {
            throw new IllegalArgumentException("Malformed request line: " + requestHeader);
        }
        
        String method = requestParts[0];
        String fullPath = URLDecoder.decode(requestParts[1], StandardCharsets.UTF_8);
        String protocol = requestParts[2];
        
        // 验证请求方法
        if (!isValidMethod(method)) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            
        }

        // 分离路径和查询参数
        var pathAndQuery = parsePathAndQuery(fullPath);
        String path = pathAndQuery.getKey();
        Map<String, String> queryParams = new HashMap<>(pathAndQuery.getValue());
        Map<String, FilePart> files = new HashMap<>();
        // 解析头部（字节级，直到空行）
        Map<String, String> headers = Arrays.stream(reqHeadArry)
                                       // .filter(s->HEADER_PATTERN.matcher(s).matches())
                                        .map(s -> s.split(":", 2)) // 分割为键值数组，最多分割两次
                                        .filter(s-> s.length == 2)
                                        .collect(Collectors.toMap(
                                            parts -> parts[0].trim().toLowerCase(),
                                            parts -> parts[1].trim()
                                        ));  //parseHeadersBytes(inputStream);

        Optional<String> contentType = getContentType(headers);
        boolean isMultipart = contentType.isPresent() && contentType.get().startsWith("multipart/form-data");
        // 解析请求体
        String body = isMultipart ? "" : parseBody(inputStream, headers);
        int contentLength = getContentLength(headers).orElse(0);
        var req = new Request(method, path, protocol, headers, queryParams, Map.of(), body, inputStream,files);
        if(isMultipart){
            try {
              req =  handleMultipart(inputStream, contentType.get(), queryParams, files,contentLength,req);
            } catch (Exception e) {
                 if (e instanceof IllegalArgumentException) {
                     throw (IllegalArgumentException) e;
                 }
                 throw new RuntimeException("Multipart parsing failed", e);
            }
        }
        
        return req;  //new Request(method, path, protocol, headers, queryParams, Map.of(), body, inputStream,files);
              
    }
    
    /**
     * 解析路径和查询参数
     */
    private static Map.Entry<String, Map<String, String>> parsePathAndQuery(String fullPath) {
        int queryStart = fullPath.indexOf('?');
        String path, queryString;
        
        if (queryStart != -1) {
            path = fullPath.substring(0, queryStart);
            queryString = fullPath.substring(queryStart + 1);
        } else {
            path = fullPath;
            queryString = "";
        }
        
        Map<String, String> queryParams = parseQueryString(queryString);
        return Map.entry(path, queryParams);
    }
    
    /**
     * 解析查询字符串
     */
    private static Map<String, String> parseQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return Map.of();
        }
        
        var params = new HashMap<String, String>();
        var matcher = QUERY_PARAM_PATTERN.matcher(queryString);
        
        while (matcher.find()) {
            String key = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        
        return Map.copyOf(params);
    }
    
    /**
     * 解析HTTP头部
     */
    private static Map<String, String> parseHeadersBytes(RepeatableInputStream in) throws Exception {
        var headers = new HashMap<String, String>();
        while (true) {
            String line = readLineBytes(in);
            if (line == null || line.isBlank()) {
                break; // 空行结束
            }
            var matcher = HEADER_PATTERN.matcher(line);
            if (matcher.matches()) {
                String name = matcher.group(1).trim();
                String value = matcher.group(2).trim();
                headers.put(name.toLowerCase(), value);
            }
        }
        return Map.copyOf(headers);
    }

    /**
     * 字节级读取一行，支持 CRLF 或 LF 作为行结束，返回不包含行终止符的字符串。
     * 到达 EOF 且未读取到任何字节时返回 null。
     */
    private static String readLineBytes(RepeatableInputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int b;
        boolean sawAny = false;
        while ((b = in.read()) != -1) {
            sawAny = true;
            if (b == '\r') {
                int n = in.read();
                if (n == '\n') {
                    break;
                } else if (n != -1) {
                    // 非标准，仅 CR 情况，将后续字节作为内容
                    sb.append((char) n);
                }
                break;
            } else if (b == '\n') {
                break;
            } else {
                sb.append((char) b);
            }
        }
        if (!sawAny && sb.length() == 0) {
            return null;
        }
        readedSize +=sb.toString().getBytes(StandardCharsets.UTF_8).length;
        return sb.toString();
    }
    
    /**
     * 解析请求体
     * 资源管理：使用 try-with-resources 确保 BufferedReader 和 InputStreamReader 正确关闭。
     * 为了不关闭底层的 RepeatableInputStream（以便后续可能重读），使用了 CloseShieldInputStream 包装器。
     */
    private static String parseBody(InputStream inputStream, Map<String, String> headers) throws IOException {
        int contentLength = getContentLength(headers).orElse(0);
        if (contentLength == 0) {
            return "";
        }
        
        if (contentLength > MAX_BODY_SIZE) {
            throw new IllegalArgumentException("Request body too large: " + contentLength + " bytes");
        }
        
        // 使用 CloseShieldInputStream 防止 BufferedReader 关闭底层流
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new CloseShieldInputStream(inputStream), 
                getCharsetFromHeaders(headers)))) {
            
            char[] buffer = new char[8192]; // Use 8KB buffer
            StringBuilder bodyBuilder = new StringBuilder(Math.min(contentLength, 1024 * 1024));
            int remaining = contentLength;
            
            while (remaining > 0) {
                int toRead = Math.min(buffer.length, remaining);
                int read = reader.read(buffer, 0, toRead);
                if (read == -1) break;
                bodyBuilder.append(buffer, 0, read);
                remaining -= read;
            }
            return bodyBuilder.toString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid content-length header", e);
        }
    }

    /**
     * 防止底层流被关闭的 InputStream 包装器
     */
    private static class CloseShieldInputStream extends java.io.FilterInputStream {
        public CloseShieldInputStream(InputStream in) {
            super(in);
        }
        @Override
        public void close() throws IOException {
            // Do nothing to keep underlying stream open
        }
    }
    
    /**
     * 从头部获取字符集
     */
    private static Charset getCharsetFromHeaders(Map<String, String> headers) {
        String contentType = headers.get("content-type");
        if (contentType != null) {
            // 解析charset参数，如: text/html; charset=ISO-8859-1
            String[] parts = contentType.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.toLowerCase().startsWith("charset=")) {
                    String charsetName = part.substring(8).trim();
                    try {
                        return Charset.forName(charsetName);
                    } catch (Exception e) {
                        // 使用默认字符集
                    }
                }
            }
        }
        return StandardCharsets.UTF_8; // 默认使用UTF-8
    }
    
    /**
     * 验证请求方法
     */
    public static boolean isValidMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" -> true;
            default -> false;
        };
    }
    
    /**
     * 获取内容长度
     */
    public static OptionalInt getContentLength(Map<String, String> headers) {
        String value = headers.get("content-length");
        if (value == null) {
            return OptionalInt.empty();
        }
        value = value.trim();
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            int len = Integer.parseInt(value);
            return len >= 0 ? OptionalInt.of(len) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public static Optional<String> getContentType(Map<String, String> headers) {
        return Optional.ofNullable(headers.get("content-type"));
       
    }

    private static String extractBoundary(String contentType) {
        int idx = contentType.indexOf("boundary=");
        if (idx == -1) return null;
        String b = contentType.substring(idx + 9);
        if (b.startsWith("\"") && b.endsWith("\"")) {
            b = b.substring(1, b.length() - 1);
        }
        return "--" + b;  // 标准 boundary 前有 --
    }

    private static String extractParam(String disposition, String paramName) {
        int idx = disposition.indexOf(paramName + "=\"");
        if (idx == -1) return null;
        int start = idx + paramName.length() + 2;
        int end = disposition.indexOf('"', start);
        return disposition.substring(start, end);
    }

    private static Request handleMultipart(RepeatableInputStream inputStream, String contentType, 
                                      Map<String, String> queryParams, Map<String, FilePart> files, int contentLength,Request req) throws IOException {
        String boundary = extractBoundary(contentType);
        if (boundary == null) return req;
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.UTF_8);
        byte[] endBoundaryBytes = (boundary + "--").getBytes(StandardCharsets.UTF_8);
        readedSize+=endBoundaryBytes.length;
        int contentLengthLeft = contentLength - readedSize;
       // try {
            MultipartParser parser = new MultipartParser(inputStream, boundaryBytes, endBoundaryBytes);
            parser.parse((headers, bodyStream) -> {
                 String disposition = headers.get("content-disposition");
                 if (disposition == null) return;
                 String filename = extractParam(disposition, "filename");
                 String name = extractParam(disposition, "name");
                 if (filename != null && name != null) {
                    var filePart = processFileUpload(filename, headers, bodyStream, contentLengthLeft,boundaryBytes);
                    files.put(name, filePart);
                 } else if (name != null) {
                     try (InputStream is = bodyStream) {
                         String value = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                         queryParams.put(name, value);
                     }
                 }
             });
       // }
        return  Request.of(req,queryParams,files);
    }

    private static FilePart processFileUpload(String filename,  Map<String, String> headers, 
                                        InputStream bodyStream, int contentLength,byte[] boundaryBytes) throws IOException {
        if (filename == null ||  bodyStream == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        
        // 安全检查：文件名
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename: " + filename);
        }
        // 安全检查：恶意扩展名
        String ext = getExtension(filename);
        if (BLOCKED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("Blocked file type: " + ext);
        }
        
        Path savePath = TempFileHandler.createSystemTempFile(Paths.get("temp",filename),".",filename);  //Paths.get("tmp/upload", UUID.randomUUID().toString() + "_" + filename);
        //Files.createDirectories(savePath.getParent());
        
        // 资源管理：确保输出流和输入流（如果需要）都被关闭
        // LimitedInputStream.close() 不会关闭底层流，所以这里安全
       // var contentLengthLeft = contentLength;
        try (InputStream is = bodyStream;
             OutputStream out = Files.newOutputStream(savePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                int toRead = Math.min(read, contentLength);
                out.write(buffer, 0, read);
                totalBytes += read;
                if (totalBytes > MAX_FILE_SIZE) {
                    // 抛出异常前，流会自动关闭（try-with-resources）
                    throw new IllegalArgumentException("File too large: " + filename);
                }
                if(indexOf(buffer,0,toRead,boundaryBytes)!=-1){
                    System.out.print("buffer " + toRead);
                    break;
                }
                contentLength -= toRead;
                MultipartParser.setReadedSize(contentLength);
                //System.out.println("contentLengthLeft: " + contentLengthLeft + " toRead: " + toRead);
                if(contentLength<=0){
                    break;
                }
            }
        } catch (IOException e) {
            // 发生异常时清理可能残留的文件
            try {
                Files.deleteIfExists(savePath);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        } catch (IllegalArgumentException e) {
             try {
                Files.deleteIfExists(savePath);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        
        String detectedContentType = detectMimeType(savePath, filename);
        return new FilePart(filename, detectedContentType, savePath.toString());
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot);
    }

    private static String detectMimeType(Path path, String filename) {
        try {
            String type = Files.probeContentType(path);
            if (type != null) return type;
        } catch (Exception ignored) {}
        // Fallback based on extension
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".pdf" -> "application/pdf";
            case ".txt" -> "text/plain";
            case ".html" -> "text/html";
            case ".json" -> "application/json";
            default -> "application/octet-stream";
        };
    }

    private static int indexOf(byte[] buf, int off, int limit, byte[] needle) {
        for (int i = off; i <= limit - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (buf[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }
}