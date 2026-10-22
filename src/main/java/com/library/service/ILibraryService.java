package com.library.service;

import com.library.model.DocGiaDTO;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote Interface ILibraryService (Bài 9 - Java RMI).
 * Định nghĩa tất cả các phương thức từ xa cho Hệ thống Quản lý Thư viện từ xa.
 * - Mọi phương thức khai báo throws RemoteException theo chuẩn Java RMI.
 * - Các phương thức nghiệp vụ ném checked exception LibraryException với mã lỗi rõ ràng.
 * - Cơ chế bảo mật: Áp dụng xác thực và phân quyền bằng token ngay tại tầng RMI Server.
 */
public interface ILibraryService extends Remote {

    // --- 1. KIỂM TRA SẴN SÀNG (LIGHTWEIGHT PING) ---
    /**
     * Kiểm tra nhanh trạng thái sẵn sàng của kết nối RMI mà không tải nặng CSDL.
     * @return true nếu kết nối RMI bình thường
     */
    boolean ping() throws RemoteException;

    // --- 2. XÁC THỰC VÀ PHIÊN LÀM VIỆC (AUTHENTICATION & SESSIONS) ---
    /**
     * Đăng nhập hệ thống, sinh token phiên làm việc trên RMI Server.
     */
    UserSessionDTO login(String email, String matKhau) throws RemoteException, LibraryException;

    /**
     * Đăng xuất, hủy bỏ token phiên làm việc trên RMI Server.
     */
    void logout(String token) throws RemoteException;

    /**
     * Tra cứu thông tin cá nhân của phiên hiện tại.
     */
    DocGiaDTO getProfile(String token) throws RemoteException, LibraryException;

    /**
     * Độc giả tự đăng ký tài khoản mới từ giao diện đăng nhập (Vai trò mặc định: READER).
     */
    int dangKyDocGia(DocGiaDTO docGia) throws RemoteException, LibraryException;

    // --- 3. TRA CỨU SÁCH (CÔNG KHAI) ---
    List<SachDTO> getAllBooks() throws RemoteException, LibraryException;
    List<SachDTO> searchBooks(String keyword) throws RemoteException, LibraryException;
    SachDTO getBookById(int maSach) throws RemoteException, LibraryException;

    // --- 4. NGHIỆP VỤ MƯỢN / TRẢ DÀNH CHO ĐỘC GIẢ (YÊU CẦU TOKEN) ---
    /**
     * Đăng ký giữ chỗ / mượn sách từ xa.
     * MaDocGia được xác định trực tiếp từ token của phiên đăng nhập (chống giả mạo danh tính).
     */
    int muonSach(String token, int maSach, int soNgayMuon) throws RemoteException, LibraryException;

    /**
     * Độc giả gửi yêu cầu trả sách cho cuốn sách đang mượn.
     */
    boolean yeuCauTraSach(String token, int maPhieu) throws RemoteException, LibraryException;

    /**
     * Độc giả báo mất sách đối với phiếu mượn của mình.
     */
    boolean baoMatSach(String token, int maPhieu) throws RemoteException, LibraryException;

    /**
     * Độc giả chủ động ngưng mượn sách (hủy yêu cầu giữ chỗ khi đang ở trạng thái Chờ lấy sách - TrangThaiPhieu = 0).
     * Trả sách về trạng thái Sẵn sàng (0) và chuyển trạng thái phiếu mượn sang Đã hủy (3).
     * @param token Phiên đăng nhập của độc giả hoặc thủ thư
     * @param maPhieu Mã phiếu mượn cần ngưng mượn
     * @return true nếu thao tác thành công
     * @throws RemoteException nếu xảy ra lỗi truyền thông RMI
     * @throws LibraryException nếu có vi phạm nghiệp vụ
     */
    boolean huyPhieuGiuCho(String token, int maPhieu) throws RemoteException, LibraryException;

    /**
     * Lấy danh sách phiếu mượn của chính độc giả đang đăng nhập.
     */
    List<PhieuMuonDTO> getPhieuMuonByDocGia(String token) throws RemoteException, LibraryException;

    // --- 5. QUẢN TRỊ MƯỢN / TRẢ DÀNH CHO THỦ THƯ (YÊU CẦU TOKEN ROLE LIBRARIAN) ---
    boolean xacNhanGiaoSach(String token, int maPhieu, int soNgayMuon) throws RemoteException, LibraryException;
    boolean traSach(String token, int maPhieu, int tienPhuThu, String tinhTrangSach) throws RemoteException, LibraryException;
    boolean xacNhanDenBu(String token, int maPhieu) throws RemoteException, LibraryException;
    int huyPhieuChoQuaHan(String token) throws RemoteException, LibraryException;
    List<PhieuMuonDTO> getAllPhieuMuon(String token) throws RemoteException, LibraryException;

    // --- 6. QUẢN LÝ SÁCH DÀNH CHO THỦ THƯ (YÊU CẦU TOKEN ROLE LIBRARIAN) ---
    int themSach(String token, SachDTO sach) throws RemoteException, LibraryException;
    boolean capNhatSach(String token, SachDTO sach) throws RemoteException, LibraryException;
    boolean boSungSachMoi(String token, int maSach, int giaTien) throws RemoteException, LibraryException;
    boolean capNhatTrangThaiSach(String token, int maSach, int trangThai) throws RemoteException, LibraryException;
    boolean xoaSach(String token, int maSach) throws RemoteException, LibraryException;
    Map<String, Object> layThongKe(String token) throws RemoteException, LibraryException;

    // --- 7. QUẢN TRỊ NGƯỜI DÙNG DÀNH CHO THỦ THƯ (YÊU CẦU TOKEN ROLE LIBRARIAN) ---
    List<DocGiaDTO> getAllDocGia(String token) throws RemoteException, LibraryException;
    int themDocGia(String token, DocGiaDTO docGia) throws RemoteException, LibraryException;
    boolean moKhoaTaiKhoan(String token, int maDocGia) throws RemoteException, LibraryException;

    // --- 8. PHẢN HỒI & BÁO CÁO TÌNH TRẠNG SÁCH ---
    boolean guiPhanHoi(String token, int maSach, String tieuDe, String noiDung) throws RemoteException, LibraryException;
    List<PhanHoiDTO> getAllPhanHoi(String token) throws RemoteException, LibraryException;
    int demPhanHoiChuaXuLy(String token) throws RemoteException, LibraryException;
    boolean xuLyPhanHoi(String token, int maPhanHoi, String ghiChu) throws RemoteException, LibraryException;

    // --- 9. MỤC SÁCH YÊU THÍCH ---
    boolean toggleYeuThich(String token, int maSach) throws RemoteException, LibraryException;
    boolean isYeuThich(String token, int maSach) throws RemoteException, LibraryException;
    List<SachDTO> getDanhSachYeuThich(String token) throws RemoteException, LibraryException;
}
