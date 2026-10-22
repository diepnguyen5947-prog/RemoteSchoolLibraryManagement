package com.library.dao;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Lớp quản lý kết nối JDBC đến Microsoft SQL Server cho CSDL ThuVienDB.
 * Đọc cấu hình ưu tiên theo thứ tự:
 * 1. File local "db.properties" (không commit Git)
 * 2. Biến môi trường (DB_URL, DB_USER, DB_PASSWORD)
 * 3. System Properties (db.url, db.user, db.password)
 * 4. Giá trị mặc định cho môi trường phát triển nội bộ.
 * 
 * Loại bỏ hoàn toàn cơ chế thử mật khẩu dự phòng (candidate passwords loop)
 * nhằm đảm bảo lỗi kết nối được phát hiện và báo chính xác, không che giấu lỗi hạ tầng.
 */
public class DBConnection {

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    static {
        loadConfiguration();
        try {
            // Tải driver Microsoft SQL Server JDBC
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] CẢNH BÁO: Chưa tìm thấy SQLServerDriver trong ClassPath! " + e.getMessage());
        }
    }

    private static synchronized void loadConfiguration() {
        Properties props = new Properties();

        // 1. Thử nạp từ file db.properties tại thư mục hiện tại
        File propFile = new File("db.properties");
        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                props.load(in);
            } catch (Exception e) {
                System.err.println("[DBConnection] Không thể đọc file db.properties: " + e.getMessage());
            }
        } else {
            // Thử nạp từ classpath
            try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (Exception ignored) {}
        }

        // Đọc URL
        String url = props.getProperty("db.url");
        if (url == null || url.trim().isEmpty()) {
            url = System.getenv("DB_URL");
        }
        if (url == null || url.trim().isEmpty()) {
            url = System.getProperty("db.url", 
                    "jdbc:sqlserver://127.0.0.1:1433;databaseName=ThuVienDB;trustServerCertificate=true;");
        }
        dbUrl = url.trim();

        // Đọc User
        String user = props.getProperty("db.user");
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("DB_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            user = System.getProperty("db.user", "sa");
        }
        dbUser = user.trim();

        // Đọc Password
        String pass = props.getProperty("db.password");
        if (pass == null) {
            pass = System.getenv("DB_PASSWORD");
        }
        if (pass == null) {
            pass = System.getProperty("db.password", "123456");
        }
        dbPassword = pass;
    }

    /**
     * Mở và trả về một kết nối Connection đến SQL Server.
     * @return Connection đối tượng kết nối
     * @throws SQLException nếu kết nối thất bại
     */
    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } catch (SQLException ex) {
            System.err.println("[DBConnection] Lỗi kết nối CSDL (" + dbUrl + ", user=" + dbUser + "): " + ex.getMessage());
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
        return dbUrl;
    }

    public static String getDbUser() {
        return dbUser;
    }
}
