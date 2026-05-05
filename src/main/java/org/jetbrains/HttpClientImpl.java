package org.jetbrains;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.logging.Logger;

public class HttpClientImpl implements HttpClient{

    private static final Logger logger = Logger.getLogger(HttpClientImpl.class.getName());
    private final OkHttpClient client;

    public HttpClientImpl(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public byte[] downloadChunk(String url, long start, long end) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=" + start + "-" + end)
                .build();

        int retries = 3;

        return requestTheChunk(request, retries);
    }

    private byte[] requestTheChunk(Request request, int retries) throws IOException {
        IOException lastException = null;
        for(int attempt = 0; attempt <= retries; attempt++){
            if(attempt > 0) {
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", ie);
                }
            }
            try (Response response = client.newCall(request).execute()){
                if (isValidResponse(response)) {
                    return response.body().bytes();
                }
                lastException = new IOException("Unexpected response code: " + response.code());
                if(attempt > 0)
                    logger.warning("Attempt " + attempt + " failed: " + lastException.getMessage() + ". Retrying...");
            }
        };

        throw new IOException("Failed to download chunk after " + retries + " retries", lastException);
    }

    private boolean isValidResponse(Response response) {
        return response.code() == 206 && response.body() != null;
    }

    @Override
    public long getFileSize(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .build();

        return requestTheFileSize(request);
    }

    private long requestTheFileSize(Request request) throws IOException {
        try(Response response = client.newCall(request).execute()) {
            validateAcceptRangesHeader(response.header("Accept-Ranges"));
            return validateContentLength(response.header("Content-Length"));
        } catch (NumberFormatException  e){
            throw new IOException("Invalid Content-Length header value", e);
        }
    }

    private void validateAcceptRangesHeader(String acceptRanges) throws IOException {
        if (!"bytes".equals(acceptRanges))
            throw new IOException("Server does not support range requests");

        logger.info("Accept-Ranges: " + acceptRanges);
    }

    private long validateContentLength(String contentLength) throws IOException{
        if(contentLength == null)
            throw new IOException("Server did not return Content-Length");

        long length = Long.parseLong(contentLength);
        if (length <= 0)
            throw new IOException("Invalid Content-Length value: " + length);
        logger.info("Content-Length: " + length + " bytes (" + String.format("%.2f", length / (1024.0 * 1024.0)) + " MB)");

        return length;
    }

    @Override
    public byte[] downloadFull(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()){
            if(response.code() != 200) throw new IOException("Failed to download file: HTTP " + response.code());
            if(response.body() == null) throw new IOException("Empty response body");

            return response.body().bytes();
        }
    }
}
