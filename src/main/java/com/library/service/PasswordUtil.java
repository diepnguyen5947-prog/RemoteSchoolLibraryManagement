package com.library.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Tiện ích mã hóa mật khẩu an toàn (SHA-256 kèm Salt ngẫu nhiên).
 * Hỗ trợ tự động nhận diện và nâng cấp dữ liệu mật khẩu cũ (legacy plain text)
 * mà không làm gián đoạn các tài khoản hiện có trong hệ thống thư viện.
 */
public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Tạo chuỗi băm mật khẩu mới kèm Salt: định dạng "saltHex$hashHex".
     * @param plainPassword Mật khẩu gốc dạng văn bản
     * @return Chuỗi mã hóa đã băm cùng salt
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null) {
            plainPassword = "";
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltHex = bytesToHex(salt);
        String hashHex = computeHash(saltHex, plainPassword);
        return saltHex + "$" + hashHex;
    }

    /**
     * Xác thực mật khẩu nhập vào so với chuỗi lưu trong CSDL.
     * Hỗ trợ cả định dạng salt$hash mới và plain text cũ của seed data.
     *
     * @param plainPassword Mật khẩu người dùng nhập vào
     * @param stored Mật khẩu hoặc mã băm đang lưu trong CSDL
     * @return true nếu mật khẩu khớp, false nếu sai
     */
    public static boolean verifyPassword(String plainPassword, String stored) {
        if (plainPassword == null || stored == null) {
            return false;
        }
        plainPassword = plainPassword.trim();
        stored = stored.trim();

        // Kiểm tra xem stored có phải định dạng "salt$hash" (salt 32 hex chars, hash 64 hex chars)
        int sepIdx = stored.indexOf('$');
        if (sepIdx > 0 && sepIdx == 32 && stored.length() == 97) {
            String saltHex = stored.substring(0, sepIdx);
            String expectedHash = stored.substring(sepIdx + 1);
            String computedHash = computeHash(saltHex, plainPassword);
            return slowEquals(expectedHash, computedHash);
        }

        // Định dạng cũ (plain text seed)
        return stored.equals(plainPassword);
    }

    /**
     * Kiểm tra xem mật khẩu hiện tại trong DB có phải là mật khẩu cũ (chưa băm) hay không.
     * @param stored Chuỗi mật khẩu trong DB
     * @return true nếu là plain text cần được tự động nâng cấp sau khi đăng nhập thành công
     */
    public static boolean isLegacyPassword(String stored) {
        if (stored == null) return false;
        int sepIdx = stored.indexOf('$');
        return !(sepIdx == 32 && stored.length() == 97);
    }

    private static String computeHash(String saltHex, String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(saltHex.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in current JVM", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * So sánh an toàn chống tấn công Timing Attack.
     */
    private static boolean slowEquals(String a, String b) {
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        for (int i = 0; i < a.length() && i < b.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
