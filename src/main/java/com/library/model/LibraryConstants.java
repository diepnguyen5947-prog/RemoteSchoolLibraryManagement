package com.library.model;

/**
 * Định nghĩa tập trung các hằng số trạng thái, phân quyền và mã lỗi cho toàn hệ thống.
 * Đồ án: Remote School Library Management - Học phần CMP180 (Lập trình mạng máy tính).
 */
public final class LibraryConstants {

    private LibraryConstants() {}

    // --- 1. TRẠNG THÁI SÁCH (Bảng Sach.TrangThai) ---
    public static final int SACH_SAN_SANG = 0;              // Sẵn sàng cho mượn / giữ chỗ
    public static final int SACH_DANG_MUON = 1;             // Đang được mượn thực tế
    public static final int SACH_GIU_CHO = 2;               // Đang giữ chỗ (chờ nhận tại quầy trong 24h)
    public static final int SACH_NGUNG_PHUC_VU = -1;        // Ngừng phục vụ / Đã mất

    // --- 2. TRẠNG THÁI PHIẾU MƯỢN (Bảng PhieuMuon.TrangThaiPhieu) ---
    public static final int PHIEU_CHO_LAY = 0;              // Chờ lấy sách (Đăng ký giữ chỗ từ xa 24h)
    public static final int PHIEU_DANG_MUON = 1;            // Đang mượn thực tế
    public static final int PHIEU_DA_TRA = 2;               // Đã trả sách / Đã hoàn tất đền bù (kết thúc)
    public static final int PHIEU_DA_HUY = 3;               // Đã hủy (do quá hạn 24h không nhận hoặc hủy)
    public static final int PHIEU_CHO_DEN_BU = 4;           // Báo mất sách / Chờ nộp phạt đền bù tại quầy
    public static final int PHIEU_CHO_XAC_NHAN_TRA = 6;     // Độc giả gửi yêu cầu trả, chờ thủ thư duyệt

    // Tập các trạng thái phiếu mượn đang chiếm dụng bản sách (chưa kết thúc)
    public static final int[] TRANG_THAI_ACTIVE_LOANS = { PHIEU_CHO_LAY, PHIEU_DANG_MUON, PHIEU_CHO_DEN_BU, PHIEU_CHO_XAC_NHAN_TRA };

    // --- 3. VAI TRÒ NGƯỜI DÙNG (Bảng DocGia.VaiTro) ---
    public static final String ROLE_READER = "READER";         // Độc giả (học sinh / sinh viên / giáo viên)
    public static final String ROLE_LIBRARIAN = "LIBRARIAN";   // Thủ thư quản trị

    // --- 4. TRẠNG THÁI TÀI KHOẢN (Bảng DocGia.TrangThaiTaiKhoan) ---
    public static final int TAI_KHOAN_HOAT_DONG = 1;
    public static final int TAI_KHOAN_BI_KHOA = 0;

    // --- 5. MÃ LỖI NGHIỆP VỤ CHUẨN HÓA (LibraryException Error Codes) ---
    public static final String ERR_UNAUTHORIZED = "UNAUTHORIZED";                       // Chưa đăng nhập hoặc token không hợp lệ (HTTP 401)
    public static final String ERR_FORBIDDEN = "FORBIDDEN";                             // Không có quyền thực hiện thao tác (HTTP 403)
    public static final String ERR_NOT_FOUND = "NOT_FOUND";                             // Không tìm thấy dữ liệu (HTTP 404)
    public static final String ERR_BOOK_NOT_FOUND = "BOOK_NOT_FOUND";                   // Cuốn sách không tồn tại (HTTP 404)
    public static final String ERR_READER_NOT_FOUND = "READER_NOT_FOUND";               // Độc giả không tồn tại (HTTP 404)
    public static final String ERR_LOAN_NOT_FOUND = "LOAN_NOT_FOUND";                   // Phiếu mượn không tồn tại (HTTP 404)
    public static final String ERR_BOOK_NOT_AVAILABLE = "BOOK_NOT_AVAILABLE";           // Sách không sẵn có / tranh chấp mượn trùng (HTTP 409)
    public static final String ERR_BOOK_RESERVED = "BOOK_RESERVED";                     // Sách đang được người khác giữ chỗ (HTTP 409)
    public static final String ERR_BOOK_ALREADY_BORROWED = "BOOK_ALREADY_BORROWED";     // Sách đang được người khác mượn (HTTP 409)
    public static final String ERR_ACCOUNT_LOCKED_OVERDUE = "ACCOUNT_LOCKED_OVERDUE";   // Tài khoản bị khóa do quá hạn > 10 ngày (HTTP 403)
    public static final String ERR_ACCOUNT_LOCKED_LOST = "ACCOUNT_LOCKED_LOST";         // Tài khoản bị khóa do mất sách (HTTP 403)
    public static final String ERR_LOAN_LIMIT_EXCEEDED = "LOAN_LIMIT_EXCEEDED";         // Vượt quá hạn mức 3 cuốn (HTTP 400)
    public static final String ERR_INVALID_STATE = "INVALID_STATE";                     // Trạng thái phiếu hoặc sách không hợp lệ cho thao tác này (HTTP 400)
    public static final String ERR_BAD_REQUEST = "BAD_REQUEST";                         // Dữ liệu đầu vào không hợp lệ (HTTP 400)
    public static final String ERR_SYSTEM_ERROR = "SYSTEM_ERROR";                       // Lỗi CSDL hoặc hệ thống nội bộ (HTTP 500)
}
