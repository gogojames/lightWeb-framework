package com.lightweb.framework.util;

import java.io.*;

public class LimitedInputStream extends InputStream {
    private final PushbackInputStream in;
    private final byte[] normalBoundary;
    private final byte[] endBoundary;
    private final int BUFFER_SIZE = 8192;
    private boolean closedByEnd = false;

    public LimitedInputStream(InputStream delegate, byte[] normalBoundary, byte[] endBoundary) {
        // 使用传入的 PushbackInputStream（若不是则包一层），以便 unread 对上层可见
        if (delegate instanceof PushbackInputStream) {
            this.in = (PushbackInputStream) delegate;
        } else {
            this.in = new PushbackInputStream(delegate, BUFFER_SIZE * 2);
        }
        this.normalBoundary = normalBoundary;
        this.endBoundary = endBoundary;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        return n == -1 ? -1 : b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        // Use a temporary buffer if needed, but here we can read directly into b
        // However, we might read too much.
        // Reading into b directly
        if(isClosedByEnd()){
            return -1;
        }
        int read = in.read(b, off, len);
        if (read == -1) return -1;

        int limit = off + read;
        int normalPos = indexOf(b, off, limit, normalBoundary);
        int endPos = indexOf(b, off, limit, endBoundary);
        int pos = -1;
        
        // Prioritize the boundary that appears first
        if (normalPos != -1 && endPos != -1) {
             pos = Math.min(normalPos, endPos);
        } else if (normalPos != -1) {
             pos = normalPos;
        } else if (endPos != -1) {
             pos = endPos;
        }
        
        // However, if we found endBoundary, we MUST ensure it is not a suffix of normalBoundary (unlikely here)
        // or that we didn't match a prefix of boundary at the end of buffer.
        // But indexOf handles complete matches.

        if (pos != -1) {
            // Found boundary at 'pos' (absolute index in b)
            // The content ends at 'pos'.
            // We need to unread from pos to limit
            int bytesToUnread = limit - pos;
            if (bytesToUnread > 0) {
                in.unread(b, pos, bytesToUnread);
            }
            
            closedByEnd = (endPos != -1 && endPos == pos);
            
            // Return bytes up to pos
            int validBytes = pos - off;
            if (validBytes == 0) return -1;
            return validBytes;
        }
        
        return read;
    }

    private int indexOf(byte[] buf, int off, int limit, byte[] needle) {
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

    public boolean isClosedByEnd() { return closedByEnd; }

    
    @Override public int read(byte[] b) throws IOException { return read(b, 0, b.length); }
    @Override public void close() throws IOException { 
        // Do not close the underlying stream, as it is shared
    }
}
