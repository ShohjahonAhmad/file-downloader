package org.jetbrains;

import java.io.IOException;

public interface HttpClient {
    byte[] downloadChunk (String url, long start, long end) throws IOException;
    long getFileSize(String url) throws IOException;
}
