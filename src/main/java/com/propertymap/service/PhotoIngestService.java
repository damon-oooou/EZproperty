package com.propertymap.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * v0.6 阶段 A:照片 ingest 规格化管线 —— 所有进入系统的照片的唯一咽喉位置。
 *
 * 严格顺序:
 *   1. 从原始字节读 EXIF(重编码前):DateTimeOriginal -> 存库;Orientation -> 摆正用
 *   2. 解码像素(TwelveMonkeys 增强后的 ImageIO,CMYK/非标准 JPEG 也能读);失败 -> 拒收
 *   3. 按 Orientation(1-8 全部取值)摆正像素,此后不再依赖任何标签
 *   4. 派生三档并编码 JPEG(像素重写,EXIF 天然全部消失,GPS 不落盘):
 *        原图   全分辨率        q0.85   PDF 导出 / 留档 / 下载原图
 *        中间档 长边 1600px    q0.85   lightbox / PDF 嵌入源(小于则不放大)
 *        缩略图 长边 200px     q0.8    房间网格 / 列表
 *
 * 原始上传字节不保留 —— "原图" = 规格化产物(摆正 + 剥 EXIF + q0.85 全分辨率)。
 */
@Service
@Slf4j
public class PhotoIngestService {

    private static final int MEDIUM_LONG_EDGE = 1600;
    private static final int THUMBNAIL_LONG_EDGE = 200;
    private static final float QUALITY_MAIN = 0.85f;
    private static final float QUALITY_THUMBNAIL = 0.8f;

    /** 管线产物:三档 JPEG 字节 + EXIF 拍摄时间(缺失为 null) */
    public record IngestResult(byte[] original, byte[] medium, byte[] thumbnail,
                               LocalDateTime takenAt) {}

    /**
     * 对一个已通过 v0.5.2 magic-bytes 校验的上传文件执行完整管线。
     *
     * @throws IllegalArgumentException 像素解码失败(损坏文件等),调用方映射为 400
     * @throws IOException              编码过程 I/O 失败
     */
    public IngestResult ingest(byte[] rawBytes, String fileName) throws IOException {
        // ---- 1. 重编码前读 EXIF ----
        ExifInfo exif = readExif(rawBytes, fileName);

        // ---- 2. 解码像素 ----
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(rawBytes));
        } catch (IOException e) {
            log.warn("Image decode failed for \"{}\": {}", fileName, e.getMessage());
            decoded = null;
        }
        if (decoded == null) {
            throw new IllegalArgumentException(
                    "\"" + fileName + "\" could not be decoded as an image. "
                    + "The file may be corrupted.");
        }

        // 统一铺到 RGB 白底画布:PNG 透明通道、CMYK 解码产物等都归一,
        // 后续 JPEG 编码不会因色彩模型失败。
        BufferedImage rgb = toRgb(decoded);

        // ---- 3. 按 Orientation 摆正 ----
        BufferedImage upright = applyOrientation(rgb, exif.orientation());

        // ---- 4. 派生三档 ----
        byte[] original = encodeJpeg(upright, QUALITY_MAIN);
        byte[] medium = longEdge(upright) <= MEDIUM_LONG_EDGE
                ? original // 小于 1600 不放大:中间档与原图同像素同质量,直接复用字节
                : encodeJpeg(scaleToLongEdge(upright, MEDIUM_LONG_EDGE), QUALITY_MAIN);
        byte[] thumbnail = encodeJpeg(
                scaleToLongEdge(upright, THUMBNAIL_LONG_EDGE), QUALITY_THUMBNAIL);

        return new IngestResult(original, medium, thumbnail, exif.takenAt());
    }

    // ===== EXIF =====

    private record ExifInfo(LocalDateTime takenAt, int orientation) {}

    /** EXIF 读取失败不阻断上传(PNG/截图本来就没有),回落 takenAt=null / orientation=1。 */
    private ExifInfo readExif(byte[] rawBytes, String fileName) {
        LocalDateTime takenAt = null;
        int orientation = 1;
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(rawBytes));

            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null) {
                Date d = sub.getDateOriginal();
                if (d != null) {
                    takenAt = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
                }
            }

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null && ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int o = ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                if (o >= 1 && o <= 8) orientation = o;
            }
        } catch (Exception e) {
            log.debug("No usable EXIF in \"{}\": {}", fileName, e.getMessage());
        }
        return new ExifInfo(takenAt, orientation);
    }

    // ===== 像素变换 =====

    private BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, Color.WHITE, null);
        g.dispose();
        return rgb;
    }

    /**
     * EXIF Orientation 1-8 全量实现:
     *   1 正常  2 水平翻转  3 旋转180  4 垂直翻转
     *   5 转置(旋90CW+水平翻转)  6 旋90CW  7 反转置(旋270CW+水平翻转)  8 旋270CW
     */
    private BufferedImage applyOrientation(BufferedImage img, int orientation) {
        return switch (orientation) {
            case 2 -> flipHorizontal(img);
            case 3 -> rotate(img, 180);
            case 4 -> flipVertical(img);
            case 5 -> flipHorizontal(rotate(img, 90));
            case 6 -> rotate(img, 90);
            case 7 -> flipHorizontal(rotate(img, 270));
            case 8 -> rotate(img, 270);
            default -> img; // 1 或缺失
        };
    }

    /** 顺时针旋转 90/180/270 度 */
    private BufferedImage rotate(BufferedImage img, int degreesCw) {
        int w = img.getWidth(), h = img.getHeight();
        boolean quarter = degreesCw == 90 || degreesCw == 270;
        BufferedImage out = new BufferedImage(quarter ? h : w, quarter ? w : h,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        AffineTransform t = new AffineTransform();
        switch (degreesCw) {
            case 90 -> { t.translate(h, 0); t.quadrantRotate(1); }
            case 180 -> { t.translate(w, h); t.quadrantRotate(2); }
            case 270 -> { t.translate(0, w); t.quadrantRotate(3); }
            default -> throw new IllegalArgumentException("Unsupported rotation: " + degreesCw);
        }
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    private BufferedImage flipHorizontal(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, w, 0, -w, h, null);
        g.dispose();
        return out;
    }

    private BufferedImage flipVertical(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, h, w, -h, null);
        g.dispose();
        return out;
    }

    // ===== 缩放与编码 =====

    private int longEdge(BufferedImage img) {
        return Math.max(img.getWidth(), img.getHeight());
    }

    private BufferedImage scaleToLongEdge(BufferedImage img, int target) throws IOException {
        if (longEdge(img) <= target) return img; // 不放大
        return Thumbnails.of(img).size(target, target).asBufferedImage();
    }

    /** 从 BufferedImage 编码 JPEG:纯像素重写,产物不含任何 EXIF(含 GPS/Orientation)。 */
    private byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(img)
                .scale(1.0)
                .outputFormat("jpg")
                .outputQuality(quality)
                .toOutputStream(out);
        return out.toByteArray();
    }
}
