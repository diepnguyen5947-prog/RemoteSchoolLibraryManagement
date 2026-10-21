package com.library.service;

import com.library.dao.DocGiaDAO;
import com.library.dao.PhanHoiDAO;
import com.library.dao.PhieuMuonDAO;
import com.library.dao.SachDAO;
import com.library.dao.YeuThichDAO;
import com.library.model.DocGiaDTO;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Lớp cài đặt Dịch vụ Remote LibraryServiceImpl (Bài 9 - Java RMI).
 * Kế thừa UnicastRemoteObject và thực thi giao diện ILibraryService.
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

    @Override
    public List<SachDTO> getAllBooks() throws RemoteException {
        return sachDAO.getAllBooks();
    }

    @Override
    public List<SachDTO> searchBooks(String keyword) throws RemoteException {
        return sachDAO.searchBooks(keyword);
    }

    @Override
    public SachDTO getBookById(int maSach) throws RemoteException {
        return sachDAO.getBookById(maSach);
    }

    @Override
    public int muonSach(int maDocGia, int maSach, int soNgayMuon) throws RemoteException {
        // Tự động kích hoạt kiểm tra quá hạn > 10 ngày
        docGiaDAO.kiemTraVaCapNhatKhoaTaiKhoan(maDocGia);

        // Kiểm tra trạng thái tài khoản của độc giả trước khi cho mượn
        DocGiaDTO dg = docGiaDAO.getDocGiaById(maDocGia);
        if (dg != null && dg.isLocked()) {
            if (dg.getLyDoKhoa() != null && dg.getLyDoKhoa().toUpperCase().contains("MẤT SÁCH")) {
                throw new RemoteException("Tài khoản của bạn đã bị KHOÁ do mất sách. Vui lòng liên hệ quầy Thư viện để hoàn tất nộp phạt/đền bù và làm thủ tục mở khóa!");
            }
            throw new RemoteException("Tài khoản của bạn đã bị KHOÁ do có sách mượn quá hạn trên 10 ngày. Vui lòng mang sách đến quầy Thư viện để trả sách, thanh toán phí phạt và làm thủ tục mở khóa!");
        }

        try {
            return phieuMuonDAO.muonSach(maDocGia, maSach, soNgayMuon);
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg.contains("MẤT SÁCH") || msg.contains("mất sách")) {
                throw new RemoteException("Tài khoản của bạn đã bị KHOÁ do mất sách. Vui lòng liên hệ quầy Thư viện để hoàn tất nộp phạt/đền bù và làm thủ tục mở khóa!");
            }
            if (msg.contains("KHOÁ") || msg.contains("quá hạn trên 10 ngày")) {
                throw new RemoteException("Tài khoản của bạn đã bị KHOÁ do có sách mượn quá hạn trên 10 ngày. Vui lòng mang sách đến quầy Thư viện để trả sách, thanh toán phí phạt và làm thủ tục mở khóa!");
            }
            // Truyền thông điệp lỗi nghiệp vụ CSDL lên RMI Client
            throw new RemoteException("Lỗi mượn sách: " + msg, e);
        }
    }

    @Override
    public boolean xacNhanGiaoSach(int maPhieu) throws RemoteException {
        return xacNhanGiaoSach(maPhieu, 0);
    }

    @Override
    public boolean xacNhanGiaoSach(int maPhieu, int soNgayMuon) throws RemoteException {
        try {
            return phieuMuonDAO.xacNhanGiaoSach(maPhieu, soNgayMuon);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi giao sách: " + e.getMessage(), e);
        }
    }

    @Override
    public int huyPhieuChoQuaHan() throws RemoteException {
        try {
            return phieuMuonDAO.huyPhieuChoQuaHan();
        } catch (SQLException e) {
            throw new RemoteException("Lỗi dọn dẹp phiếu quá hạn: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean yeuCauTraSach(int maPhieu) throws RemoteException {
        try {
            return phieuMuonDAO.yeuCauTraSach(maPhieu);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi yêu cầu trả sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean traSach(int maPhieu) throws RemoteException {
        return traSach(maPhieu, 0, "Bình thường (Nguyên vẹn)");
    }

    @Override
    public boolean traSach(int maPhieu, int tienPhuThu) throws RemoteException {
        return traSach(maPhieu, tienPhuThu, "Bình thường (Nguyên vẹn)");
    }

    @Override
    public boolean traSach(int maPhieu, int tienPhuThu, String tinhTrangSach) throws RemoteException {
        try {
            return phieuMuonDAO.traSach(maPhieu, tienPhuThu, tinhTrangSach);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi trả sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean baoMatSach(int maPhieu) throws RemoteException {
        try {
            return phieuMuonDAO.baoMatSach(maPhieu);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi báo mất sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean xacNhanDenBu(int maPhieu) throws RemoteException {
        try {
            return phieuMuonDAO.xacNhanDenBu(maPhieu);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi xác nhận đền bù: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PhieuMuonDTO> getAllPhieuMuon() throws RemoteException {
        return phieuMuonDAO.getAllPhieuMuon();
    }

    @Override
    public List<PhieuMuonDTO> getPhieuMuonByDocGia(int maDocGia) throws RemoteException {
        return phieuMuonDAO.getPhieuMuonByDocGia(maDocGia);
    }

    @Override
    public DocGiaDTO login(String email, String matKhau) throws RemoteException {
        // Tự động quét kiểm tra và khóa tài khoản quá hạn > 10 ngày trước khi đăng nhập
        docGiaDAO.kiemTraVaCapNhatKhoaTaiKhoan(null);
        return docGiaDAO.authenticate(email, matKhau);
    }

    @Override
    public List<DocGiaDTO> getAllDocGia() throws RemoteException {
        return docGiaDAO.getAllDocGia();
    }

    @Override
    public int themDocGia(DocGiaDTO docGia) throws RemoteException {
        try {
            return docGiaDAO.themDocGia(docGia);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi thêm người dùng: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean moKhoaTaiKhoan(int maDocGia) throws RemoteException {
        try {
            return docGiaDAO.moKhoaTaiKhoan(maDocGia);
        } catch (SQLException e) {
            throw new RemoteException(e.getMessage(), e);
        }
    }

    @Override
    public DocGiaDTO kiemTraTrangThaiDocGia(int maDocGia) throws RemoteException {
        docGiaDAO.kiemTraVaCapNhatKhoaTaiKhoan(maDocGia);
        return docGiaDAO.getDocGiaById(maDocGia);
    }

    @Override
    public int themSach(SachDTO sach) throws RemoteException {
        try {
            return sachDAO.themSach(sach);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi thêm sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean capNhatSach(SachDTO sach) throws RemoteException {
        try {
            return sachDAO.capNhatSach(sach);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi cập nhật sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean boSungSachMoi(int maSach, int giaTien) throws RemoteException {
        try {
            return sachDAO.boSungSachMoi(maSach, giaTien);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi CSDL khi bổ sung sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean capNhatTrangThaiSach(int maSach, int trangThai) throws RemoteException {
        try {
            return sachDAO.capNhatTrangThai(maSach, trangThai);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi CSDL khi cập nhật trạng thái sách: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean xoaSach(int maSach) throws RemoteException {
        try {
            return sachDAO.xoaSach(maSach);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi xóa sách: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> layThongKe() throws RemoteException {
        return sachDAO.layThongKe();
    }

    // --- CÁC PHƯƠNG THỨC PHẢN HỒI & BÁO CÁO TÌNH TRẠNG SÁCH ---
    @Override
    public boolean guiPhanHoi(int maDocGia, int maSach, String tieuDe, String noiDung) throws RemoteException {
        return phanHoiDAO.guiPhanHoi(maDocGia, maSach, tieuDe, noiDung);
    }

    @Override
    public List<PhanHoiDTO> getAllPhanHoi() throws RemoteException {
        return phanHoiDAO.getAllPhanHoi();
    }

    @Override
    public int demPhanHoiChuaXuLy() throws RemoteException {
        return phanHoiDAO.demPhanHoiChuaXuLy();
    }

    @Override
    public boolean xuLyPhanHoi(int maPhanHoi, String ghiChu) throws RemoteException {
        return phanHoiDAO.capNhatTrangThai(maPhanHoi, 1, ghiChu);
    }

    // --- CÁC PHƯƠNG THỨC MỤC SÁCH YÊU THÍCH CỦA ĐỘC GIẢ ---
    @Override
    public boolean toggleYeuThich(int maDocGia, int maSach) throws RemoteException {
        return yeuThichDAO.toggleYeuThich(maDocGia, maSach);
    }

    @Override
    public boolean isYeuThich(int maDocGia, int maSach) throws RemoteException {
        return yeuThichDAO.isYeuThich(maDocGia, maSach);
    }

    @Override
    public List<SachDTO> getDanhSachYeuThich(int maDocGia) throws RemoteException {
        return yeuThichDAO.getDanhSachYeuThich(maDocGia);
    }
}
