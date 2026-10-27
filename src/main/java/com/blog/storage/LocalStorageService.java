package com.blog.storage;

import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.config.BlogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 本地磁盘存储实现（后续可替换 OSS / MinIO，只需新增一个 {@link StorageService} 实现）。
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final BlogProperties properties;

    private final Path root;

    private final String urlPrefix;

    public LocalStorageService(BlogProperties properties,
                               @Value("${blog.upload.root:./uploads}") String uploadRoot,
                               @Value("${blog.upload.url-prefix:/uploads}") String urlPrefix) {
        this.properties = properties;
        this.root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录：" + this.root, e);
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String folder) throws IOException {
        return store(file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
                file.getSize(), folder);
    }

    @Override
    public StoredFile store(InputStream inputStream, String originalName, String contentType,
                            long size, String folder) throws IOException {
        checkExtension(originalName);
        checkSize(size);
        String ext = extension(originalName);
        // 重命名存储，避免覆盖与路径穿越
        String fileName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        String datePath = LocalDate.now().format(DATE_PATH);
        Path dir = resolveSafe(root.resolve(sanitizeFolder(folder)).resolve(datePath));
        Files.createDirectories(dir);
        Path target = resolveSafe(dir.resolve(fileName));
        try (InputStream in = inputStream) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        StoredFile stored = new StoredFile();
        stored.setFileName(fileName);
        stored.setOriginalName(originalName);
        stored.setPath(root.relativize(target).toString().replace("\\", "/"));
        stored.setUrl(urlPrefix + "/" + stored.getPath());
        stored.setMimeType(contentType);
        stored.setSize(Files.size(target));
        if (isImage(ext)) {
            int[] dimension = readImageSize(target);
            stored.setWidth(dimension[0]);
            stored.setHeight(dimension[1]);
        }
        return stored;
    }

    @Override
    public void delete(String urlOrPath) {
        if (!StringUtils.hasText(urlOrPath)) {
            return;
        }
        String relative = urlOrPath;
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            int idx = relative.indexOf(urlPrefix);
            if (idx < 0) {
                return;
            }
            relative = relative.substring(idx + urlPrefix.length());
        } else if (relative.startsWith(urlPrefix)) {
            relative = relative.substring(urlPrefix.length());
        }
        relative = relative.replace("\\", "/");
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path target;
        try {
            target = resolveSafe(root.resolve(relative));
        } catch (BusinessException e) {
            log.warn("非法删除路径：{}", urlOrPath);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除文件失败：{}", target, e);
        }
    }

    @Override
    public String initChunk(String uploadId, String originalName) {
        Path dir = chunkDir(uploadId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UPLOAD_FAILED, "初始化分片目录失败");
        }
        return uploadId;
    }

    @Override
    public void writeChunk(String uploadId, int index, InputStream inputStream) throws IOException {
        Path dir = chunkDir(uploadId);
        Files.createDirectories(dir);
        Path part = resolveSafe(dir.resolve("part_" + index));
        try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(part)) {
            in.transferTo(out);
        }
    }

    @Override
    public StoredFile mergeChunks(String uploadId, String originalName, String contentType,
                                  String folder) throws IOException {
        Path dir = chunkDir(uploadId);
        if (!Files.isDirectory(dir)) {
            throw new BusinessException(ErrorCode.UPLOAD_FAILED, "分片数据不存在，请重新上传");
        }
        Path merged;
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> parts = files
                    .filter(p -> p.getFileName().toString().startsWith("part_"))
                    .sorted(Comparator.comparingInt(p -> Integer.parseInt(
                            p.getFileName().toString().substring("part_".length()))))
                    .toList();
            if (parts.isEmpty()) {
                throw new BusinessException(ErrorCode.UPLOAD_FAILED, "没有可合并的分片");
            }
            merged = Files.createTempFile("merge-", ".tmp");
            try (OutputStream out = Files.newOutputStream(merged)) {
                for (Path part : parts) {
                    Files.copy(part, out);
                }
            }
        }
        StoredFile stored;
        try (InputStream in = Files.newInputStream(merged)) {
            stored = store(in, originalName, contentType, Files.size(merged), folder);
        } finally {
            Files.deleteIfExists(merged);
            deleteDirectory(dir);
        }
        return stored;
    }

    @Override
    public List<String> allowedExtensions() {
        return properties.getUpload().getAllowedExtensions();
    }

    // ---------------- private ----------------

    private Path chunkDir(String uploadId) {
        if (!uploadId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "uploadId 不合法");
        }
        return resolveSafe(root.resolve(".chunks").resolve(uploadId));
    }

    private void deleteDirectory(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 忽略删除失败
                }
            });
        } catch (IOException e) {
            log.warn("清理分片目录失败：{}", dir, e);
        }
    }

    /**
     * 校验目录合法性并阻止路径穿越。
     */
    private Path resolveSafe(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法文件路径");
        }
        return normalized;
    }

    private String sanitizeFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            return "";
        }
        String cleaned = folder.replace("\\", "/").trim();
        cleaned = cleaned.replaceAll("/{2,}", "/").replaceAll("^/|/$", "");
        if (cleaned.contains("..") || !cleaned.matches("[A-Za-z0-9_\\-/. ]*")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目录名称不合法");
        }
        return cleaned;
    }

    private void checkExtension(String originalName) {
        String ext = extension(originalName);
        if (ext.isEmpty() || !allowedExtensions().contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "不支持的文件类型：" + (ext.isEmpty() ? "未知" : ext) + "，仅允许 " + allowedExtensions());
        }
    }

    private void checkSize(long size) {
        if (size > properties.getUpload().getMaxSizeMb() * 1024 * 1024L) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小不能超过 " + properties.getUpload().getMaxSizeMb() + "MB");
        }
    }

    private String extension(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return "";
        }
        int index = originalName.lastIndexOf('.');
        if (index < 0 || index == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isImage(String ext) {
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("gif") || ext.equals("webp") || ext.equals("bmp")
                || ext.equals("ico");
    }

    private int[] readImageSize(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (Exception ignored) {
            // 读取失败时返回 0，不影响入库
        }
        return new int[]{0, 0};
    }
}
