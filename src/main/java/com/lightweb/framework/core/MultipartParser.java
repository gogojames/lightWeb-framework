
package com.lightweb.framework.core;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MultipartParser {
    private static final String BOUNDARY_PREFIX = "--";
    private static final String HEADER_SEPARATOR = ": ";
    private static final String CONTENT_DISPOSITION = "content-disposition";
    private static final String CONTENT_TYPE = "content-type";

    /**
     * 解析multipart/form-data请求体
     */
    public static Map<String, Request.Part> parse(InputStream inputStream, String boundary) throws IOException {
        Map<String, Request.Part> parts = new HashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(BOUNDARY_PREFIX + boundary)) {
                    parsePart(reader, parts);
                }
            }
        }
        return Map.copyOf(parts);
    }

    private static void parsePart(BufferedReader reader, Map<String, Request.Part> parts) throws IOException {
        // 解析头部
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int separatorIndex = line.indexOf(HEADER_SEPARATOR);
            if (separatorIndex != -1) {
                String name = line.substring(0, separatorIndex).trim().toLowerCase();
                String value = line.substring(separatorIndex + HEADER_SEPARATOR.length()).trim();
                headers.put(name, value);
            }
        }

        // 解析Content-Disposition
        String disposition = headers.get(CONTENT_DISPOSITION);
        if (disposition == null) return;

        Map<String, String> dispositionParams = parseDisposition(disposition);
        String partName = dispositionParams.get("name");
        String filename = dispositionParams.get("filename");

        // 读取内容
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((line = reader.readLine()) != null && !line.startsWith(BOUNDARY_PREFIX)) {
            buffer.write(line.getBytes(StandardCharsets.UTF_8));
            buffer.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        parts.put(partName, new Request.Part(
            partName,
            filename,
            new ByteArrayInputStream(buffer.toByteArray()),
            headers
        ));
    }

    private static Map<String, String> parseDisposition(String disposition) {
        Map<String, String> params = new HashMap<>();
        String[] parts = disposition.split(";");
        for (String part : parts) {
            part = part.trim();
            int equalIndex = part.indexOf('=');
            if (equalIndex != -1) {
                String key = part.substring(0, equalIndex).trim();
                String value = part.substring(equalIndex + 1).trim().replace("\"", "");
                params.put(key, value);
            }
        }
        return params;
    }
}
