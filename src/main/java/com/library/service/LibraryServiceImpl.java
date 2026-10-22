package com.library.service;

import com.library.dao.DocGiaDAO;
import com.library.dao.PhanHoiDAO;
import com.library.dao.PhieuMuonDAO;
import com.library.dao.SachDAO;
import com.library.dao.YeuThichDAO;
import com.library.model.DocGiaDTO;
import com.library.model.LibraryConstants;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Lớp cài đặt Dịch vụ Remote LibraryServiceImpl (Bài 9 - Java RMI).
 * Kế thừa UnicastRemoteObject và thực thi giao diện ILibraryService.
 * Thực thi kiểm tra xác thực và phân quyền bằng token trên mọi lời gọi từ xa.
 */
public class LibraryServiceImpl extends UnicastRemoteObject implements ILibraryService {
    private static final long serialVersionUID = 1L;

    private final SachDAO sachDAO;
    private final PhieuMuonDAO phieuMuonDAO;
    private final DocGiaDAO docGiaDAO;
    private final PhanHoiDAO phanHoiDAO;
    private final YeuThichDAO yeuThichDAO;

    public LibraryServiceImpl() throws RemoteException {
        super();
        this.sachDAO = new SachDAO();
        this.phieuMuonDAO = new PhieuMuonDAO();
        this.docGiaDAO = new DocGiaDAO();
        this.phanHoiDAO = new PhanHoiDAO();
        this.yeuThichDAO = new YeuThichDAO();
    }

    public LibraryServiceImpl(int port) throws RemoteException {
        super(port);
        this.sachDAO = new SachDAO();
        this.phieuMuonDAO = new PhieuMuonDAO();
        this.docGiaDAO = new DocGiaDAO();
        this.phanHoiDAO = new PhanHoiDAO();
        this.yeuThichDAO = new YeuThichDAO();
    }

