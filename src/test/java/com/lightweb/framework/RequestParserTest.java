package com.lightweb.framework;

import org.junit.jupiter.api.Test;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.RequestParser;
import com.lightweb.framework.core.MultipartParser;
import com.lightweb.framework.util.RepeatableInputStream;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
public class RequestParserTest {
    @Test
    void parse_should_extract_form_field_into_queryParams() throws Exception {
        String boundary = "----WebKitFormBoundarye24nZTki31LrS9iS";
        String request = "POST /upload HTTP/1.1\r\n" +
                "Host: localhost:8080\r\n" +
                "Connection: keep-alive\r\n" +
                "Content-Length: 3262\r\n" +
                "Content-Type: multipart/form-data; boundary=" + boundary + "\r\n" +
                "\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"username\"\r\n" +
                "\r\n" +
                "值\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"avatar\"; filename=\"m.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "文件内容\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"csrf_token\"\r\n" +
                "\r\n" +
                "1e929e56-72dc-457c-a8c2-cee9f26a26aa\r\n" +
                "--" + boundary + "--\r\n";

        byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
        try (RepeatableInputStream ris = new RepeatableInputStream(new ByteArrayInputStream(bytes))) {
            Request req = RequestParser.parse(ris);

            assertEquals("POST", req.method());
            assertEquals("/upload", req.path());
            assertTrue(req.getContentType().orElse("").startsWith("multipart/form-data"));
            // 当前实现未将文件持久化至 files 映射，故应为空
            assertTrue(req.files().get("avatar").filename().equals("m.txt"));
           
            // 验证将部分内容存入请求体参数
            assertEquals("值", req.getQueryParam("username").orElse(""));
            Path savedFile = Path.of(req.files().get("avatar").savedPath());
            Files.deleteIfExists(savedFile);
            
        }
    }
}
