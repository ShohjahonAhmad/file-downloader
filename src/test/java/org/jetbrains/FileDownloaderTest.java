package org.jetbrains;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FileDownloaderTest {
    private final String outputPath = "test-output.txt";
    private MockWebServer server;
    private HttpClient httpClient = new HttpClientImpl(new okhttp3.OkHttpClient());

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        httpClient = new HttpClientImpl(new okhttp3.OkHttpClient());
    }

    @AfterEach
    void cleanup() throws IOException {
        server.shutdown();
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(outputPath));
    }

    @Test
    void testGetLength() throws IOException {
        server.enqueue(new MockResponse()
                .addHeader("Content-Length", "42")
                .addHeader("Accept-Ranges", "bytes")
        );

        long length = httpClient.getFileSize(server.url("/test.txt").toString());

        assertEquals(length, 42);
    }

    @Test
    void testGetLengthNoContentLength() throws IOException {
        server.enqueue(new MockResponse()
                .addHeader("Accept-Ranges", "bytes")
        );

        assertThrows(IOException.class, () -> {
            httpClient.getFileSize(server.url("/test.txt").toString());
        });
    }

    @Test
    void testGetLengthNoRangeSupport() throws IOException {
        server.enqueue(new MockResponse()
                .addHeader("Content-Length", "42")
        );

        assertThrows(IOException.class, () -> {
            httpClient.getFileSize(server.url("/test.txt").toString());
        });
    }

    @Test
    void testGetRequest() throws IOException {
        server.enqueue(new MockResponse()
                .setBody("Hello")
                .setResponseCode(206)
        );

        String response = new String(httpClient.downloadChunk(server.url("/test.txt").toString(), 0, 4));

        assertEquals("Hello", response);
    }

    // Integration test
    @Test
    void testFullDownload() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String range = request.getHeader("Range");

                if (range == null) {
                    return new MockResponse() //Response for HEAD request
                            .addHeader("Content-Length", "12")
                            .addHeader("Accept-Ranges", "bytes");
                }

                String content = "Hello World!";
                String[] parts = range.replace("bytes=", "").split("-");
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);

                return new MockResponse() //Response for GET request with Range header
                        .setBody(content.substring(start, end + 1))
                        .setResponseCode(206);
            }
        });


        FileDownloader.main(new String[]{server.url("/test.txt").toString(), outputPath});

        //Checks if the output file created
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(outputPath)));

        String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(outputPath)));

        //Checks if the right content downloaded
        assertEquals("Hello World!", content);
    }

    @Test
    void testDownloadSingleChunk() throws Exception {
        HttpClient mockClient = new HttpClient() {
            @Override
            public byte[] downloadChunk(String url, long start, long end) throws IOException {
                return "Hello".getBytes();
            }

            @Override
            public long getFileSize(String url) throws IOException {
                return 5;
            }

            @Override
            public byte[] downloadFull(String url) throws IOException {
                return new byte[0];
            }
        };

        new FileDownloader(mockClient, 1024, 8).download("http://fake", outputPath);
        String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(outputPath)));
        assertEquals("Hello", content);
    }

    @Test
    void testDownloadMultipleChunks() throws Exception {
        String[] chunks = {"Hel", "lo ", "Wor"};
        HttpClient mockClient = new HttpClient() {
            @Override
            public byte[] downloadChunk(String url, long start, long end) { return chunks[(int) (start / chunks.length)].getBytes();}

            @Override
            public long getFileSize(String url) { return 9;}

            @Override
            public byte[] downloadFull(String url) throws IOException {
                return new byte[0];
            }
        };

        new FileDownloader(mockClient, 3, 8).download("http://fake", outputPath);
        String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(outputPath)));
        assertEquals("Hello Wor", content);
    }

    @Test
    void testChunkFailureThrows() throws IOException {
        HttpClient mockClient = new HttpClient() {
            @Override
            public byte[] downloadChunk(String url, long start, long end) throws IOException {
                throw new IOException("Network error");
            }

            @Override
            public long getFileSize(String url) { return 9;}

            @Override
            public byte[] downloadFull(String url) throws IOException {
                return new byte[0];
            }
        };

        assertThrows(Exception.class, () -> new FileDownloader(mockClient, 3, 8).download("http://fake", outputPath));
    }
}
