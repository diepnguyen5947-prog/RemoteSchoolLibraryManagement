package com.library.dao;

import com.library.model.SachDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO xử lý tương tác cơ sở dữ liệu cho bảng YeuThich (Danh sách sách yêu thích của từng độc giả).
 */
public class YeuThichDAO {

    /**
     * Kiểm tra xem cuốn sách đã được độc giả lưu vào danh sách yêu thích chưa
     */
    public boolean isYeuThich(int maDocGia, int maSach) {
        String sql = "SELECT COUNT(*) FROM YeuThich WHERE MaDocGia = ? AND MaSach = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maDocGia);
            ps.setInt(2, maSach);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[YeuThichDAO.isYeuThich] Lỗi: " + e.getMessage());
        }
        return false;
    }

    /**
     * Bật/Tắt trạng thái yêu thích:
     * - Nếu chưa có: Thêm vào mục yêu thích (trả về true).
     * - Nếu đã có: Xóa khỏi mục yêu thích (trả về false).
     */
    public boolean toggleYeuThich(int maDocGia, int maSach) {
        if (isYeuThich(maDocGia, maSach)) {
            // Xóa khỏi yêu thích
            String sqlDelete = "DELETE FROM YeuThich WHERE MaDocGia = ? AND MaSach = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlDelete)) {

                ps.setInt(1, maDocGia);
                ps.setInt(2, maSach);
                ps.executeUpdate();
                return false; // Đã bỏ yêu thích
            } catch (SQLException e) {
                System.err.println("[YeuThichDAO.toggleYeuThich - Delete] Lỗi: " + e.getMessage());
            }
        } else {
            // Thêm vào yêu thích
            String sqlInsert = "INSERT INTO YeuThich (MaDocGia, MaSach, NgayThem) VALUES (?, ?, GETDATE())";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlInsert)) {

                ps.setInt(1, maDocGia);
                ps.setInt(2, maSach);
                ps.executeUpdate();
                return true; // Đã thêm yêu thích
            } catch (SQLException e) {
                System.err.println("[YeuThichDAO.toggleYeuThich - Insert] Lỗi: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Lấy danh sách toàn bộ các cuốn sách yêu thích của một độc giả
     */
    public List<SachDTO> getDanhSachYeuThich(int maDocGia) {
        List<SachDTO> list = new ArrayList<>();
        String sql = "SELECT s.MaSach, s.TenSach, s.TacGia, s.NamXuatBan, s.TrangThai, s.HinhAnh " +
                     "FROM YeuThich y " +
                     "INNER JOIN Sach s ON y.MaSach = s.MaSach " +
                     "WHERE y.MaDocGia = ? " +
                     "ORDER BY y.NgayThem DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maDocGia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SachDTO s = new SachDTO(
                            rs.getInt("MaSach"),
                            rs.getString("TenSach"),
                            rs.getString("TacGia"),
                            rs.getInt("NamXuatBan"),
                            rs.getInt("TrangThai"),
                            rs.getString("HinhAnh")
                    );
                    list.add(s);
                }
            }
        } catch (SQLException e) {
            System.err.println("[YeuThichDAO.getDanhSachYeuThich] Lỗi: " + e.getMessage());
        }
        return list;
    }
}
