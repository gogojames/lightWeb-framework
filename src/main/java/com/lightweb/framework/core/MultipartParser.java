package com.lightweb.framework.core;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PushbackInputStream;
//import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.lightweb.framework.util.LimitedInputStream;
import com.lightweb.framework.util.PartConsumer;
import com.lightweb.framework.util.RepeatableInputStream;

public class MultipartParser {
    private final PushbackInputStream input;
    private final byte[] boundary;
    private final byte[] endBoundary;
    private static int readedSize = 0;

    public MultipartParser(RepeatableInputStream input, byte[] boundary, byte[] endBoundary){
        this.input = new PushbackInputStream(input, 16384); // 16KB buffer
        this.boundary = boundary;
        this.endBoundary = endBoundary;
        readedSize = boundary.length;
    }

    public static void setReadedSize(int size){
        readedSize = size;
    }

    void parse(PartConsumer consumer) throws IOException {
        // 从当前流位置开始查找边界
        skipToBoundary();

        while (true) {
            Map<String, String> headers = readHeaders();
            if (headers.isEmpty()) break; // 结束

            // 读取 body，直到下一个 boundary
            // 为 LimitedInputStream 构造带 CRLF 的边界，因为 body 与 boundary 之间有 CRLF 分隔
            byte[] delimiter = new byte[boundary.length + 2];
            delimiter[0] = '\r';
            delimiter[1] = '\n';
            System.arraycopy(boundary, 0, delimiter, 2, boundary.length);
            
            byte[] endDelimiter = new byte[endBoundary.length + 2];
            endDelimiter[0] = '\r';
            endDelimiter[1] = '\n';
            System.arraycopy(endBoundary, 0, endDelimiter, 2, endBoundary.length);

            LimitedInputStream bodyStream = new LimitedInputStream(input, delimiter, endDelimiter);

            consumer.accept(headers, bodyStream);

            if (bodyStream.isClosedByEnd()) {
                break; // 遇到 --boundary-- 结束
            }
            // 跳至下一个 boundary 的后续，准备读取下一部分头
            // LimitedInputStream 已将流回推至 \r\n--boundary 位置
            // 重新跳过 boundary 以便处理随后的 CRLF 和下一部分头
            skipToBoundary();
        }
    }

    private void skipToBoundary() throws IOException {
        int matched = 0;
        if(readedSize == 0){
            return;
        }
        while (true) {
            int b = input.read();
            if (b == -1) return; // EOF
            if (b == (boundary[matched] & 0xFF)) {
                matched++;
                if (matched == boundary.length) {
                    // boundary 完整匹配，尝试跳过随后的 CRLF
                    int r = input.read();
                    if (r == '\r') {
                        int n = input.read();
                        if (n != '\n') {
                            if (n != -1) input.unread(n);
                        }
                    } else if (r == '\n') {
                        // 跳过单 LF
                    } else if (r != -1) {
                        input.unread(r);
                    }
                    return;
                }
            } else {
                // 回退匹配
                if (matched > 0) {
                     // 简单重置
                }
                matched = (b == (boundary[0] & 0xFF)) ? 1 : 0;
            }
        }
    }

    private Map<String, String> readHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        StringBuilder line = new StringBuilder();
        // firstHeaderByte logic is removed as we use unread() now

        while (true) {
            int b = input.read();
            if (b == -1) {
                return headers;
            }

            if (b == '\r') {
                int next = input.read();
                if (next == '\n') {
                    String headerLine = line.toString();
                    System.out.println("116 Header Line: " + headerLine);
                    if (headerLine.isEmpty()) {
                        return headers; // 空行表示头结束
                    }
                    int colon = headerLine.indexOf(':');
                    if (colon > 0) {
                        String name = headerLine.substring(0, colon).trim().toLowerCase();
                        String value = headerLine.substring(colon + 1).trim();
                        if (headers.containsKey(name)) {
                            String existing = headers.get(name);
                            headers.put(name, existing + ", " + value);
                        } else {
                            headers.put(name, value);
                        }
                    }
                    line.setLength(0);
                } else {
                    // 不规范，仅 CR 结尾，视为换行
                    String headerLine = line.toString();
                    System.out.println("135 Header Line: " + headerLine);
                    if (headerLine.isEmpty()) return headers;
                    int colon = headerLine.indexOf(':');
                    if (colon > 0) {
                        String name = headerLine.substring(0, colon).trim().toLowerCase();
                        String value = headerLine.substring(colon + 1).trim();
                        headers.put(name, value);
                    }
                    line.setLength(0);
                    if (next != -1) {
                        line.append((char) next);
                    }
                }
            } else if (b == '\n') {
                String headerLine = line.toString();
                System.out.println("150 Header Line: " + headerLine);
                if (headerLine.isEmpty()) return headers;
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    String name = headerLine.substring(0, colon).trim().toLowerCase();
                    String value = headerLine.substring(colon + 1).trim();
                    headers.put(name, value);
                }
                line.setLength(0);
            } else {
                line.append((char) b);
            }
        }
    }

    // 移除本地缓冲读取，直接在底层流上进行解析以保持指针一致

        // @Override
        // public void close() throws IOException {
        //     input.close();
        // }
}
