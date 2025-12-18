package com.lightweb.framework.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record FilePart(
        String filename,
        String contentType,
        String savedPath)
    {
        public void saveTo(Path path) throws IOException {
            Files.copy(Path.of(savedPath), path);
        }
    }
