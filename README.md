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

## How it works

1. Sends a HEAD request to get file size from `Content-Length` header
2. Verifies server supports range requests via `Accept-Ranges: bytes` header
3. Splits the file into chunks and downloads them in parallel using `ExecutorService`
4. Writes each chunk to the correct byte offset in the output file using `RandomAccessFile`

## Tests
```bash
mvn test
```