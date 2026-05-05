# File Downloader

Java program that downloads a file from a URL in parallel using HTTP range requests.

## Requirements

- Java 25
- Maven

## Build
```bash
mvn package
```

## Run

Start a local web server using Docker, mounting the directory that contains the file you want to download:
```bash
docker run --rm -p 8080:80 -v /path/to/your/directory:/usr/local/apache2/htdocs/ httpd:latest
```

The file will be accessible at `http://localhost:8080/<filename>`. For example if your directory contains `test.txt`:
```bash
java -jar target/file-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar http://localhost:8080/test.txt output.txt
```

Alternatively, you can test with any public URL that supports range requests:
```bash
java -jar target/file-downloader-1.0-SNAPSHOT-jar-with-dependencies.jar https://kvongcmehsanalibrary.wordpress.com/wp-content/uploads/2021/07/harrypotter.pdf output.pdf
```

Note:
If the server does not support range requests (e.g. some cloud storage links),
the downloader will automatically fall back to sequential downloading.

## How it works

1. Sends a HEAD request to get file size from `Content-Length` header
2. Verifies server supports range requests via `Accept-Ranges: bytes` header
3. Splits the file into chunks and downloads them in parallel using `ExecutorService`
4. Writes each chunk atomically to the correct byte offset using `FileChannel`,
   which allows lock-free parallel writes
5. Retries failed chunks up to 3 times with exponential backoff (1s, 2s, 3s delays)
6. If the server does not return `Content-Length` or does not support range requests,
   automatically falls back to a single sequential download
7. Logs progress and errors using `java.util.logging` with appropriate levels
   (INFO for progress, WARNING for degraded behavior, SEVERE for fatal errors)

## Tests
```bash
mvn test
```