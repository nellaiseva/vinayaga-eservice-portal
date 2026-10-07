package com.eservice1.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path uploadDirectory;

    public LocalStorageService(
            @Value("${file.upload-dir:/app/uploads}") String uploadDir
    ) throws IOException {

        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();

        Files.createDirectories(this.uploadDirectory);
    }

    @Override
    public String upload(MultipartFile file, Long requestId) throws IOException {

        String extension = "";

        String original = file.getOriginalFilename();

        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf("."));
        }

        String objectPath =
                "customer/"
                        + requestId
                        + "/"
                        + UUID.randomUUID()
                        + extension;

        Path target = uploadDirectory
                .resolve(objectPath)
                .normalize();

        if (!target.startsWith(uploadDirectory)) {
            throw new IOException("Invalid file path.");
        }

        Files.createDirectories(target.getParent());

        file.transferTo(target);

        return objectPath;
    }

    @Override
    public byte[] download(String objectPath) {

        try {
            Path target = uploadDirectory
                    .resolve(objectPath)
                    .normalize();

            if (!target.startsWith(uploadDirectory)) {
                throw new RuntimeException("Invalid file path.");
            }

            return Files.readAllBytes(target);

        } catch (IOException ex) {
            throw new RuntimeException("Unable to download file.", ex);
        }
    }

    @Override
    public void delete(String objectPath) {

        try {
            Path target = uploadDirectory
                    .resolve(objectPath)
                    .normalize();

            if (!target.startsWith(uploadDirectory)) {
                throw new RuntimeException("Invalid file path.");
            }

            Files.deleteIfExists(target);

        } catch (IOException ex) {
            throw new RuntimeException("Unable to delete file.", ex);
        }
    }
}
