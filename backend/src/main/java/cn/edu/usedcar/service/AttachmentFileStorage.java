package cn.edu.usedcar.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.model.UploadFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 校验文件内容，按随机键落盘。业务回滚时自动清除本次落盘的文件。
 * @author pangzhihao
 */
@Component
class AttachmentFileStorage {
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentFileStorage.class);
    private final Path root;
    private final int maxBytes;

    AttachmentFileStorage(@Value("${app.uploads}") String uploadPath, MultipartProperties multipart) {
        root = Path.of(uploadPath).toAbsolutePath().normalize();
        long configured = multipart.getMaxFileSize().toBytes();
        maxBytes = configured < 0 ? Integer.MAX_VALUE - 1 : (int) Math.min(configured, Integer.MAX_VALUE - 1L);
    }

    Stored write(UploadFile file, boolean allowPdf) {
        if (file.size() > maxBytes) throw tooLarge();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("附件写入需要绑定业务事务");
        }
        byte[] bytes;
        try {
            bytes = file.stream().readNBytes(maxBytes + 1);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "无法读取上传文件。");
        }
        if (bytes.length > maxBytes) throw tooLarge();
        if (bytes.length == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "上传文件不能为空。");
        String mime = detect(bytes);
        String declared = file.contentType();
        boolean generic = declared == null || declared.isBlank() || declared.equalsIgnoreCase("application/octet-stream");
        if (mime == null || (!allowPdf && mime.equals("application/pdf"))
                || (!generic && !mime.equals(declared.toLowerCase(Locale.ROOT)))
                || !canOpen(bytes, mime)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "上传文件的内容或类型不受支持。");
        }

        String key = UUID.randomUUID().toString().replace("-", "");
        Path destination = root.resolve(key);
        try {
            Files.createDirectories(root);
            // CREATE_NEW 防止覆盖已有文件，文件系统只接收服务端生成的键。
            try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                output.write(bytes);
            }
        } catch (java.nio.file.FileAlreadyExistsException duplicate) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "保存文件失败，请重试。");
        } catch (IOException error) {
            cleanup(destination);
            LOG.warn("Could not store attachment {}", key, error);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "保存文件失败，请重试。");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) cleanup(destination);
            }
        });
        return new Stored(key, safeName(file.name()), mime, bytes.length);
    }

    Path read(String key) {
        if (key == null || !key.matches("[0-9a-f]{32}")) throw missing();
        Path path = root.resolve(key);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw missing();
        return path;
    }

    /** 在检查类型签名后再验证实际内容。照片只解码缩略采样，避免大图占用过多内存。 */
    private static boolean canOpen(byte[] data, String mime) {
        if (mime.equals("application/pdf")) {
            try (var document = Loader.loadPDF(data)) {
                return document.getNumberOfPages() > 0;
            } catch (InvalidPasswordException encrypted) {
                // 加密报告可下载，系统不会要求卖家在网页中输入文件密码。
                return true;
            } catch (IOException | RuntimeException invalid) {
                return false;
            }
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var candidates = ImageIO.getImageReaders(input);
            if (!candidates.hasNext()) return false;
            var reader = candidates.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 16384 || height > 16384
                        || (long) width * height > 80_000_000L) return false;
                int step = (Math.max(width, height) + 1023) / 1024;
                var sampling = reader.getDefaultReadParam();
                sampling.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, sampling) != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException invalid) {
            return false;
        }
    }

    private static String detect(byte[] data) {
        if (data.length >= 4 && unsigned(data[0]) == 0xff && unsigned(data[1]) == 0xd8
                && unsigned(data[2]) == 0xff && unsigned(data[data.length - 2]) == 0xff
                && unsigned(data[data.length - 1]) == 0xd9) return "image/jpeg";
        if (data.length >= 45 && unsigned(data[0]) == 0x89 && matches(data, 1, "PNG\r\n\u001a\n")
                && matches(data, 12, "IHDR") && matches(data, data.length - 8, "IEND")) return "image/png";
        if (data.length >= 20 && matches(data, 0, "RIFF") && matches(data, 8, "WEBP")
                && littleEndian(data, 4) + 8 == data.length && littleEndian(data, 16) > 0
                && littleEndian(data, 16) + (littleEndian(data, 16) & 1) <= data.length - 20
                && (matches(data, 12, "VP8 ") || matches(data, 12, "VP8L") || matches(data, 12, "VP8X"))) {
            return "image/webp";
        }
        if (data.length >= 12 && matches(data, 0, "%PDF-") && hasPdfEnd(data)) return "application/pdf";
        return null;
    }

    private static boolean hasPdfEnd(byte[] data) {
        int end = data.length;
        while (end > 0 && Character.isWhitespace(unsigned(data[end - 1]))) end--;
        return matches(data, end - 5, "%%EOF");
    }

    private static long littleEndian(byte[] data, int start) {
        return (long) unsigned(data[start]) | (long) unsigned(data[start + 1]) << 8
                | (long) unsigned(data[start + 2]) << 16 | (long) unsigned(data[start + 3]) << 24;
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static boolean matches(byte[] data, int start, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (start < 0 || start + expected.length > data.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (data[start + index] != expected[index]) return false;
        }
        return true;
    }

    private static String safeName(String raw) {
        String normalized = raw == null ? "" : raw.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder();
        basename.codePoints().filter(code -> !Character.isISOControl(code)).limit(160).forEach(name::appendCodePoint);
        return name.toString().isBlank() ? "附件" : name.toString().strip();
    }

    private static void cleanup(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            LOG.warn("Could not clean up attachment {}", path.getFileName(), error);
        }
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "上传文件超过大小限制。");
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "附件不存在或不可查看。");
    }

    record Stored(String key, String name, String mime, long bytes) {
    }
}
