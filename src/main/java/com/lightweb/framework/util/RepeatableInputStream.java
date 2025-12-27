package com.lightweb.framework.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


public class RepeatableInputStream extends InputStream {
    private final ByteArrayOutputStream buffer;
    private final BufferedInputStream bufferedInput;
    private final InputStream original;
    private final int BUFSIZE=8192;
    private InputStream cached;
    private boolean isClosed = false;
    private int readedSize=0;
    
    public RepeatableInputStream(InputStream original) {
        this.original = original;
        this.buffer = new ByteArrayOutputStream();
        this.bufferedInput = new BufferedInputStream(original, BUFSIZE);
    }
    
    @Override
    public int read() throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        
        if (cached != null) {
            return cached.read();
        }
        
        int data = bufferedInput.read();
        if (data != -1) {
            buffer.write(data);
        }
        readedSize += data;
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
        
        int bytesRead = bufferedInput.read(b, off, len);
        if (bytesRead > 0) {
            buffer.write(b, off, bytesRead);
        }
         readedSize+=bytesRead;
         return bytesRead;
    }
    public String readHeaderString() throws IOException  {
        byte[] buf=new byte[BUFSIZE];
        int splitbyte=0;
        bufferedInput.mark(BUFSIZE);
        int read=-1;
        try{
            read = bufferedInput.read(buf,0,BUFSIZE);
        }catch(IOException e){
            buffer.close();
            bufferedInput.close();
            throw e;
        }
        while (read>0) {
            readedSize+=read;
            splitbyte = findHeaderEnd(buf, readedSize);
            if (splitbyte > 0) {
                break;
            }
            read = bufferedInput.read(buf,readedSize,BUFSIZE-readedSize);
            
        }
        if (splitbyte > 0) {
            bufferedInput.reset();
            bufferedInput.skip(splitbyte);
            return new String(buf, 0, splitbyte, StandardCharsets.UTF_8);
        } else {
            return null;
        
        }
    }

    private int findHeaderEnd(final byte[] buf, int rlen) {
        int splitbyte = 0;
        while (splitbyte + 1 < rlen) {

            // RFC2616
            if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && splitbyte + 3 < rlen && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
                return splitbyte + 4;
            }

            // tolerance
            if (buf[splitbyte] == '\n' && buf[splitbyte + 1] == '\n') {
                return splitbyte + 2;
            }
            splitbyte++;
        }
        return 0;
    }

        public int getReadedSize(){
            return readedSize;
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
        bufferedInput.close();
        if (cached != null) {
            cached.close();
        }
    }
}
