package com.swiftdrop.backend.modules.courier.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

@Component
@Slf4j
public class ProofPhotoStorage {

    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final byte[] PNG_HEADER = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private final MinioClient client;
    private final String bucket;

    public ProofPhotoStorage(@Value("${MINIO_ENDPOINT:}") String endpoint,
                             @Value("${MINIO_ACCESS_KEY:}") String accessKey,
                             @Value("${MINIO_SECRET_KEY:}") String secretKey,
                             @Value("${MINIO_PROOF_BUCKET:delivery-proofs}") String bucket) {
        this.client = endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank()
                ? null : MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    public String upload(UUID parcelId, MultipartFile photo) {
        if (photo.isEmpty() || photo.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a photo up to 5 MB");
        }
        String key = parcelId + "/" + UUID.randomUUID();
        MinioClient storage = requireStorage();
        try (var stream = new BufferedInputStream(photo.getInputStream())) {
            stream.mark(8);
            String contentType = imageType(stream.readNBytes(8));
            stream.reset();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        removeAfterRollback(storage, key);
                    }
                }
            });
            storage.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(stream, photo.getSize(), -1).contentType(contentType).build());
            return key;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Proof photo upload failed", ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proof photo upload failed");
        }
    }

    public InputStream download(String key) {
        MinioClient storage = requireStorage();
        try {
            return storage.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            log.warn("Proof photo download failed", ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proof photo is unavailable");
        }
    }

    private MinioClient requireStorage() {
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proof photo storage is unavailable");
        }
        return client;
    }

    private void removeAfterRollback(MinioClient storage, String key) {
        try {
            storage.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            log.warn("Could not remove photo after proof save failed", ex);
        }
    }

    private String imageType(byte[] header) {
        if (Arrays.equals(header, PNG_HEADER)) {
            return "image/png";
        }
        if (header.length >= 3 && header[0] == (byte) 255 && header[1] == (byte) 216 && header[2] == (byte) 255) {
            return "image/jpeg";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a JPEG or PNG photo");
    }
}
