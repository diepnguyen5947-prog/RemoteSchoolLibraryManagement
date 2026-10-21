package com.library.dao;

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
 */
public class SachDAO {

    /**
     * Lấy toàn bộ danh sách sách trong thư viện
     * @return Danh sách các đối tượng SachDTO
     */
    public List<SachDTO> getAllBooks() {
        List<SachDTO> list = new ArrayList<>();
        String sql = "SELECT MaSach, TenSach, TacGia, NamXuatBan, TrangThai, HinhAnh, ISNULL(GiaTien, 50000) AS GiaTien FROM Sach ORDER BY MaSach ASC";

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
                list.add(s);
            }
        } catch (SQLException e) {
            System.err.println("[SachDAO.getAllBooks] Lỗi: " + e.getMessage());
        }
        return list;
    }

    /**
     * Tìm kiếm sách theo từ khóa (khớp tên sách hoặc tác giả)
     * @param keyword Từ khóa tìm kiếm
     * @return Danh sách các cuốn sách thỏa mãn điều kiện
     */
    public List<SachDTO> searchBooks(String keyword) {
        List<SachDTO> list = new ArrayList<>();
        String sql = "SELECT MaSach, TenSach, TacGia, NamXuatBan, TrangThai, HinhAnh, ISNULL(GiaTien, 50000) AS GiaTien FROM Sach " +
                     "WHERE TenSach LIKE ? OR TacGia LIKE ? ORDER BY MaSach ASC";

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
                    list.add(s);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SachDAO.searchBooks] Lỗi: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy thông tin chi tiết một cuốn sách theo Mã sách
     * @param maSach Mã sách cần tra cứu
     * @return Đối tượng SachDTO hoặc null nếu không tìm thấy
     */
    public SachDTO getBookById(int maSach) {
        String sql = "SELECT MaSach, TenSach, TacGia, NamXuatBan, TrangThai, HinhAnh, ISNULL(GiaTien, 50000) AS GiaTien FROM Sach WHERE MaSach = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maSach);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SachDTO(
                            rs.getInt("MaSach"),
                            rs.getString("TenSach"),
                            rs.getString("TacGia"),
                            rs.getInt("NamXuatBan"),
                            rs.getInt("TrangThai"),
                            rs.getString("HinhAnh"),
                            rs.getInt("GiaTien")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[SachDAO.getBookById] Lỗi: " + e.getMessage());
        }
        return null;
    }

    /**
     * Thêm một cuốn sách mới vào kho (Dành cho Thủ thư)
     * @param sach Đối tượng sách cần thêm
     * @return Mã sách vừa được cấp phát
     * @throws SQLException nếu lỗi CSDL
     */
    public int themSach(SachDTO sach) throws SQLException {
        String sql = "INSERT INTO Sach (TenSach, TacGia, NamXuatBan, TrangThai, HinhAnh, GiaTien) VALUES (?, ?, ?, 0, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setNString(1, sach.getTenSach());
            ps.setNString(2, sach.getTacGia());
            ps.setInt(3, sach.getNamXuatBan());
            ps.setNString(4, sach.getHinhAnh());
            ps.setInt(5, sach.getGiaTien() > 0 ? sach.getGiaTien() : 50000);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    sach.setMaSach(id);
                    return id;
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
     * Đưa trạng thái sách về 0 (Sẵn sàng) và cập nhật giá nhập mới nếu có.
     * @param maSach Mã sách cần bổ sung
     * @param giaTien Giá nhập mới của cuốn sách
     * @return true nếu cập nhật thành công
     * @throws SQLException nếu có lỗi CSDL
     */
    public boolean boSungSachMoi(int maSach, int giaTien) throws SQLException {
        String sql = "UPDATE Sach SET TrangThai = 0, GiaTien = ? WHERE MaSach = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, giaTien > 0 ? giaTien : 50000);
            ps.setInt(2, maSach);

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cập nhật trạng thái sách (0: Sẵn sàng, 1: Đang mượn, 2: Giữ chỗ, -1: Báo mất)
     * @param maSach Mã sách cần cập nhật
     * @param trangThai Trạng thái mới
     * @return true nếu cập nhật thành công
     * @throws SQLException nếu có lỗi CSDL
     */
    public boolean capNhatTrangThai(int maSach, int trangThai) throws SQLException {
        String sql = "UPDATE Sach SET TrangThai = ? WHERE MaSach = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, trangThai);
            ps.setInt(2, maSach);

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Xóa một cuốn sách khỏi thư viện (Dành cho Thủ thư)
     * @param maSach Mã sách cần xóa
     * @return true nếu xóa thành công
     * @throws SQLException nếu sách đang được mượn hoặc lỗi ràng buộc khóa ngoại
     */
    public boolean xoaSach(int maSach) throws SQLException {
        SachDTO current = getBookById(maSach);
        if (current == null) {
            throw new SQLException("Cuốn sách mã " + maSach + " không tồn tại!");
        }
        if (current.getTrangThai() == 1) {
            throw new SQLException("Không thể xóa cuốn sách đang được mượn!");
        }

        // Xóa các phiếu mượn lịch sử đã trả trước khi xóa sách (để tránh vi phạm FK)
        String delLoansSql = "DELETE FROM PhieuMuon WHERE MaSach = ? AND TrangThaiPhieu = 1";
        String delBookSql = "DELETE FROM Sach WHERE MaSach = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(delLoansSql);
                 PreparedStatement ps2 = conn.prepareStatement(delBookSql)) {

                ps1.setInt(1, maSach);
                ps1.executeUpdate();

                ps2.setInt(1, maSach);
                int rows = ps2.executeUpdate();

                conn.commit();
                return rows > 0;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Lấy các chỉ số thống kê phục vụ Trang tổng quan Thủ thư (Dashboard Stats)
     * @return Map chứa các chỉ số
     */
    public Map<String, Object> layThongKe() {
        Map<String, Object> stats = new HashMap<>();

        String sql = "SELECT " +
                     "(SELECT COUNT(*) FROM Sach) AS TongSoSach, " +
                     "(SELECT COUNT(*) FROM Sach WHERE TrangThai = 1) AS SoSachDangMuon, " +
                     "(SELECT COUNT(*) FROM Sach WHERE TrangThai = 0) AS SoSachSanSang, " +
                     "(SELECT COUNT(*) FROM Sach WHERE TrangThai = 2) AS SoSachDangGiu, " +
                     "(SELECT COUNT(*) FROM DocGia WHERE ISNULL(VaiTro, 'READER') = 'READER') AS TongSoDocGia, " +
                     "(SELECT COUNT(*) FROM PhieuMuon) AS TongSoPhieuMuon, " +
                     "(SELECT COUNT(*) FROM PhieuMuon WHERE TrangThaiPhieu = 1) AS SoPhieuDangMuon, " +
                     "(SELECT COUNT(*) FROM PhieuMuon WHERE TrangThaiPhieu = 0) AS SoPhieuChoLay";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                stats.put("tongSoSach", rs.getInt("TongSoSach"));
                stats.put("soSachDangMuon", rs.getInt("SoSachDangMuon"));
                stats.put("soSachSanSang", rs.getInt("SoSachSanSang"));
                stats.put("soSachDangGiu", rs.getInt("SoSachDangGiu"));
                stats.put("tongSoDocGia", rs.getInt("TongSoDocGia"));
                stats.put("tongSoPhieuMuon", rs.getInt("TongSoPhieuMuon"));
                stats.put("soPhieuDangMuon", rs.getInt("SoPhieuDangMuon"));
                stats.put("soPhieuChoLay", rs.getInt("SoPhieuChoLay"));
            }
        } catch (SQLException e) {
            System.err.println("[SachDAO.layThongKe] Lỗi: " + e.getMessage());
        }
        return stats;
    }
}
