package com.library.service;

import com.library.model.DocGiaDTO;
import com.library.model.LibraryConstants;
import com.library.model.UserSessionDTO;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý phiên làm việc tập trung tại RMI Server (Server-side Session Manager).
 * Đảm bảo thread-safe, xác thực token và kiểm tra phân quyền độc lập tại tầng RMI,
 * ngăn chặn việc gọi trực tiếp Remote Methods nhằm bỏ qua cơ chế kiểm soát tại Web Gateway.
 */
public class ServerSessionManager {

    private static class SessionEntry {
        final DocGiaDTO user;
        final long createdAt;
        volatile long lastAccessTime;

        SessionEntry(DocGiaDTO user) {
            this.user = user;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessTime = this.createdAt;
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            return (System.currentTimeMillis() - lastAccessTime) > ttlMillis;
        }
    }

    // Thời gian hết hạn phiên: mặc định 24 giờ (hoặc cấu hình qua session.ttl.ms)
    private static final long DEFAULT_TTL_MILLIS = Long.getLong("session.ttl.ms", 24L * 3600 * 1000);

    private static final Map<String, SessionEntry> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    /**
     * Tạo một phiên làm việc mới cho người dùng đã xác thực.
     * Lưu bản sao an toàn của DocGiaDTO (đã xóa mật khẩu) vào session.
     * @param user Thông tin người dùng đã xác thực
     * @return UserSessionDTO chứa token ngẫu nhiên và thông tin người dùng
     */
    public static UserSessionDTO createSession(DocGiaDTO user) {
        if (user == null) {
            return null;
        }
        // Luôn sanitize mật khẩu trước khi lưu vào session và trả về client
        DocGiaDTO safeUser = user.getSafeCopy();
        String token = UUID.randomUUID().toString().replace("-", "");
        ACTIVE_SESSIONS.put(token, new SessionEntry(safeUser));
        return new UserSessionDTO(token, safeUser);
    }

    /**
     * Xác thực token của phiên làm việc.
     * @param token Token cần kiểm tra
     * @return DocGiaDTO nếu hợp lệ
     * @throws LibraryException nếu token rỗng, không tồn tại hoặc đã hết hạn
     */
    public static DocGiaDTO validateToken(String token) throws LibraryException {
        if (token == null || token.trim().isEmpty()) {
            throw new LibraryException(LibraryConstants.ERR_UNAUTHORIZED, 
                    "Yêu cầu xác thực: Token phiên làm việc không được để trống!");
        }
        SessionEntry entry = ACTIVE_SESSIONS.get(token.trim());
        if (entry == null) {
            throw new LibraryException(LibraryConstants.ERR_UNAUTHORIZED, 
                    "Phiên làm việc không tồn tại hoặc đã bị đăng xuất!");
        }
        if (entry.isExpired(DEFAULT_TTL_MILLIS)) {
            ACTIVE_SESSIONS.remove(token.trim());
            throw new LibraryException(LibraryConstants.ERR_UNAUTHORIZED, 
                    "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!");
        }
        entry.touch();
        return entry.user;
    }

    /**
     * Kiểm tra quyền của người dùng theo vai trò yêu cầu.
     * @param token Token phiên
     * @param requiredRole Vai trò bắt buộc (vd: LibraryConstants.ROLE_LIBRARIAN)
     * @return DocGiaDTO nếu hợp lệ và đủ quyền
     * @throws LibraryException nếu không hợp lệ hoặc thiếu quyền
     */
    public static DocGiaDTO requireRole(String token, String requiredRole) throws LibraryException {
        DocGiaDTO user = validateToken(token);
        if (requiredRole != null && !requiredRole.equalsIgnoreCase(user.getVaiTro())) {
            throw new LibraryException(LibraryConstants.ERR_FORBIDDEN, 
                    "Từ chối truy cập: Thao tác này yêu cầu quyền " + requiredRole + 
                    ", tài khoản của bạn đang là " + user.getVaiTro() + "!");
        }
        return user;
    }

    /**
     * Hủy phiên làm việc (Đăng xuất).
     * @param token Token cần hủy
     */
    public static void invalidate(String token) {
        if (token != null) {
            ACTIVE_SESSIONS.remove(token.trim());
        }
    }

    /**
     * Xóa toàn bộ phiên (dùng khi kiểm thử hoặc tắt server).
     */
    public static void clearAll() {
        ACTIVE_SESSIONS.clear();
    }

    /**
     * Đếm số phiên đang hoạt động.
     */
    public static int getActiveSessionCount() {
        return ACTIVE_SESSIONS.size();
    }
}
