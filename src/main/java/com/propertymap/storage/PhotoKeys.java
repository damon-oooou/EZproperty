package com.propertymap.storage;

/**
 * v0.6:storage key 约定的唯一定义处。
 * 主文件(规格化原图)= {uuid}.jpg;变体由约定派生,表里只存主 key。
 * 本地文件系统与 R2 共用同一套 key。
 */
public final class PhotoKeys {

    private PhotoKeys() {}

    /** 中间档(长边 1600):{uuid}.jpg -> {uuid}_m.jpg */
    public static String medium(String key) {
        return withSuffix(key, "_m");
    }

    /** 缩略图(长边 200):{uuid}.jpg -> {uuid}_t.jpg */
    public static String thumbnail(String key) {
        return withSuffix(key, "_t");
    }

    private static String withSuffix(String key, String suffix) {
        int dot = key.lastIndexOf('.');
        return key.substring(0, dot) + suffix + key.substring(dot);
    }
}
