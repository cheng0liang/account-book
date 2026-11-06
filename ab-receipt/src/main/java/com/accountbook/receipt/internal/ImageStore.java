package com.accountbook.receipt.internal;

import com.accountbook.common.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Receipt photos on disk under {@code <dataDir>/images/yyyy/MM/<uuid>.<ext>}; the relative path is the "image token". */
@Component
class ImageStore {

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);
    private static final Pattern TOKEN = Pattern.compile("^\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|heic)$");

    record ImageType(String ext, String mime) {
    }

    private final Path root;

    ImageStore(AppProperties props) {
        this.root = Path.of(props.dataDir(), "images").toAbsolutePath().normalize();
    }

    static Optional<ImageType> detect(byte[] b) {
        if (b == null || b.length < 12) {
            return Optional.empty();
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(new ImageType("jpg", "image/jpeg"));
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Optional.of(new ImageType("png", "image/png"));
        }
        if (ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 12).equals("WEBP")) {
            return Optional.of(new ImageType("webp", "image/webp"));
        }
        if (ascii(b, 4, 8).equals("ftyp")) {
            String brand = ascii(b, 8, 12);
            if (brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1")) {
                return Optional.of(new ImageType("heic", "image/heic"));
            }
        }
        return Optional.empty();
    }

    String save(byte[] bytes, ImageType type) {
        LocalDate d = LocalDate.now();
        String token = "%04d/%02d/%s.%s".formatted(d.getYear(), d.getMonthValue(), UUID.randomUUID(), type.ext());
        Path file = root.resolve(token);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store receipt image", e);
        }
        return token;
    }

    boolean isValidToken(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    Optional<Path> resolve(String token) {
        if (!isValidToken(token)) {
            return Optional.empty();
        }
        Path file = root.resolve(token).normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    static String mimeOf(String token) {
        return switch (token.substring(token.lastIndexOf('.') + 1)) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            default -> "image/jpeg";
        };
    }

    void delete(String token) {
        resolve(token).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("Could not delete image {}: {}", token, e.toString());
            }
        });
    }

    private static String ascii(byte[] b, int from, int to) {
        return new String(b, from, to - from, StandardCharsets.US_ASCII);
    }
}
