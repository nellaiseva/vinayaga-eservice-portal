package com.eservice1.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class SupabaseStorageService implements StorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.bucket}")
    private String bucket;

    @Value("${supabase.service-key}")
    private String serviceKey;

    private final RestTemplate restTemplate = new RestTemplate();

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

        String url =
                supabaseUrl +
                        "/storage/v1/object/" +
                        bucket +
                        "/" +
                        objectPath;

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(serviceKey);
        headers.set("apikey", serviceKey);
        headers.set("x-upsert", "false");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if (file.getContentType() != null) {
            mediaType = MediaType.parseMediaType(file.getContentType());
        }

        headers.setContentType(mediaType);
        HttpEntity<byte[]> entity =
                new HttpEntity<>(
                        file.getBytes(),
                        headers
                );

        try {

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException(
                        "Supabase upload failed: "
                                + response.getStatusCode()
                                + " "
                                + response.getBody()
                );
            }

            return objectPath;

        } catch (Exception ex) {

            throw new IOException(
                    "Unable to upload file to Supabase",
                    ex
            );
        }}

    @Override
    public byte[] download(String objectPath) {

        String url =
                supabaseUrl +
                        "/storage/v1/object/" +
                        bucket +
                        "/" +
                        objectPath;

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(serviceKey);
        headers.set("apikey", serviceKey);
        headers.set("x-upsert", "false");
        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        ResponseEntity<byte[]> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        byte[].class
                );

        if (!response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null) {

            throw new RuntimeException("Unable to download file.");
        }

        return response.getBody();    }

    @Override
    public void delete(String objectPath) {

        String url =
                supabaseUrl +
                        "/storage/v1/object/" +
                        bucket +
                        "/" +
                        objectPath;

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(serviceKey);
        headers.set("apikey", serviceKey);
        headers.set("x-upsert", "false");
        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        try {

            restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    Void.class
            );

        } catch (Exception ex) {

            throw new RuntimeException(
                    "Unable to delete file from Supabase",
                    ex
            );

        }
    }
}