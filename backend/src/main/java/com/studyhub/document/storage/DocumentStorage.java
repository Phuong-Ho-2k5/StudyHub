package com.studyhub.document.storage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentStorage {
    private final Path root;
    private final long maxBytes;

    public DocumentStorage(@Value("${studyhub.storage.root:uploads/documents}") String directory,
            @Value("${spring.servlet.multipart.max-file-size:20MB}") String maxSize) throws IOException {
        Path configured = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(configured);
        root = configured.toRealPath();
        maxBytes = DataSize.parse(maxSize).toBytes();
        if (maxBytes <= 0) throw new IllegalArgumentException("Upload limit must be positive");
    }

    public record StoredFile(String key, String fileName) {}

    public StoredFile store(MultipartFile file) {
        String name = safeName(file.getOriginalFilename());
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp không được rỗng.");
        if (file.getSize() > maxBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép.");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf") || !"application/pdf".equalsIgnoreCase(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ hỗ trợ tệp PDF.");
        String key = UUID.randomUUID() + ".pdf";
        Path target = resolve(key);
        boolean complete = false;
        try {
            try (var input = file.getInputStream(); var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép.");
                    output.write(buffer, 0, count);
                }
            }
            // Parse the actual PDF; neither client MIME nor a %PDF prefix is sufficient.
            try (var pdf = Loader.loadPDF(target.toFile())) {
                if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1)
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF phải có trang và không được mã hóa.");
            } catch (IOException invalidPdf) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tệp PDF không hợp lệ hoặc được bảo vệ bằng mật khẩu.");
            }
            complete = true;
            return new StoredFile(key, name);
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot store document", error);
        } finally {
            if (!complete) delete(key);
        }
    }

    public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}\\.pdf")) throw new IllegalArgumentException("Invalid storage key");
        Path resolved = root.resolve(key).normalize();
        if (!resolved.getParent().equals(root)) throw new IllegalArgumentException("Invalid storage key");
        return resolved;
    }

    private String safeName(String original) {
        String name = original == null ? "document.pdf" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tệp quá dài.");
        return name;
    }
}
