package org.jetbrains;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class HttpClientImpl implements HttpClient{

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

        try(Response response = client.newCall(request).execute()){
            if(response.code() != 206) throw new IOException("Server did not return partial content for range " + start + "-" + end);
            if(response.body() == null) throw new IOException("Empty response body");
            return response.body().bytes();
        }
    }

    @Override
    public long getFileSize(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .build();

        long length;
        String acceptRanges;

        try(Response response = client.newCall(request).execute()) {
            String contentLength = response.header("Content-Length");
            if (contentLength == null || Long.parseLong(contentLength) == 0) throw new IOException("Server did not return Content-Length");
            length = Long.parseLong(contentLength);
            System.out.println("Content-Length: " + length);
            acceptRanges = response.header("Accept-Ranges");
            if (!"bytes".equals(acceptRanges)) throw new IOException("Server does not support range requests");
            System.out.println("Accept-Ranges: " + acceptRanges);
        } catch (NumberFormatException  e){
            throw new IOException("Invalid Content-Length header value", e);
        }

        return length;
    }
}
