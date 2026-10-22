package com.library.dao;

import com.library.model.DocGiaDTO;
import com.library.service.PasswordUtil;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp DocGiaDAO xử lý các thao tác truy vấn, cấp tài khoản và xác thực Độc giả / Thủ thư.
 * - Sử dụng PasswordUtil (SHA-256 + Salt) để mã hóa mật khẩu an toàn.
 * - Tự động nâng cấp mật khẩu cũ (plain text seed) lên mật khẩu băm khi đăng nhập thành công.
 * - Luôn bảo vệ thông tin: không trả mật khẩu hoặc chuỗi băm ra ngoài các phương thức công khai.
 * - Lan truyền SQLException khi gặp sự cố CSDL thay vì nuốt lỗi.
 */
public class DocGiaDAO {

    /**
     * Lấy toàn bộ danh sách người dùng (Độc giả và Thủ thư)
     * @return Danh sách DocGiaDTO an toàn (không chứa mật khẩu)
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public List<DocGiaDTO> getAllDocGia() throws SQLException {
        // Quét cập nhật trạng thái khóa trước khi lấy danh sách
        kiemTraVaCapNhatKhoaTaiKhoan(null);

        List<DocGiaDTO> list = new ArrayList<>();
        String sql = "SELECT MaDocGia, HoTen, Email, SoDienThoai, ISNULL(VaiTro, 'READER') AS VaiTro, " +
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
                        null, // Không gửi mật khẩu
                        rs.getString("SoDienThoai"),
                        rs.getString("VaiTro"),
                        rs.getInt("TrangThaiTaiKhoan"),
                        rs.getString("LyDoKhoa")
                );
                list.add(dg);
            }
        }
        return list;
    }

    /**
     * Xác thực thông tin đăng nhập độc giả / thủ thư.
     * Sử dụng PasswordUtil để kiểm tra mật khẩu.
     * Nếu tài khoản đang dùng mật khẩu cũ (plain text), tự động nâng cấp sang mã băm Salted SHA-256.
     * 
     * @param email Email tài khoản
     * @param matKhau Mật khẩu dạng văn bản
     * @return Đối tượng DocGiaDTO an toàn (mật khẩu đã xóa) nếu đúng thông tin, ngược lại trả về null
     * @throws SQLException nếu lỗi kết nối hoặc truy vấn CSDL
     */
    public DocGiaDTO authenticate(String email, String matKhau) throws SQLException {
        // Tự động kích hoạt kiểm tra quá hạn > 10 ngày để cập nhật khóa nếu vi phạm
        kiemTraVaCapNhatKhoaTaiKhoan(null);

        String sql = "SELECT MaDocGia, HoTen, Email, MatKhau, SoDienThoai, ISNULL(VaiTro, 'READER') AS VaiTro, " +
                     "ISNULL(TrangThaiTaiKhoan, 1) AS TrangThaiTaiKhoan, ISNULL(LyDoKhoa, '') AS LyDoKhoa " +
                     "FROM DocGia WHERE Email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email != null ? email.trim() : "");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int maDocGia = rs.getInt("MaDocGia");
                    String storedPassword = rs.getString("MatKhau");

                    boolean match = PasswordUtil.verifyPassword(matKhau, storedPassword);
                    if (match) {
                        // Tự động nâng cấp mật khẩu cũ sang chuỗi băm kèm Salt nếu cần
                        if (PasswordUtil.isLegacyPassword(storedPassword)) {
                            upgradePassword(conn, maDocGia, matKhau);
                        }

                        // Trả về DTO an toàn (không chứa mật khẩu thuần hay mã băm)
                        return new DocGiaDTO(
                                maDocGia,
                                rs.getString("HoTen"),
                                rs.getString("Email"),
                                null, // sanitized
                                rs.getString("SoDienThoai"),
                                rs.getString("VaiTro"),
                                rs.getInt("TrangThaiTaiKhoan"),
                                rs.getString("LyDoKhoa")
                        );
                    }
                }
            }
        }
        return null;
    }

    private void upgradePassword(Connection conn, int maDocGia, String plainPassword) {
        String updateSql = "UPDATE DocGia SET MatKhau = ? WHERE MaDocGia = ?";
        try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
            String newHashed = PasswordUtil.hashPassword(plainPassword);
            psUpdate.setString(1, newHashed);
            psUpdate.setInt(2, maDocGia);
            psUpdate.executeUpdate();
            System.out.println("[DocGiaDAO] Đã tự động nâng cấp mật khẩu sang SHA-256 + Salt cho độc giả ID #" + maDocGia);
        } catch (SQLException e) {
            System.err.println("[DocGiaDAO] Cảnh báo không thể tự động nâng cấp hash mật khẩu: " + e.getMessage());
        }
    }

    /**
     * Tìm độc giả theo Mã độc giả
     * @param maDocGia Mã độc giả
     * @return Đối tượng DocGiaDTO an toàn hoặc null nếu không tìm thấy
     * @throws SQLException nếu xảy ra lỗi CSDL
     */
    public DocGiaDTO getDocGiaById(int maDocGia) throws SQLException {
        kiemTraVaCapNhatKhoaTaiKhoan(maDocGia);

        String sql = "SELECT MaDocGia, HoTen, Email, SoDienThoai, ISNULL(VaiTro, 'READER') AS VaiTro, " +
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
                            null, // Không gửi mật khẩu
                            rs.getString("SoDienThoai"),
                            rs.getString("VaiTro"),
                            rs.getInt("TrangThaiTaiKhoan"),
                            rs.getString("LyDoKhoa")
                    );
                }
            }
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
             CallableStatement cs = conn.prepareCall(sql)) {

            if (maDocGia != null && maDocGia > 0) {
                cs.setInt(1, maDocGia);
            } else {
                cs.setNull(1, Types.INTEGER);
            }
            cs.registerOutParameter(2, Types.INTEGER);
            cs.execute();
            return cs.getInt(2);
        } catch (SQLException e) {
            System.err.println("[DocGiaDAO.kiemTraVaCapNhatKhoaTaiKhoan] Lỗi quét khóa tài khoản: " + e.getMessage());
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
             CallableStatement cs = conn.prepareCall(sql)) {

            cs.setInt(1, maDocGia);
            cs.execute();
            return true;
        }
    }

    /**
     * Cấp tài khoản Độc giả / Thủ thư mới.
     * Mật khẩu được tự động băm kèm Salt bằng PasswordUtil trước khi lưu vào CSDL.
     * 
     * @param docGia Đối tượng thông tin người dùng mới
     * @return Mã độc giả mới được sinh
     * @throws SQLException nếu email bị trùng lặp hoặc lỗi CSDL
     */
    public int themDocGia(DocGiaDTO docGia) throws SQLException {
        String sql = "INSERT INTO DocGia (HoTen, Email, MatKhau, SoDienThoai, VaiTro, TrangThaiTaiKhoan) VALUES (?, ?, ?, ?, ?, 1)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            String rawPassword = docGia.getMatKhau() != null ? docGia.getMatKhau().trim() : "123456";
            String hashedPassword = PasswordUtil.hashPassword(rawPassword);

            ps.setNString(1, docGia.getHoTen());
            ps.setString(2, docGia.getEmail().trim());
            ps.setString(3, hashedPassword);
            ps.setString(4, docGia.getSoDienThoai() != null ? docGia.getSoDienThoai().trim() : "");
            ps.setString(5, docGia.getVaiTro() != null ? docGia.getVaiTro() : "READER");

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    docGia.setMaDocGia(id);
                    docGia.setMatKhau(null); // Xóa mật khẩu khỏi đối tượng bộ nhớ sau khi lưu
                    return id;
                }
            }
        }
        return -1;
    }
}
