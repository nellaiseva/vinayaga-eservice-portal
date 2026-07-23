package com.eservice1.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {

    String upload(MultipartFile file, Long requestId) throws IOException;

    byte[] download(String objectPath);

    void delete(String objectPath);
}