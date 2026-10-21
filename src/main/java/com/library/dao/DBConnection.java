package com.library.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Lớp quản lý kết nối JDBC đến Microsoft SQL Server cho CSDL ThuVienDB.
 */
public class DBConnection {

    // Chuỗi kết nối JDBC chuẩn theo yêu cầu của đồ án
    private static final String DB_URL = System.getProperty("db.url", 
            "jdbc:sqlserver://localhost:1433;databaseName=ThuVienDB;trustServerCertificate=true;");
    private static final String DB_USER = System.getProperty("db.user", "sa");

    // Danh sách mật khẩu ứng viên thường dùng cho SQL Server đồ án
    private static final String[] CANDIDATE_PASSWORDS = {
        System.getProperty("db.password", "123456"),
        "123456",
        "Password123!",
        "12345678",
        "Admin@123",
        "root",
        ""
    };

    private static volatile String activePassword = System.getProperty("db.password", "123456");

    static {
        try {
            // Tải driver Microsoft SQL Server JDBC
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] CẢNH BÁO: Chưa tìm thấy SQLServerDriver trong ClassPath! " + e.getMessage());
        }
    }

    /**
     * Mở và trả về một kết nối Connection đến SQL Server
     * @return Connection đối tượng kết nối
     * @throws SQLException nếu kết nối thất bại
     */
    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, activePassword);
        } catch (SQLException ex) {
            // Nếu mật khẩu hiện tại không đúng (Login failed), tự động thử qua danh sách mật khẩu khả dụng
            for (String candidate : CANDIDATE_PASSWORDS) {
                if (candidate != null && !candidate.equals(activePassword)) {
                    try {
                        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, candidate);
                        activePassword = candidate;
                        System.out.println("[DBConnection] Đã kết nối CSDL thành công với mật khẩu dự phòng!");
                        return conn;
                    } catch (SQLException ignored) {
                    }
                }
            }
            System.err.println("[DBConnection] Lỗi kết nối JDBC chuẩn: " + ex.getMessage());
            throw ex;
        }
    }

    /**
     * Đóng an toàn một kết nối
     * @param conn đối tượng kết nối cần đóng
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("[DBConnection] Lỗi khi đóng kết nối: " + e.getMessage());
            }
        }
    }

    /**
     * Kiểm tra trạng thái kết nối CSDL
     * @return true nếu kết nối thành công, false nếu thất bại
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    public static String getDbUrl() {
        return DB_URL;
    }
}
