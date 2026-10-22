package com.library.dao;

import com.library.model.LibraryConstants;
import com.library.model.SachDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lớp SachDAO xử lý các thao tác dữ liệu liên quan đến bảng Sach (CRUD & Thống kê).
 * Bảo vệ bất biến nghiệp vụ:
 * - Không cho phép xóa sách có lịch sử mượn/trả nhằm bảo toàn tính toàn vẹn dữ liệu.
 * - Không cho phép đưa sách về trạng thái Sẵn sàng nếu vẫn còn phiếu chiếm dụng hợp lệ.
 * - Lan truyền SQLException chính xác lên tầng Service, không nuốt lỗi.
 */
public class SachDAO {

    /**
     * Lấy toàn bộ danh sách sách trong thư viện
     * @return Danh sách các đối tượng SachDTO
     * @throws SQLException nếu xảy ra lỗi kết nối hoặc truy vấn CSDL
     */
    public List<SachDTO> getAllBooks() throws SQLException {
        List<SachDTO> list = new ArrayList<>();
        String sql = "SELECT s.MaSach, s.TenSach, s.TacGia, s.NamXuatBan, s.TrangThai, s.HinhAnh, ISNULL(s.GiaTien, 50000) AS GiaTien, " +
                     "(SELECT TOP 1 pm.NgayHenTra FROM PhieuMuon pm WHERE pm.MaSach = s.MaSach AND pm.TrangThaiPhieu IN (0, 1) ORDER BY pm.MaPhieu DESC) AS NgayHenTra " +
                     "FROM Sach s ORDER BY s.MaSach ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                SachDTO s = new SachDTO(
                        rs.getInt("MaSach"),
                        rs.getString("TenSach"),
                        rs.getString("TacGia"),
                        rs.getInt("NamXuatBan"),
                        rs.getInt("TrangThai"),
                        rs.getString("HinhAnh"),
                        rs.getInt("GiaTien")
                );
                java.sql.Timestamp ts = rs.getTimestamp("NgayHenTra");
                if (ts != null) {
                    s.setNgayHenTra(new java.util.Date(ts.getTime()));
                }
                list.add(s);
            }
        }
        return list;
    }

    /**
     * Tìm kiếm sách theo từ khóa (khớp tên sách hoặc tác giả)
     * @param keyword Từ khóa tìm kiếm
     * @return Danh sách các cuốn sách thỏa mãn điều kiện
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public List<SachDTO> searchBooks(String keyword) throws SQLException {
        List<SachDTO> list = new ArrayList<>();
        String sql = "SELECT s.MaSach, s.TenSach, s.TacGia, s.NamXuatBan, s.TrangThai, s.HinhAnh, ISNULL(s.GiaTien, 50000) AS GiaTien, " +
                     "(SELECT TOP 1 pm.NgayHenTra FROM PhieuMuon pm WHERE pm.MaSach = s.MaSach AND pm.TrangThaiPhieu IN (0, 1) ORDER BY pm.MaPhieu DESC) AS NgayHenTra " +
                     "FROM Sach s WHERE s.TenSach LIKE ? OR s.TacGia LIKE ? ORDER BY s.MaSach ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String searchPattern = "%" + (keyword != null ? keyword.trim() : "") + "%";
            ps.setNString(1, searchPattern);
            ps.setNString(2, searchPattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SachDTO s = new SachDTO(
                            rs.getInt("MaSach"),
                            rs.getString("TenSach"),
                            rs.getString("TacGia"),
                            rs.getInt("NamXuatBan"),
                            rs.getInt("TrangThai"),
                            rs.getString("HinhAnh"),
                            rs.getInt("GiaTien")
                    );
                    java.sql.Timestamp ts = rs.getTimestamp("NgayHenTra");
                    if (ts != null) {
                        s.setNgayHenTra(new java.util.Date(ts.getTime()));
                    }
                    list.add(s);
                }
            }
        }
        return list;
    }

    /**
     * Lấy thông tin chi tiết một cuốn sách theo Mã sách
     * @param maSach Mã sách cần tra cứu
     * @return Đối tượng SachDTO hoặc null nếu không tìm thấy
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public SachDTO getBookById(int maSach) throws SQLException {
        String sql = "SELECT s.MaSach, s.TenSach, s.TacGia, s.NamXuatBan, s.TrangThai, s.HinhAnh, ISNULL(s.GiaTien, 50000) AS GiaTien, " +
                     "(SELECT TOP 1 pm.NgayHenTra FROM PhieuMuon pm WHERE pm.MaSach = s.MaSach AND pm.TrangThaiPhieu IN (0, 1) ORDER BY pm.MaPhieu DESC) AS NgayHenTra " +
                     "FROM Sach s WHERE s.MaSach = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maSach);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SachDTO s = new SachDTO(
                            rs.getInt("MaSach"),
                            rs.getString("TenSach"),
                            rs.getString("TacGia"),
                            rs.getInt("NamXuatBan"),
                            rs.getInt("TrangThai"),
                            rs.getString("HinhAnh"),
                            rs.getInt("GiaTien")
                    );
                    java.sql.Timestamp ts = rs.getTimestamp("NgayHenTra");
                    if (ts != null) {
                        s.setNgayHenTra(new java.util.Date(ts.getTime()));
                    }
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Thêm mới một cuốn sách vào kho thư viện (Dành cho Thủ thư)
     * @param sach Đối tượng sách cần thêm
     * @return Mã sách mới được sinh
     * @throws SQLException nếu lỗi CSDL
     */
    public int themSach(SachDTO sach) throws SQLException {
        String sql = "INSERT INTO Sach (TenSach, TacGia, NamXuatBan, TrangThai, HinhAnh, GiaTien) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setNString(1, sach.getTenSach());
            ps.setNString(2, sach.getTacGia());
            ps.setInt(3, sach.getNamXuatBan());
            ps.setInt(4, sach.getTrangThai());
            ps.setNString(5, sach.getHinhAnh() != null ? sach.getHinhAnh() : "");
            ps.setInt(6, sach.getGiaTien() > 0 ? sach.getGiaTien() : 50000);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int maSach = rs.getInt(1);
                    sach.setMaSach(maSach);
                    return maSach;
                }
            }
        }
        return -1;
    }

    /**
     * Cập nhật thông tin cuốn sách (Dành cho Thủ thư)
     * @param sach Thông tin cập nhật
     * @return true nếu cập nhật thành công
     * @throws SQLException nếu lỗi CSDL
     */
    public boolean capNhatSach(SachDTO sach) throws SQLException {
        String sql = "UPDATE Sach SET TenSach = ?, TacGia = ?, NamXuatBan = ?, HinhAnh = ?, GiaTien = ? WHERE MaSach = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setNString(1, sach.getTenSach());
            ps.setNString(2, sach.getTacGia());
            ps.setInt(3, sach.getNamXuatBan());
            ps.setNString(4, sach.getHinhAnh());
            ps.setInt(5, sach.getGiaTien() > 0 ? sach.getGiaTien() : 50000);
            ps.setInt(6, sach.getMaSach());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Bổ sung sách mới vào kho thay thế cho sách bị mất/ngừng phục vụ.
     * Kiểm tra điều kiện: không được đưa sách về sẵn sàng nếu còn phiếu mượn/giữ chỗ chưa kết thúc.
     * @param maSach Mã sách cần bổ sung
     * @param giaTien Giá nhập mới của cuốn sách
     * @return true nếu cập nhật thành công
     * @throws SQLException nếu có lỗi CSDL hoặc vi phạm bất biến
     */
    public boolean boSungSachMoi(int maSach, int giaTien) throws SQLException {
        // Kiểm tra tồn tại
        SachDTO current = getBookById(maSach);
        if (current == null) {
            throw new SQLException("Cuốn sách mã " + maSach + " không tồn tại trong thư viện!");
        }

        // Kiểm tra xem có phiếu mượn nào chưa kết thúc (0: Giữ chỗ, 1: Đang mượn, 4: Chờ đền bù, 6: Chờ xác nhận trả)
        String checkActiveLoanSql = "SELECT COUNT(*) FROM PhieuMuon WHERE MaSach = ? AND TrangThaiPhieu IN (0, 1, 4, 6)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psCheck = conn.prepareStatement(checkActiveLoanSql)) {
            psCheck.setInt(1, maSach);
            try (ResultSet rs = psCheck.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new SQLException("Không thể bổ sung sách về trạng thái Sẵn sàng khi đang có phiếu chiếm dụng chưa kết thúc!");
                }
            }
        }

        String sql = "UPDATE Sach SET TrangThai = ?, GiaTien = ? WHERE MaSach = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, LibraryConstants.SACH_SAN_SANG);
            ps.setInt(2, giaTien > 0 ? giaTien : 50000);
            ps.setInt(3, maSach);

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cập nhật trạng thái sách (0: Sẵn sàng, 1: Đang mượn, 2: Giữ chỗ, -1: Ngừng phục vụ / Báo mất).
     * Ngăn chặn việc tùy tiện chuyển sách về Sẵn sàng khi vẫn còn phiếu đang mượn/giữ chỗ.
     * @param maSach Mã sách cần cập nhật
     * @param trangThai Trạng thái mới
     * @return true nếu cập nhật thành công
     * @throws SQLException nếu có lỗi CSDL hoặc vi phạm trạng thái
     */
    public boolean capNhatTrangThai(int maSach, int trangThai) throws SQLException {
        if (trangThai == LibraryConstants.SACH_SAN_SANG) {
            String checkActiveLoanSql = "SELECT COUNT(*) FROM PhieuMuon WHERE MaSach = ? AND TrangThaiPhieu IN (0, 1, 4, 6)";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement psCheck = conn.prepareStatement(checkActiveLoanSql)) {
                psCheck.setInt(1, maSach);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Không thể đưa sách về trạng thái Sẵn sàng khi đang có phiếu mượn/giữ chỗ chưa kết thúc!");
                    }
                }
            }
        }

        String sql = "UPDATE Sach SET TrangThai = ? WHERE MaSach = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, trangThai);
            ps.setInt(2, maSach);

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Xóa một cuốn sách khỏi thư viện (Dành cho Thủ thư).
     * QUY TẮC BẢO TOÀN DỮ LIỆU:
     * 1. Không cho phép xóa sách đang có phiếu hoạt động (0, 1, 4, 6).
     * 2. Không được xóa bản ghi lịch sử trong bảng PhieuMuon để né khóa ngoại (FK)!
     * 3. Nếu sách đã từng có lịch sử giao dịch (mượn/trả/đền bù), từ chối xóa và yêu cầu
     *    chuyển trạng thái sang Ngừng phục vụ (-1) để lưu trữ audit trail.
     * 4. Chỉ cho phép xóa vật lý khi cuốn sách hoàn toàn chưa có bất kỳ phiếu mượn nào.
     *
     * @param maSach Mã sách cần xóa
     * @return true nếu xóa thành công
     * @throws SQLException nếu sách đang được mượn hoặc đã có lịch sử giao dịch
     */
    public boolean xoaSach(int maSach) throws SQLException {
        SachDTO current = getBookById(maSach);
        if (current == null) {
            throw new SQLException("Cuốn sách mã " + maSach + " không tồn tại trong thư viện!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            // Kiểm tra phiếu đang hoạt động
            String checkActiveLoanSql = "SELECT COUNT(*) FROM PhieuMuon WHERE MaSach = ? AND TrangThaiPhieu IN (0, 1, 4, 6)";
            try (PreparedStatement ps = conn.prepareStatement(checkActiveLoanSql)) {
                ps.setInt(1, maSach);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Không thể xóa cuốn sách đang có phiếu mượn/giữ chỗ hoạt động!");
                    }
                }
            }

            // Kiểm tra lịch sử giao dịch (bảo vệ toàn vẹn lịch sử thư viện)
            String checkHistoryLoanSql = "SELECT COUNT(*) FROM PhieuMuon WHERE MaSach = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkHistoryLoanSql)) {
                ps.setInt(1, maSach);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Không thể xóa sách đã từng có lịch sử mượn/trả trong thư viện để bảo toàn tính toàn vẹn dữ liệu. " +
                                "Vui lòng chuyển trạng thái sách sang Ngừng phục vụ (-1)!");
                    }
                }
            }

            // Xóa bản ghi trong bảng Sach khi chưa từng có phiếu mượn
            String delBookSql = "DELETE FROM Sach WHERE MaSach = ?";
            try (PreparedStatement ps = conn.prepareStatement(delBookSql)) {
                ps.setInt(1, maSach);
                int rows = ps.executeUpdate();
                return rows > 0;
            }
        }
    }

    /**
     * Lấy dữ liệu thống kê tổng quan thư viện
     * @return Map chứa các chỉ số thống kê
     * @throws SQLException nếu lỗi CSDL
     */
    public Map<String, Object> layThongKe() throws SQLException {
        Map<String, Object> stats = new HashMap<>();

        String sqlTongSach = "SELECT COUNT(*) FROM Sach";
        String sqlSachSanSang = "SELECT COUNT(*) FROM Sach WHERE TrangThai = 0";
        String sqlSachDangMuon = "SELECT COUNT(*) FROM Sach WHERE TrangThai = 1";
        String sqlSachGiuCho = "SELECT COUNT(*) FROM Sach WHERE TrangThai = 2";
        String sqlSachMat = "SELECT COUNT(*) FROM Sach WHERE TrangThai = -1";
        String sqlTongDocGia = "SELECT COUNT(*) FROM DocGia WHERE VaiTro = 'READER'";
        String sqlDocGiaKhoa = "SELECT COUNT(*) FROM DocGia WHERE TrangThaiTaiKhoan = 0";
        String sqlTongLuotMuon = "SELECT COUNT(*) FROM PhieuMuon";
        String sqlDangMuon = "SELECT COUNT(*) FROM PhieuMuon WHERE TrangThaiPhieu = 1";
        String sqlQuaHan = "SELECT COUNT(*) FROM PhieuMuon WHERE TrangThaiPhieu = 1 AND NgayHenTra < GETDATE()";

        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {

            try (ResultSet rs = st.executeQuery(sqlTongSach)) { if (rs.next()) stats.put("tongSach", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlSachSanSang)) { if (rs.next()) stats.put("sachSanSang", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlSachDangMuon)) { if (rs.next()) stats.put("sachDangMuon", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlSachGiuCho)) { if (rs.next()) stats.put("sachGiuCho", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlSachMat)) { if (rs.next()) stats.put("sachMat", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlTongDocGia)) { if (rs.next()) stats.put("tongDocGia", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlDocGiaKhoa)) { if (rs.next()) stats.put("docGiaKhoa", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlTongLuotMuon)) { if (rs.next()) stats.put("tongLuotMuon", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlDangMuon)) { if (rs.next()) stats.put("dangMuon", rs.getInt(1)); }
            try (ResultSet rs = st.executeQuery(sqlQuaHan)) { if (rs.next()) stats.put("quaHan", rs.getInt(1)); }
        }
        return stats;
    }
}