    // --- 1. KIỂM TRA SẴN SÀNG (PING) ---
    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    // --- 2. XÁC THỰC & PHIÊN LÀM VIỆC ---
    @Override
    public UserSessionDTO login(String email, String matKhau) throws RemoteException, LibraryException {
        if (email == null || email.trim().isEmpty() || matKhau == null || matKhau.trim().isEmpty()) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Email và mật khẩu không được để trống!");
        }
        try {
            DocGiaDTO user = docGiaDAO.authenticate(email, matKhau);
            if (user == null) {
                throw new LibraryException(LibraryConstants.ERR_UNAUTHORIZED, "Email hoặc mật khẩu không chính xác!");
            }
            return ServerSessionManager.createSession(user);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi kết nối CSDL khi đăng nhập: " + e.getMessage(), e);
        }
    }

    @Override
    public void logout(String token) throws RemoteException {
        ServerSessionManager.invalidate(token);
    }

    @Override
    public DocGiaDTO getProfile(String token) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            DocGiaDTO fresh = docGiaDAO.getDocGiaById(caller.getMaDocGia());
            return (fresh != null) ? fresh : caller;
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi lấy thông tin độc giả: " + e.getMessage(), e);
        }
    }

    @Override
    public int dangKyDocGia(DocGiaDTO docGia) throws RemoteException, LibraryException {
        if (docGia == null || docGia.getEmail() == null || docGia.getEmail().trim().isEmpty() ||
            docGia.getHoTen() == null || docGia.getHoTen().trim().isEmpty()) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Thông tin đăng ký không hợp lệ!");
        }
        docGia.setVaiTro(LibraryConstants.ROLE_READER);
        docGia.setTrangThaiTaiKhoan(LibraryConstants.TAI_KHOAN_HOAT_DONG);
        try {
            return docGiaDAO.themDocGia(docGia);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Email '" + docGia.getEmail() + "' đã được đăng ký trong hệ thống!", e);
            }
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi khi đăng ký tài khoản: " + e.getMessage(), e);
        }
    }

    // --- 3. TRA CỨU SÁCH (CÔNG KHAI) ---
    @Override
    public List<SachDTO> getAllBooks() throws RemoteException, LibraryException {
        try {
            return sachDAO.getAllBooks();
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi lấy danh sách sách: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SachDTO> searchBooks(String keyword) throws RemoteException, LibraryException {
        try {
            return sachDAO.searchBooks(keyword);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi tìm kiếm sách: " + e.getMessage(), e);
        }
    }

    @Override
    public SachDTO getBookById(int maSach) throws RemoteException, LibraryException {
        try {
            SachDTO s = sachDAO.getBookById(maSach);
            if (s == null) {
                throw new LibraryException(LibraryConstants.ERR_BOOK_NOT_FOUND, "Không tìm thấy cuốn sách có mã: " + maSach);
            }
            return s;
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi tra cứu sách: " + e.getMessage(), e);
        }
    }

    // --- 4. NGHIỆP VỤ MƯỢN / TRẢ DÀNH CHO ĐỘC GIẢ ---
    @Override
    public int muonSach(String token, int maSach, int soNgayMuon) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        int maDocGia = caller.getMaDocGia();

        // Kiểm tra trạng thái tài khoản mới nhất trong CSDL
        try {
            docGiaDAO.kiemTraVaCapNhatKhoaTaiKhoan(maDocGia);
            DocGiaDTO dg = docGiaDAO.getDocGiaById(maDocGia);
            if (dg != null && dg.isLocked()) {
                if (dg.getLyDoKhoa() != null && dg.getLyDoKhoa().toUpperCase().contains("MẤT SÁCH")) {
                    throw new LibraryException(LibraryConstants.ERR_ACCOUNT_LOCKED_LOST, 
                            "TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH");
                }
                throw new LibraryException(LibraryConstants.ERR_ACCOUNT_LOCKED_OVERDUE, 
                        "TÀI KHOẢN ĐANG BỊ KHOÁ DO CÓ SÁCH QUÁ HẠN > 10 NGÀY");
            }
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi kiểm tra trạng thái độc giả: " + e.getMessage(), e);
        }

        try {
            return phieuMuonDAO.muonSach(maDocGia, maSach, soNgayMuon);
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("ERR_BOOK_RESERVED") || msg.contains("ERR_BOOK_ALREADY_BORROWED") || 
                msg.contains("ERR_BOOK_UNAVAILABLE") || msg.contains("UQ_PhieuMuon_ActiveSach") || 
                msg.contains("2601") || msg.contains("2627") ||
                msg.contains("đã được mượn") || msg.contains("đang được mượn") || msg.contains("giữ chỗ")) {
                throw new LibraryException(LibraryConstants.ERR_BOOK_NOT_AVAILABLE, 
                        "Sách đang được mượn hoặc giữ chỗ bởi độc giả khác!", e);
            }
            if (msg.contains("ERR_ACCOUNT_LOCKED_LOST") || msg.contains("MẤT SÁCH") || msg.contains("mất sách")) {
                throw new LibraryException(LibraryConstants.ERR_ACCOUNT_LOCKED_LOST, 
                        "TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH", e);
            }
            if (msg.contains("ERR_ACCOUNT_LOCKED_OVERDUE") || msg.contains("KHOÁ") || msg.contains("quá hạn")) {
                throw new LibraryException(LibraryConstants.ERR_ACCOUNT_LOCKED_OVERDUE, 
                        "TÀI KHOẢN ĐANG BỊ KHOÁ DO CÓ SÁCH QUÁ HẠN > 10 NGÀY", e);
            }
            if (msg.contains("ERR_LOAN_LIMIT_EXCEEDED") || msg.contains("tối đa") || msg.contains("hạn mức")) {
                throw new LibraryException(LibraryConstants.ERR_LOAN_LIMIT_EXCEEDED, 
                        "Bạn đã mượn tối đa số sách cho phép (3 cuốn)!", e);
            }
            if (msg.contains("ERR_BOOK_NOT_FOUND")) {
                throw new LibraryException(LibraryConstants.ERR_BOOK_NOT_FOUND, 
                        "Không tìm thấy cuốn sách có mã: " + maSach, e);
            }
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Lỗi nghiệp vụ mượn sách: " + msg, e);
        }
    }

    @Override
    public boolean yeuCauTraSach(String token, int maPhieu) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            PhieuMuonDTO pm = phieuMuonDAO.getPhieuMuonById(maPhieu);
            if (pm == null) {
                throw new LibraryException(LibraryConstants.ERR_LOAN_NOT_FOUND, "Phiếu mượn mã #" + maPhieu + " không tồn tại!");
            }
            if (!caller.isLibrarian() && pm.getMaDocGia() != caller.getMaDocGia()) {
                throw new LibraryException(LibraryConstants.ERR_FORBIDDEN, "Bạn không có quyền thao tác trên phiếu mượn của người khác!");
            }
            return phieuMuonDAO.yeuCauTraSach(maPhieu);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi gửi yêu cầu trả sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean baoMatSach(String token, int maPhieu) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            PhieuMuonDTO pm = phieuMuonDAO.getPhieuMuonById(maPhieu);
            if (pm == null) {
                throw new LibraryException(LibraryConstants.ERR_LOAN_NOT_FOUND, "Phiếu mượn mã #" + maPhieu + " không tồn tại!");
            }
            if (!caller.isLibrarian() && pm.getMaDocGia() != caller.getMaDocGia()) {
                throw new LibraryException(LibraryConstants.ERR_FORBIDDEN, "Bạn không có quyền báo mất sách cho phiếu của người khác!");
            }
            return phieuMuonDAO.baoMatSach(maPhieu);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi báo mất sách: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PhieuMuonDTO> getPhieuMuonByDocGia(String token) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            return phieuMuonDAO.getPhieuMuonByDocGia(caller.getMaDocGia());
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi lấy lịch sử mượn: " + e.getMessage(), e);
        }
    }

    // --- 5. QUẢN TRỊ MƯỢN / TRẢ DÀNH CHO THỦ THƯ ---
    @Override
    public boolean xacNhanGiaoSach(String token, int maPhieu, int soNgayMuon) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phieuMuonDAO.xacNhanGiaoSach(maPhieu, soNgayMuon);
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("ERR_HOLD_EXPIRED")) {
                throw new LibraryException(LibraryConstants.ERR_INVALID_STATE, "Phiếu mượn đã quá hạn 24 giờ giữ sách và đã tự động chuyển sang Đã hủy!", e);
            }
            if (msg != null && msg.contains("ERR_LOAN_NOT_FOUND")) {
                throw new LibraryException(LibraryConstants.ERR_LOAN_NOT_FOUND, "Phiếu mượn #" + maPhieu + " không tồn tại!", e);
            }
            if (msg != null && msg.contains("ERR_INVALID_LOAN_STATE")) {
                throw new LibraryException(LibraryConstants.ERR_INVALID_STATE, "Phiếu mượn #" + maPhieu + " không ở trạng thái Chờ lấy sách!", e);
            }
            String cleanMsg = msg != null ? msg.replace("ERR_HOLD_EXPIRED: ", "").replace("ERR_LOAN_NOT_FOUND: ", "").replace("ERR_INVALID_LOAN_STATE: ", "") : "";
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Lỗi xác nhận giao sách: " + cleanMsg, e);
        }
    }

    @Override
    public boolean traSach(String token, int maPhieu, int tienPhuThu, String tinhTrangSach) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phieuMuonDAO.traSach(maPhieu, tienPhuThu, tinhTrangSach);
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null) {
                if (msg.contains("ERR_LOAN_NOT_FOUND")) {
                    throw new LibraryException(LibraryConstants.ERR_LOAN_NOT_FOUND, "Phiếu mượn #" + maPhieu + " không tồn tại!", e);
                }
                if (msg.contains("ERR_ALREADY_RETURNED")) {
                    throw new LibraryException(LibraryConstants.ERR_INVALID_STATE, "Phiếu mượn này đã được hoàn tất trả trước đó!", e);
                }
                if (msg.contains("ERR_HOLD_NOT_PICKED_UP")) {
                    throw new LibraryException(LibraryConstants.ERR_INVALID_STATE, "Phiếu mượn đang ở trạng thái Chờ lấy sách, chưa được giao tại quầy!", e);
                }
            }
            String cleanMsg = msg != null ? msg.replace("ERR_LOAN_NOT_FOUND: ", "").replace("ERR_ALREADY_RETURNED: ", "").replace("ERR_HOLD_NOT_PICKED_UP: ", "") : "";
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Lỗi xác nhận trả sách: " + cleanMsg, e);
        }
    }

    @Override
    public boolean xacNhanDenBu(String token, int maPhieu) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phieuMuonDAO.xacNhanDenBu(maPhieu);
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("ERR_NOT_IN_LOST_STATE")) {
                throw new LibraryException(LibraryConstants.ERR_INVALID_STATE, "Phiếu mượn không ở trạng thái Báo mất / Chờ đền bù!", e);
            }
            String cleanMsg = msg != null ? msg.replace("ERR_NOT_IN_LOST_STATE: ", "").replace("ERR_LOAN_NOT_FOUND: ", "") : "";
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Lỗi xác nhận đền bù: " + cleanMsg, e);
        }
    }

    @Override
    public int huyPhieuChoQuaHan(String token) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phieuMuonDAO.huyPhieuChoQuaHan();
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi dọn dẹp phiếu quá hạn: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PhieuMuonDTO> getAllPhieuMuon(String token) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phieuMuonDAO.getAllPhieuMuon();
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi lấy danh sách phiếu mượn: " + e.getMessage(), e);
        }
    }

    // --- 6. QUẢN LÝ SÁCH DÀNH CHO THỦ THƯ ---
    @Override
    public int themSach(String token, SachDTO sach) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        if (sach == null || sach.getTenSach() == null || sach.getTenSach().trim().isEmpty()) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Tên sách không được để trống!");
        }
        try {
            return sachDAO.themSach(sach);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi thêm sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean capNhatSach(String token, SachDTO sach) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        if (sach == null || sach.getMaSach() <= 0) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Mã sách không hợp lệ!");
        }
        try {
            return sachDAO.capNhatSach(sach);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi cập nhật sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean boSungSachMoi(String token, int maSach, int giaTien) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return sachDAO.boSungSachMoi(maSach, giaTien);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, e.getMessage(), e);
        }
    }

    @Override
    public boolean capNhatTrangThaiSach(String token, int maSach, int trangThai) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return sachDAO.capNhatTrangThai(maSach, trangThai);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, e.getMessage(), e);
        }
    }

    @Override
    public boolean xoaSach(String token, int maSach) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return sachDAO.xoaSach(maSach);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> layThongKe(String token) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return sachDAO.layThongKe();
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi lấy số liệu thống kê: " + e.getMessage(), e);
        }
    }

    // --- 7. QUẢN TRỊ NGƯỜI DÙNG DÀNH CHO THỦ THƯ ---
    @Override
    public List<DocGiaDTO> getAllDocGia(String token) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return docGiaDAO.getAllDocGia();
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi lấy danh sách người dùng: " + e.getMessage(), e);
        }
    }

    @Override
    public int themDocGia(String token, DocGiaDTO docGia) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        if (docGia == null || docGia.getEmail() == null || docGia.getEmail().trim().isEmpty()) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Email không được để trống!");
        }
        try {
            return docGiaDAO.themDocGia(docGia);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, "Email '" + docGia.getEmail() + "' đã tồn tại!", e);
            }
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi thêm người dùng: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean moKhoaTaiKhoan(String token, int maDocGia) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return docGiaDAO.moKhoaTaiKhoan(maDocGia);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_BAD_REQUEST, e.getMessage(), e);
        }
    }

    // --- 8. PHẢN HỒI & BÁO CÁO TÌNH TRẠNG SÁCH ---
    @Override
    public boolean guiPhanHoi(String token, int maSach, String tieuDe, String noiDung) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            return phanHoiDAO.guiPhanHoi(caller.getMaDocGia(), maSach, tieuDe, noiDung);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi gửi phản hồi: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PhanHoiDTO> getAllPhanHoi(String token) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phanHoiDAO.getAllPhanHoi();
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi lấy danh sách phản hồi: " + e.getMessage(), e);
        }
    }

    @Override
    public int demPhanHoiChuaXuLy(String token) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phanHoiDAO.demPhanHoiChuaXuLy();
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi đếm phản hồi: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean xuLyPhanHoi(String token, int maPhanHoi, String ghiChu) throws RemoteException, LibraryException {
        ServerSessionManager.requireRole(token, LibraryConstants.ROLE_LIBRARIAN);
        try {
            return phanHoiDAO.capNhatTrangThai(maPhanHoi, 1, ghiChu);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi xử lý phản hồi: " + e.getMessage(), e);
        }
    }

    // --- 9. MỤC SÁCH YÊU THÍCH ---
    @Override
    public boolean toggleYeuThich(String token, int maSach) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            return yeuThichDAO.toggleYeuThich(caller.getMaDocGia(), maSach);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi cập nhật mục yêu thích: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isYeuThich(String token, int maSach) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            return yeuThichDAO.isYeuThich(caller.getMaDocGia(), maSach);
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi kiểm tra mục yêu thích: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SachDTO> getDanhSachYeuThich(String token) throws RemoteException, LibraryException {
        DocGiaDTO caller = ServerSessionManager.validateToken(token);
        try {
            return yeuThichDAO.getDanhSachYeuThich(caller.getMaDocGia());
        } catch (SQLException e) {
            throw new LibraryException(LibraryConstants.ERR_SYSTEM_ERROR, "Lỗi CSDL khi lấy sách yêu thích: " + e.getMessage(), e);
        }
    }
}
