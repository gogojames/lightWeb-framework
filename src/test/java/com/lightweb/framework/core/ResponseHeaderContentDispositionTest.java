package com.lightweb.framework.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Content-Disposition 响应头测试
 */
class ResponseHeaderContentDispositionTest {

    @Test
    void response_should_include_content_disposition_header_in_bytes() {
        Response res = new Response()
                .status(200)
                .header("Content-Disposition", "attachment; filename=\"report.txt\"");

        String responseStr = new String(res.toBytes(), StandardCharsets.UTF_8);
        // 调试输出完整响应头用于诊断
        System.out.println("[DEBUG] Full Response for presence test:\n" + responseStr);

        assertTrue(responseStr.toLowerCase(Locale.ROOT).contains("content-disposition:"));
        assertTrue(res.getHeader("Content-Disposition").isPresent());
        assertEquals("attachment; filename=\"report.txt\"", res.getHeader("Content-Disposition").orElse(""));
    }

    @Test
    void getHeader_should_be_case_insensitive() {
        Response res = new Response()
                .header("content-disposition", "attachment; filename=\"a.txt\"");

        // 两种大小写都应成功获取
        assertEquals("attachment; filename=\"a.txt\"", res.getHeader("Content-Disposition").orElse(""));
        assertEquals("attachment; filename=\"a.txt\"", res.getHeader("content-disposition").orElse(""));

        // 调试输出全部头部
        System.out.println("[DEBUG] Headers map: " + res.getHeaders());
    }

    @Test
    void header_value_should_preserve_special_characters_and_multiple_parameters() {
        String value = "attachment; filename=\"Résumé 2025 (final).pdf\"; filename*=UTF-8''R%C3%A9sum%C3%A9%202025%20(final).pdf";
        Response res = new Response().header("Content-Disposition", value);

        assertEquals(value, res.getHeader("Content-Disposition").orElse(""));

        // 响应字节中包含完整的值
        String responseStr = new String(res.toBytes(), StandardCharsets.UTF_8);
        System.out.println("[DEBUG] Full Response for special char test:\n" + responseStr);
        assertTrue(responseStr.contains(value));
    }

    @Test
    void getHeader_should_return_empty_when_not_present() {
        Response res = new Response();
        assertTrue(res.getHeader("Content-Disposition").isEmpty());
    }
}