package com.library.dao;

import com.library.model.PhanHoiDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO xử lý tương tác cơ sở dữ liệu cho bảng PhanHoi (Phản hồi & Báo cáo tình trạng sách).
 * Lan truyền SQLException để phát hiện lỗi hệ thống chính xác.
 */
public class PhanHoiDAO {

    /**
     * Thêm mới một phản hồi từ độc giả
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public boolean guiPhanHoi(int maDocGia, int maSach, String tieuDe, String noiDung) throws SQLException {
        String sql = "INSERT INTO PhanHoi (MaDocGia, MaSach, TieuDe, NoiDung, NgayGui, TrangThai) VALUES (?, ?, ?, ?, GETDATE(), 0)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (maDocGia > 0) {
                ps.setInt(1, maDocGia);
            } else {
                ps.setNull(1, Types.INTEGER);
            }

            if (maSach > 0) {
                ps.setInt(2, maSach);
            } else {
                ps.setNull(2, Types.INTEGER);
            }

            ps.setNString(3, (tieuDe != null && !tieuDe.isEmpty()) ? tieuDe : "Báo cáo tình trạng sách");
            ps.setNString(4, noiDung != null ? noiDung : "");

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Lấy danh sách tất cả phản hồi kèm tên độc giả và tên sách
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public List<PhanHoiDTO> getAllPhanHoi() throws SQLException {
        List<PhanHoiDTO> list = new ArrayList<>();
        String sql = "SELECT p.MaPhanHoi, p.MaDocGia, ISNULL(d.HoTen, N'Khách vãng lai') AS TenDocGia, " +
                     "       ISNULL(d.Email, 'N/A') AS EmailDocGia, " +
                     "       p.MaSach, ISNULL(s.TenSach, N'Không xác định') AS TenSach, " +
                     "       p.TieuDe, p.NoiDung, p.NgayGui, p.TrangThai, p.GhiChuThuThu " +
                     "FROM PhanHoi p " +
                     "LEFT JOIN DocGia d ON p.MaDocGia = d.MaDocGia " +
                     "LEFT JOIN Sach s ON p.MaSach = s.MaSach " +
                     "ORDER BY p.TrangThai ASC, p.NgayGui DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                PhanHoiDTO dto = new PhanHoiDTO(
                        rs.getInt("MaPhanHoi"),
                        rs.getInt("MaDocGia"),
                        rs.getString("TenDocGia"),
                        rs.getString("EmailDocGia"),
                        rs.getInt("MaSach"),
                        rs.getString("TenSach"),
                        rs.getString("TieuDe"),
                        rs.getString("NoiDung"),
                        rs.getTimestamp("NgayGui"),
                        rs.getInt("TrangThai"),
                        rs.getString("GhiChuThuThu")
                );
                list.add(dto);
            }
        }
        return list;
    }

    /**
     * Đếm số lượng phản hồi mới chưa được thủ thư xử lý (TrangThai = 0)
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public int demPhanHoiChuaXuLy() throws SQLException {
        String sql = "SELECT COUNT(*) FROM PhanHoi WHERE TrangThai = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Cập nhật trạng thái phản hồi (1: Đã xử lý / Đã tiếp nhận)
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public boolean capNhatTrangThai(int maPhanHoi, int trangThai, String ghiChu) throws SQLException {
        String sql = "UPDATE PhanHoi SET TrangThai = ?, GhiChuThuThu = ? WHERE MaPhanHoi = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, trangThai);
            ps.setNString(2, ghiChu != null ? ghiChu : "");
            ps.setInt(3, maPhanHoi);

            return ps.executeUpdate() > 0;
        }
    }
}
