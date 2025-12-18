package com.lightweb.framework.core;

import com.lightweb.framework.util.FilePart;
import com.lightweb.framework.util.RepeatableInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RequestParserTest {

    @Test
    void testParseSimpleGet() throws Exception {
        String raw = "GET /hello?name=world HTTP/1.1\r\nHost: localhost\r\n\r\n";
        RepeatableInputStream in = new RepeatableInputStream(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        
        Request req = RequestParser.parse(in);
        
        assertEquals("GET", req.method());
        assertEquals("/hello", req.path());
        assertEquals("world", req.queryParams().get("name"));
    }

    @Test
    void testParseMultipart() throws Exception {
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        String body = 
            "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"field1\"\r\n" +
            "\r\n" +
            "value1\r\n" +
            "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file1\"; filename=\"test测试.txt\"\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "Hello Content\r\n" +
            "--" + boundary + "--\r\n";
            
        String header = "POST /upload HTTP/1.1\r\n" +
                        "Content-Type: multipart/form-data; boundary=" + boundary + "\r\n" +
                        "Content-Length: " + body.length() + "\r\n" +
                        "\r\n";
        
        String raw = header + body;
        
        RepeatableInputStream in = new RepeatableInputStream(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        
        Request req = RequestParser.parse(in);
        
        assertEquals("value1", req.queryParams().get("field1"));
        assertTrue(req.files().containsKey("file1"));
        
        FilePart part = req.files().get("file1");
        assertEquals("test测试.txt", part.filename());
        // MIME type might be detected as text/plain or fallback
        assertTrue(part.contentType().contains("text/plain") || part.contentType().contains("application/octet-stream"));
        
        Path savedFile = Path.of(part.savedPath());
        assertTrue(Files.exists(savedFile));
        assertEquals("Hello Content", Files.readString(savedFile));
        
        // Cleanup
        Files.deleteIfExists(savedFile);
    }

    public static String autoFixEncoding(String input) {
        // 尝试常见的编码问题修复
        String[] charsetPairs = {
            "ISO-8859-1", "GBK",      // 常见乱码：ISO转GBK
            "ISO-8859-1", "UTF-8",    // ISO转UTF-8
            "GB2312", "UTF-8",        // GB2312转UTF-8
            "GBK", "UTF-8",           // GBK转UTF-8
        };
        
        for (int i = 0; i < charsetPairs.length; i += 2) {
            try {
                String fixed = fixChineseEncoding(input, charsetPairs[i], charsetPairs[i+1]);
                if (isValidChineseString(fixed)) {
                    return fixed;
                }
            } catch (Exception e) {
                // 继续尝试下一个编码对
            }
        }
        return input; // 无法修复，返回原字符串
    }

    public static String fixChineseEncoding(String input, 
                                           String fromCharset, 
                                           String toCharset) {
        try {
            byte[] bytes = input.getBytes(fromCharset);
            return new String(bytes, toCharset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isValidChineseString(String str) {
        // 简单的有效性检查：是否包含可识别中文字符且没有明显的乱码字符
        int chineseCount = 0;
        int garbageCount = 0;
        
        for (char c : str.toCharArray()) {
            if (isChineseCharacter(c)) {
                chineseCount++;
            } else if (c == '�' || c == 'ï' || c == '¿' || c == '½') {
                garbageCount++; // 常见的乱码字符
            }
        }
        
        return chineseCount > 0 && garbageCount == 0;
    }

    private static boolean isChineseCharacter(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    @Test
    void testBlockedExtension() throws Exception {
        String boundary = "boundary";
        String body = 
            "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file1\"; filename=\"virus.exe\"\r\n" +
            "\r\n" +
            "malware\r\n" +
            "--" + boundary + "--\r\n";
            
        String header = "POST /upload HTTP/1.1\r\n" +
                        "Content-Type: multipart/form-data; boundary=" + boundary + "\r\n" +
                        "\r\n";
        
        RepeatableInputStream in = new RepeatableInputStream(new ByteArrayInputStream((header + body).getBytes(StandardCharsets.UTF_8)));
        
        assertThrows(IllegalArgumentException.class, () -> {
            RequestParser.parse(in);
        }, "Should block .exe files");
    }

    @Test
    void testStreamNotClosedAfterParse() throws Exception {
        String raw = "GET / HTTP/1.1\r\n\r\n";
        RepeatableInputStream in = new RepeatableInputStream(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        
        RequestParser.parse(in);
        
        // Try to read again to verify it's not closed
        assertDoesNotThrow(() -> in.read());
    }

    @Test
    void testBodyStreamClosureSafety() throws Exception {
         String body = "some body content";
         String raw = "POST / HTTP/1.1\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;
         RepeatableInputStream in = new RepeatableInputStream(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
         
         Request req = RequestParser.parse(in);
         assertEquals(body, req.body());
         
         // Verify underlying stream is still open
         assertDoesNotThrow(() -> in.read());
    }
}
