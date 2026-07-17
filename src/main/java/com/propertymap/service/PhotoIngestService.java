package com.propertymap.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
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
import java.util.Iterator;
import java.util.concurrent.Semaphore;

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
 *
 * 内存纪律(生产 1GB 容器 / -Xmx700m 实测调优,勿随意破坏):
 *   - 摆正与 RGB 归一合并为一次绘制:全程只存在"解码图 + 目标图"两份全尺寸位图
 *   - 目标图用 TYPE_3BYTE_BGR(3 字节/px),比 TYPE_INT_RGB 省 25% 内存
 *   - 原图编码直接走 ImageWriter,不经过任何中间副本
 *   - INGEST_GATE 信号量:同一时刻仅一张图在做像素级处理,并发上传排队而非叠内存
 *     (一张 4800 万像素照片的解码+目标 ≈ 300MB,两张并发即爆堆)
 */
@Service
@Slf4j
public class PhotoIngestService {

    private static final int MEDIUM_LONG_EDGE = 1600;
    private static final int THUMBNAIL_LONG_EDGE = 200;
    private static final float QUALITY_MAIN = 0.85f;
    private static final float QUALITY_THUMBNAIL = 0.8f;

    /**
     * 像素数上限:6000 万像素。主流手机最高像素模式(48-50MP)全部放行,
     * 全景图(通常 60MP+)与超大图拒收 —— 无上界的输入在有限内存下数学上不闭合,
     * 这道闸不管容器内存多大都必须存在。60MP 峰值 ≈ 360MB,-Xmx700m 下安全。
     */
    private static final long MAX_PIXELS = 60_000_000L;

    /** 全局像素处理闸门:见类注释的内存纪律 */
    private static final Semaphore INGEST_GATE = new Semaphore(1);

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

        // ---- 1.5 像素数闸门(只读文件头拿尺寸,不解码,在排队前就拒掉超大图) ----
        long pixels = readPixelCount(rawBytes);
        if (pixels > MAX_PIXELS) {
            throw new IllegalArgumentException(
                    "\"" + fileName + "\" is " + (pixels / 1_000_000) + " megapixels — photos over "
                    + (MAX_PIXELS / 1_000_000) + "MP (such as panoramas) are not supported. "
                    + "Please upload standard photos.");
        }

        INGEST_GATE.acquireUninterruptibly();
        try {
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

            // ---- 3. 摆正 + 归一到 RGB 白底(单次绘制,PNG 透明/CMYK 一并处理)----
            BufferedImage upright = normalizeOrientation(decoded, exif.orientation());
            decoded.flush();
            decoded = null; // 尽早释放解码位图,给编码阶段腾内存

            // ---- 4. 派生三档 ----
            byte[] original = encodeJpeg(upright, QUALITY_MAIN);
            byte[] medium = longEdge(upright) <= MEDIUM_LONG_EDGE
                    ? original // 小于 1600 不放大:中间档与原图同像素同质量,直接复用字节
                    : encodeJpeg(scaleToLongEdge(upright, MEDIUM_LONG_EDGE), QUALITY_MAIN);
            byte[] thumbnail = encodeJpeg(
                    scaleToLongEdge(upright, THUMBNAIL_LONG_EDGE), QUALITY_THUMBNAIL);
            upright.flush();

            return new IngestResult(original, medium, thumbnail, exif.takenAt());
        } finally {
            INGEST_GATE.release();
        }
    }

    /**
     * 只读文件头获取宽高(不分配像素缓冲,开销可忽略)。
     * 读不出尺寸时返回 0:放行给解码路径,由它给出"无法解码"的 400。
     */
    private long readPixelCount(byte[] rawBytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return 0;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return (long) reader.getWidth(0) * reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return 0;
        }
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

    /**
     * EXIF Orientation 1-8 全量实现,与 RGB 白底归一合并为单次绘制:
     *   1 正常  2 水平翻转  3 旋转180  4 垂直翻转
     *   5 转置  6 旋90CW  7 反转置  8 旋270CW
     * 变换矩阵为业界通用写法(metadata-extractor 官方示例同款)。
     */
    private BufferedImage normalizeOrientation(BufferedImage src, int orientation) {
        int w = src.getWidth(), h = src.getHeight();
        boolean swap = orientation >= 5; // 5-8 都是 90/270 度族,宽高互换
        int outW = swap ? h : w;
        int outH = swap ? w : h;

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { /* 1 或缺失:恒等,仍走一次绘制以完成 RGB 白底归一 */ }
        }

        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, outW, outH); // PNG 透明通道压到白底
        g.drawImage(src, t, null);
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

    /**
     * 从 BufferedImage 直接编码 JPEG(ImageWriter,零中间副本):
     * 纯像素重写,产物不含任何 EXIF(含 GPS/Orientation)。
     */
    private byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
