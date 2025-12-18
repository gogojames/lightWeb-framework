package com.lightweb.framework.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RepeatableInputStream extends InputStream {
    private final ByteArrayOutputStream buffer;
    private final InputStream original;
    private InputStream cached;
    private boolean isClosed = false;
    
    public RepeatableInputStream(InputStream original) {
        this.original = original;
        this.buffer = new ByteArrayOutputStream();
    }
    
    @Override
    public int read() throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        
        if (cached != null) {
            return cached.read();
        }
        
        int data = original.read();
        if (data != -1) {
            buffer.write(data);
        }
        return data;
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        
        if (cached != null) {
            return cached.read(b, off, len);
        }
        
        int bytesRead = original.read(b, off, len);
        if (bytesRead > 0) {
            buffer.write(b, off, bytesRead);
        }
        return bytesRead;
    }
    
    /**
     * 重置流，可以重新读取
     */
    public void resetStream() {
        System.out.println(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
        this.cached = new ByteArrayInputStream(buffer.toByteArray());
    }
    
    @Override
    public void close() throws IOException {
        isClosed = true;
        original.close();
        if (cached != null) {
            cached.close();
        }
    }
}
