package org.jetbrains;

import okhttp3.OkHttpClient;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FileDownloader {
    private final int chunkSize; // 1MB
    private final int numThreads;
    private final HttpClient httpClient;

    public FileDownloader(HttpClient httpClient, int chunkSize, int numThreads) {
        this.chunkSize = chunkSize;
        this.numThreads = numThreads;
        this.httpClient = httpClient;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -jar target/file-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar <file_url> <output_path>");
            return;
        }

        new FileDownloader(new HttpClientImpl(new OkHttpClient()), 1024 * 1024, 8).download(args[0], args[1]);
    }

    public void download(String url, String outputPath) throws Exception {
        long length;
        try {
            length = httpClient.getFileSize(url);
        } catch (IOException exception) {
            System.out.println("Failed to get file size: " + exception.getMessage());
            System.out.println("Fallback to sequential downloading...");

            byte[] data = httpClient.downloadFull(url);
            writeToFile(data, outputPath);

            return;
        }
        long numChunks = (length + chunkSize - 1) / chunkSize;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        try (FileChannel channel = FileChannel.open(Path.of(outputPath),StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            channel.truncate(length);
            for (long i = 0; i < numChunks; i++) {
                long start = i * chunkSize;
                long end = Math.min(start + chunkSize - 1, length - 1);
                long chunkIndex = i;

                Future<?> future = executor.submit(() -> {
                    try {
                        byte[] data = httpClient.downloadChunk(url, start, end);
                        channel.write(ByteBuffer.wrap(data), start);
                        System.out.println("Downloaded chunk " + chunkIndex + ": bytes " + start + "-" + end);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }

            try {
                waitAllFutures(futures);
                System.out.println("Download completed successfully.");
            } finally {
                executor.shutdown();
            }

        }
    }

    private void writeToFile(byte[] data, String outputPath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(outputPath, "rw")) {
            raf.write(data);
        }
    }

    private void waitAllFutures(List<Future<?>> futures) throws ExecutionException, InterruptedException {
        for (Future<?> future : futures) {
            future.get();
        }
    }
}