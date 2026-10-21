package com.library.dao;

import com.library.model.DocGiaDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp DocGiaDAO xử lý các thao tác truy vấn, cấp tài khoản và xác thực Độc giả / Thủ thư.
 */
public class DocGiaDAO {

    /**
     * Lấy toàn bộ danh sách người dùng (Độc giả và Thủ thư)
     * @return Danh sách DocGiaDTO
     */
    public List<DocGiaDTO> getAllDocGia() {
        // Quét cập nhật trạng thái khóa trước khi lấy danh sách
        kiemTraVaCapNhatKhoaTaiKhoan(null);

        List<DocGiaDTO> list = new ArrayList<>();
        String sql = "SELECT MaDocGia, HoTen, Email, MatKhau, SoDienThoai, ISNULL(VaiTro, 'READER') AS VaiTro, " +
                     "ISNULL(TrangThaiTaiKhoan, 1) AS TrangThaiTaiKhoan, ISNULL(LyDoKhoa, '') AS LyDoKhoa " +
                     "FROM DocGia ORDER BY MaDocGia ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                DocGiaDTO dg = new DocGiaDTO(
                        rs.getInt("MaDocGia"),
                        rs.getString("HoTen"),
                        rs.getString("Email"),
                        rs.getString("MatKhau"),
                        rs.getString("SoDienThoai"),
                        rs.getString("VaiTro"),
                        rs.getInt("TrangThaiTaiKhoan"),
                        rs.getString("LyDoKhoa")
                );
                list.add(dg);
            }
        } catch (SQLException e) {
            System.err.println("[DocGiaDAO.getAllDocGia] Lỗi: " + e.getMessage());
        }
        return list;
    }

    /**
     * Xác thực thông tin đăng nhập độc giả / thủ thư
     * @param email Email tài khoản
     * @param matKhau Mật khẩu
     * @return Đối tượng DocGiaDTO có chứa vai trò nếu đúng thông tin, ngược lại trả về null
     */
    public DocGiaDTO authenticate(String email, String matKhau) {
        // Tự động kích hoạt kiểm tra quá hạn > 10 ngày để cập nhật khóa nếu vi phạm
        kiemTraVaCapNhatKhoaTaiKhoan(null);

        String sql = "SELECT MaDocGia, HoTen, Email, MatKhau, SoDienThoai, ISNULL(VaiTro, 'READER') AS VaiTro, " +
                     "ISNULL(TrangThaiTaiKhoan, 1) AS TrangThaiTaiKhoan, ISNULL(LyDoKhoa, '') AS LyDoKhoa " +
                     "FROM DocGia WHERE Email = ? AND MatKhau = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email != null ? email.trim() : "");
            ps.setString(2, matKhau != null ? matKhau.trim() : "");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DocGiaDTO(
                            rs.getInt("MaDocGia"),
                            rs.getString("HoTen"),
                            rs.getString("Email"),
                            rs.getString("MatKhau"),
                            rs.getString("SoDienThoai"),
                            rs.getString("VaiTro"),
                            rs.getInt("TrangThaiTaiKhoan"),
                            rs.getString("LyDoKhoa")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[DocGiaDAO.authenticate] Lỗi: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tìm độc giả theo Mã độc giả
     * @param maDocGia Mã độc giả
     * @return Đối tượng DocGiaDTO hoặc null
     */
    public DocGiaDTO getDocGiaById(int maDocGia) {
        // Quét cập nhật khóa cho độc giả này nếu có sách quá hạn > 10 ngày
        kiemTraVaCapNhatKhoaTaiKhoan(maDocGia);

        String sql = "SELECT MaDocGia, HoTen, Email, MatKhau, SoDienThoai, ISNULL(VaiTro, 'READER') AS VaiTro, " +
                     "ISNULL(TrangThaiTaiKhoan, 1) AS TrangThaiTaiKhoan, ISNULL(LyDoKhoa, '') AS LyDoKhoa " +
                     "FROM DocGia WHERE MaDocGia = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, maDocGia);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DocGiaDTO(
                            rs.getInt("MaDocGia"),
                            rs.getString("HoTen"),
                            rs.getString("Email"),
                            rs.getString("MatKhau"),
                            rs.getString("SoDienThoai"),
                            rs.getString("VaiTro"),
                            rs.getInt("TrangThaiTaiKhoan"),
                            rs.getString("LyDoKhoa")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[DocGiaDAO.getDocGiaById] Lỗi: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tự động quét kiểm tra và khóa tài khoản độc giả có sách mượn quá hạn trên 10 ngày
     * @param maDocGia Mã độc giả cụ thể cần kiểm tra hoặc null để quét toàn bộ
     * @return Số lượng độc giả bị khóa
     */
    public int kiemTraVaCapNhatKhoaTaiKhoan(Integer maDocGia) {
        String sql = "{call sp_KiemTraVaCapNhatKhoaTaiKhoan(?, ?)}";
        try (Connection conn = DBConnection.getConnection();
             java.sql.CallableStatement cs = conn.prepareCall(sql)) {

            if (maDocGia != null && maDocGia > 0) {
                cs.setInt(1, maDocGia);
            } else {
                cs.setNull(1, java.sql.Types.INTEGER);
            }
            cs.registerOutParameter(2, java.sql.Types.INTEGER);
            cs.execute();
            return cs.getInt(2);
        } catch (SQLException e) {
            System.err.println("[DocGiaDAO.kiemTraVaCapNhatKhoaTaiKhoan] Lỗi: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Mở khóa tài khoản độc giả (Dành cho Thủ thư sau khi thu tiền phạt và sách quá hạn)
     * @param maDocGia Mã độc giả
     * @return true nếu mở khóa thành công
     * @throws SQLException nếu độc giả vẫn còn sách quá hạn > 10 ngày chưa trả hoặc lỗi CSDL
     */
    public boolean moKhoaTaiKhoan(int maDocGia) throws SQLException {
        String sql = "{call sp_MoKhoaTaiKhoan(?)}";
        try (Connection conn = DBConnection.getConnection();
             java.sql.CallableStatement cs = conn.prepareCall(sql)) {

            cs.setInt(1, maDocGia);
            cs.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("[DocGiaDAO.moKhoaTaiKhoan] Lỗi: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Cấp tài khoản Độc giả / Thủ thư mới (Dành cho Quản trị viên/Thủ thư)
     * @param docGia Đối tượng thông tin người dùng mới
     * @return Mã độc giả mới được sinh
     * @throws SQLException nếu email bị trùng lặp hoặc lỗi CSDL
     */
    public int themDocGia(DocGiaDTO docGia) throws SQLException {
        String sql = "INSERT INTO DocGia (HoTen, Email, MatKhau, SoDienThoai, VaiTro, TrangThaiTaiKhoan) VALUES (?, ?, ?, ?, ?, 1)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setNString(1, docGia.getHoTen());
            ps.setString(2, docGia.getEmail().trim());
            ps.setString(3, docGia.getMatKhau().trim());
            ps.setString(4, docGia.getSoDienThoai());
            ps.setString(5, docGia.getVaiTro() != null ? docGia.getVaiTro() : "READER");

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    docGia.setMaDocGia(id);
                    return id;
                }
            }
        }
        return -1;
    }
}
