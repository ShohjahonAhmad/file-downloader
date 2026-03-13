package org.jetbrains;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FileDownloader {
    final static int chunkSize = 1024 * 1024; // 1MB
    final static int numThreads = 8;
    final static OkHttpClient client = new OkHttpClient();
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java downloadFile <url> <outputFile>");
            return;
        }
        String url = args[0];
        String outputPath = args[1];

        int length = getLength(url);

        int numChunks = (int) Math.ceil((double) length / chunkSize);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        ArrayList<Future<?>> futures = new ArrayList<>();

        try(RandomAccessFile file = new RandomAccessFile(outputPath, "rw")){
            file.setLength(length);
            for(int i = 0; i < numChunks; i++){
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize - 1, length - 1);
                int chunkIndex = i;

                Future<?> future = executor.submit(() -> {
                    try {
                        synchronized(file) {
                            file.seek(start);
                            file.write(getRequest(url, start, end));
                        }
                        System.out.println("Downloaded chunk " + chunkIndex + ": bytes " + start + "-" + end);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }

            for(Future<?> future : futures){
                future.get();
            }

            executor.shutdown();
        }
    }

    public static byte[] getRequest(String url, int start, int end) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=" + start + "-" + end)
                .build();

        try(Response response = client.newCall(request).execute()){
            return response.body().bytes();
        }
    }

    public static int getLength(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .build();

        int length;
        String acceptRanges;

        try(Response response = client.newCall(request).execute()) {
            String contentLength = response.header("Content-Length");
            if (contentLength == null || Integer.parseInt(contentLength) == 0) throw new RuntimeException("Server did not return Content-Length");
            length = Integer.parseInt(contentLength);
            System.out.println("Content-Length: " + length);
            acceptRanges = response.header("Accept-Ranges");
            if (!"bytes".equals(acceptRanges)) throw new RuntimeException("Server does not support range requests");
            System.out.println("Accept-Ranges: " + acceptRanges);
        }

        return length;
    }
}
