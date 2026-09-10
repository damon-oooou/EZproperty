package com.propertymap.exception;

/** v0.8:资源状态冲突(如"这张照片已被更新过"),由 GlobalExceptionHandler 映射为 409。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
