package com.propertymap.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "photos")
@Getter @Setter @NoArgsConstructor
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** 用户上传时的原始文件名(仅作展示用) */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * v0.6:storage key,规格化主文件名 {uuid}.jpg。
     * 变体由约定派生:中间档 {uuid}_m.jpg / 缩略图 {uuid}_t.jpg。
     * 本地文件系统与对象存储(阶段 B 的 R2)共用同一套 key。
     */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    /** 规格化原图(全分辨率 q0.85 JPEG)的字节数,原始上传字节不保留 */
    @Column(name = "file_size")
    private Long fileSize;

    /** v0.6:EXIF DateTimeOriginal,缺失为 NULL(展示时回退 uploadedAt) */
    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
