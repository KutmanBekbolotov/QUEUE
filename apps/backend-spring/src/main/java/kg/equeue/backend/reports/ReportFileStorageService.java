package kg.equeue.backend.reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class ReportFileStorageService {

    private final Path localRoot;

    public ReportFileStorageService(@Value("${app.reports.export.local-dir:./data/exports}") String localDir) {
        this.localRoot = Path.of(localDir).toAbsolutePath().normalize();
    }

    public StoredReportFile allocate(UUID exportId, String fileName) throws IOException {
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path directory = localRoot.resolve(exportId.toString()).normalize();
        Files.createDirectories(directory);
        Path path = directory.resolve(safeFileName).normalize();
        if (!path.startsWith(localRoot)) {
            throw new IOException("Invalid export path");
        }
        return new StoredReportFile("local", localRoot.relativize(path).toString(), path, safeFileName);
    }

    public Resource load(String fileKey) throws IOException {
        Path path = localRoot.resolve(fileKey).normalize();
        if (!path.startsWith(localRoot) || !Files.exists(path)) {
            throw new IOException("Export file was not found");
        }
        return new UrlResource(path.toUri());
    }

    public long size(String fileKey) throws IOException {
        Path path = localRoot.resolve(fileKey).normalize();
        return Files.size(path);
    }

    public record StoredReportFile(String bucket, String key, Path path, String fileName) {
    }
}
