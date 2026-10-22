package com.library.service;

import java.io.Serializable;

/**
 * Ngoại lệ nghiệp vụ có cấu trúc của Hệ thống Thư viện từ xa (Remote School Library).
 * - Kế thừa java.lang.Exception để làm checked exception truyền qua RMI.
 * - Tuân thủ Java Serialization (Bài 6) với serialVersionUID đầy đủ.
 * - Mang mã lỗi chuẩn (errorCode) để phân biệt chính xác lỗi nghiệp vụ (sách không sẵn,
 *   tài khoản khóa, hết hạn mức, thiếu quyền) với lỗi truyền thông mạng (RemoteException).
 */
public class LibraryException extends Exception implements Serializable {
    private static final long serialVersionUID = 1001L;

    private final String errorCode;

    public LibraryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : "SYSTEM_ERROR";
    }

    public LibraryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode != null ? errorCode : "SYSTEM_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "LibraryException[" + errorCode + "]: " + getMessage();
    }
}
