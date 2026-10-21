package com.library.dao;

import com.library.model.PhieuMuonDTO;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp PhieuMuonDAO xử lý các thao tác quản lý phiếu mượn/trả và gọi Stored Procedures.
 */
public class PhieuMuonDAO {

    /**
     * Thực thi Stored Procedure sp_KiemTraVaMuonSach trên SQL Server để mượn sách an toàn.
     * Stored Procedure sử dụng UPDLOCK để chống Race Condition khi mượn trùng.
     * 
     * @param maDocGia Mã độc giả thực hiện mượn
     * @param maSach Mã cuốn sách cần mượn
     * @param soNgayMuon Thời gian mượn dự kiến (mặc định 14 ngày)
     * @return Mã phiếu mượn mới sinh nếu thành công, hoặc ném ngoại lệ nếu bị từ chối
     * @throws SQLException nếu xảy ra lỗi CSDL hoặc vi phạm nghiệp vụ (sách đã được mượn)
     */
    public int muonSach(int maDocGia, int maSach, int soNgayMuon) throws SQLException {
        String sql = "{call sp_KiemTraVaMuonSach(?, ?, ?, ?)}";

        try (Connection conn = DBConnection.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {

            cs.setInt(1, maDocGia);
            cs.setInt(2, maSach);
            cs.setInt(3, soNgayMuon <= 0 ? 14 : soNgayMuon);
            cs.registerOutParameter(4, Types.INTEGER);

            cs.execute();

            int maPhieu = cs.getInt(4);
            return maPhieu;
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.muonSach] Lỗi nghiệp vụ mượn sách: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Thực thi Stored Procedure sp_TraSach trên SQL Server để trả sách.
     * Cập nhật NgayTraThucTe, đổi trạng thái phiếu mượn sang Đã trả (1), và trả lại trạng thái sách về Sẵn sàng (0).
     * 
     * @param maPhieu Mã phiếu mượn cần hoàn tất trả
     * @return true nếu trả sách thành công
     * @throws SQLException nếu phiếu mượn không tồn tại hoặc đã trả trước đó
     */
    public boolean traSach(int maPhieu) throws SQLException {
        return traSach(maPhieu, 0, "Bình thường (Nguyên vẹn)");
    }

    public boolean traSach(int maPhieu, int tienPhuThu) throws SQLException {
        return traSach(maPhieu, tienPhuThu, "Bình thường (Nguyên vẹn)");
    }

    public boolean traSach(int maPhieu, int tienPhuThu, String tinhTrangSach) throws SQLException {
        String sql = "{call sp_TraSach(?, ?, ?)}";

        try (Connection conn = DBConnection.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {

            cs.setInt(1, maPhieu);
            cs.setInt(2, tienPhuThu);
            cs.setString(3, tinhTrangSach != null ? tinhTrangSach : "Bình thường (Nguyên vẹn)");
            cs.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.traSach] Lỗi nghiệp vụ trả sách: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Độc giả gửi yêu cầu trả sách. Chuyển trạng thái phiếu sang 4 (Chờ thủ thư xác nhận).
     * @param maPhieu Mã phiếu mượn
     * @return true nếu cập nhật thành công
     * @throws SQLException nếu lỗi cơ sở dữ liệu
     */
    public boolean yeuCauTraSach(int maPhieu) throws SQLException {
        String sql = "UPDATE PhieuMuon SET TrangThaiPhieu = 6 WHERE MaPhieu = ? AND TrangThaiPhieu = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maPhieu);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.yeuCauTraSach] Lỗi: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Thực thi Stored Procedure sp_BaoMatVaDenSach:
     * Dừng tính phạt theo ngày, tính tiền đền bù = Phạt + Giá sách, chuyển phiếu sang 2, sách sang -2, khóa tài khoản độc giả.
     * @param maPhieu Mã phiếu mượn báo mất
     * @return true nếu báo mất thành công
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public boolean baoMatSach(int maPhieu) throws SQLException {
        String sql = "{call sp_BaoMatVaDenSach(?)}";
        try (Connection conn = DBConnection.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setInt(1, maPhieu);
            cs.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.baoMatSach] Lỗi nghiệp vụ báo mất sách: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Thực thi Stored Procedure sp_XacNhanThanhToanDenBu:
     * Chuyển phiếu sang 2 (Đã hoàn tất đền bù), mở lại tài khoản độc giả nếu không còn nợ phiếu mất khác.
     * @param maPhieu Mã phiếu mượn cần chốt đền bù
     * @return true nếu xác nhận thành công
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public boolean xacNhanDenBu(int maPhieu) throws SQLException {
        String sql = "{call sp_XacNhanThanhToanDenBu(?)}";
        try (Connection conn = DBConnection.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setInt(1, maPhieu);
            cs.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.xacNhanDenBu] Lỗi nghiệp vụ xác nhận đền bù: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Thủ thư xác nhận giao sách cho độc giả tại quầy (Remote Pickup).
     * Chuyển phiếu mượn sang 1 (Đang mượn thực tế), tính hạn 14 ngày, đổi sách sang 1 (Đang cho mượn).
     * @param maPhieu Mã phiếu mượn
     * @return true nếu giao sách thành công
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public boolean xacNhanGiaoSach(int maPhieu) throws SQLException {
        return xacNhanGiaoSach(maPhieu, 0);
    }

    public boolean xacNhanGiaoSach(int maPhieu, int soNgayMuon) throws SQLException {
        String sql = "{call sp_XacNhanGiaoSach(?, ?)}";
        try (Connection conn = DBConnection.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setInt(1, maPhieu);
            if (soNgayMuon > 0) {
                cs.setInt(2, soNgayMuon);
            } else {
                cs.setNull(2, Types.INTEGER);
            }
            cs.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.xacNhanGiaoSach] Lỗi nghiệp vụ giao sách: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Quét và tự động dọn dẹp các phiếu mượn giữ chỗ quá hạn 24 giờ mà độc giả không đến lấy.
     * Chuyển phiếu sang 3 (Đã hủy), phục hồi sách về 0 (Sẵn sàng).
     * @return Số lượng phiếu đã hủy
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public int huyPhieuChoQuaHan() throws SQLException {
        String sql = "{call sp_HuyPhieuChoQuaHan}";
        try (Connection conn = DBConnection.getConnection();
             CallableStatement cs = conn.prepareCall(sql);
             ResultSet rs = cs.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("SoLuongDaHuy");
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.huyPhieuChoQuaHan] Lỗi quét dọn dẹp phiếu quá hạn: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Lấy toàn bộ danh sách phiếu mượn kèm thông tin độc giả, tên sách, tiền đền bù, giá niêm yết và hạn giữ sách
     * @return Danh sách PhieuMuonDTO
     */
    public List<PhieuMuonDTO> getAllPhieuMuon() {
        List<PhieuMuonDTO> list = new ArrayList<>();
        String sql = "SELECT pm.MaPhieu, pm.MaDocGia, dg.HoTen AS TenDocGia, " +
                     "pm.MaSach, s.TenSach, pm.NgayMuon, pm.NgayHenTra, pm.NgayTraThucTe, pm.TrangThaiPhieu, " +
                     "ISNULL(pm.TienDenBu, 0) AS TienDenBu, ISNULL(s.GiaTien, 50000) AS GiaTienSach, " +
                     "pm.NgayHetHanGiu, ISNULL(pm.TienPhat, 0) AS TienPhat, ISNULL(pm.SoNgayMuon, 14) AS SoNgayMuon, " +
                     "ISNULL(pm.TinhTrangSachKhiTra, '') AS TinhTrangSachKhiTra " +
                     "FROM PhieuMuon pm " +
                     "JOIN DocGia dg ON pm.MaDocGia = dg.MaDocGia " +
                     "JOIN Sach s ON pm.MaSach = s.MaSach " +
                     "ORDER BY pm.MaPhieu DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                PhieuMuonDTO pm = new PhieuMuonDTO(
                        rs.getInt("MaPhieu"),
                        rs.getInt("MaDocGia"),
                        rs.getString("TenDocGia"),
                        rs.getInt("MaSach"),
                        rs.getString("TenSach"),
                        rs.getTimestamp("NgayMuon"),
                        rs.getTimestamp("NgayHenTra"),
                        rs.getTimestamp("NgayTraThucTe"),
                        rs.getInt("TrangThaiPhieu"),
                        rs.getInt("TienDenBu"),
                        rs.getInt("GiaTienSach"),
                        rs.getTimestamp("NgayHetHanGiu"),
                        rs.getLong("TienPhat")
                );
                pm.setSoNgayMuon(rs.getInt("SoNgayMuon"));
                pm.setTinhTrangSachKhiTra(rs.getString("TinhTrangSachKhiTra"));
                list.add(pm);
            }
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.getAllPhieuMuon] Lỗi: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy lịch sử mượn trả theo Mã độc giả kèm thông tin tiền đền bù, giá niêm yết và hạn giữ sách
     * @param maDocGia Mã độc giả
     * @return Danh sách các phiếu mượn của độc giả đó
     */
    public List<PhieuMuonDTO> getPhieuMuonByDocGia(int maDocGia) {
        List<PhieuMuonDTO> list = new ArrayList<>();
        String sql = "SELECT pm.MaPhieu, pm.MaDocGia, dg.HoTen AS TenDocGia, " +
                     "pm.MaSach, s.TenSach, pm.NgayMuon, pm.NgayHenTra, pm.NgayTraThucTe, pm.TrangThaiPhieu, " +
                     "ISNULL(pm.TienDenBu, 0) AS TienDenBu, ISNULL(s.GiaTien, 50000) AS GiaTienSach, " +
                     "pm.NgayHetHanGiu, ISNULL(pm.TienPhat, 0) AS TienPhat, ISNULL(pm.SoNgayMuon, 14) AS SoNgayMuon, " +
                     "ISNULL(pm.TinhTrangSachKhiTra, '') AS TinhTrangSachKhiTra " +
                     "FROM PhieuMuon pm " +
                     "JOIN DocGia dg ON pm.MaDocGia = dg.MaDocGia " +
                     "JOIN Sach s ON pm.MaSach = s.MaSach " +
                     "WHERE pm.MaDocGia = ? " +
                     "ORDER BY pm.MaPhieu DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maDocGia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PhieuMuonDTO pm = new PhieuMuonDTO(
                            rs.getInt("MaPhieu"),
                            rs.getInt("MaDocGia"),
                            rs.getString("TenDocGia"),
                            rs.getInt("MaSach"),
                            rs.getString("TenSach"),
                            rs.getTimestamp("NgayMuon"),
                            rs.getTimestamp("NgayHenTra"),
                            rs.getTimestamp("NgayTraThucTe"),
                            rs.getInt("TrangThaiPhieu"),
                            rs.getInt("TienDenBu"),
                            rs.getInt("GiaTienSach"),
                            rs.getTimestamp("NgayHetHanGiu"),
                            rs.getLong("TienPhat")
                    );
                    pm.setSoNgayMuon(rs.getInt("SoNgayMuon"));
                    pm.setTinhTrangSachKhiTra(rs.getString("TinhTrangSachKhiTra"));
                    list.add(pm);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PhieuMuonDAO.getPhieuMuonByDocGia] Lỗi: " + e.getMessage());
        }
        return list;
    }
}
