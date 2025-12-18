package com.lightweb.framework.util;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;

public class TempFileHandler {
    
    /**
     * 使用Files.createTempFile，系统自动处理重名
     */
    public static Path createSystemTempFile(Path directory, String prefix, String suffix) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            // 系统会自动生成唯一的临时文件名
            Path tempFile = Files.createTempFile(directory, prefix, suffix);
            
            // 设置文件在JVM退出时删除
            tempFile.toFile().deleteOnExit();
            
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("无法创建临时文件", e);
        }
    }
    
}
