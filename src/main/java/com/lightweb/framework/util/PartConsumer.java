package com.lightweb.framework.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@FunctionalInterface
public interface PartConsumer {
    void accept(Map<String, String> headers, InputStream body) throws IOException;
}
