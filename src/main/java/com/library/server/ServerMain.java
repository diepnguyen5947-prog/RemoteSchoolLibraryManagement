package com.library.server;

import com.library.dao.DBConnection;
import com.library.service.ILibraryService;
import com.library.service.LibraryServiceImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Lớp khởi chạy RMI Server (Bài 9 - Java RMI).
 * Thiết lập cấu hình mạng linh hoạt (hỗ trợ triển khai LAN đa máy),
 * phân biệt rõ trạng thái Registry vs Kết nối CSDL,
 * export Remote Object trên cổng cố định để cấu hình tường lửa (firewall) an toàn.
 */
public class ServerMain {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_REGISTRY_PORT = 1099;
    public static final int DEFAULT_EXPORT_PORT = 1098;
    public static final String DEFAULT_SERVICE_NAME = "LibraryService";

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  HỆ THỐNG QUẢN LÝ THƯ VIỆN TRƯỜNG HỌC TỪ XA (RMI SERVER)");
        System.out.println("  Học phần: CMP180 - Lập trình mạng máy tính");
        System.out.println("====================================================================");

        // 1. Đọc và áp dụng cấu hình Host & Port
        String rmiHost = System.getProperty("rmi.host", System.getenv().getOrDefault("RMI_HOST", DEFAULT_HOST));
        int registryPort = getIntProperty("rmi.port", "RMI_PORT", DEFAULT_REGISTRY_PORT);
        int exportPort = getIntProperty("rmi.export.port", "RMI_EXPORT_PORT", DEFAULT_EXPORT_PORT);
        String serviceName = System.getProperty("rmi.service", System.getenv().getOrDefault("RMI_SERVICE", DEFAULT_SERVICE_NAME));

        // BẮT BUỘC thiết lập java.rmi.server.hostname trước khi export remote object để Client LAN gọi đúng IP
        System.setProperty("java.rmi.server.hostname", rmiHost);

        System.out.println("[CẤU HÌNH MẠNG]");
        System.out.println("  - Server Host IP:       " + rmiHost + " (java.rmi.server.hostname)");
        System.out.println("  - Cổng RMI Registry:    " + registryPort);
        System.out.println("  - Cổng Export Object:   " + exportPort + " (Dành cho mở Firewall)");
        System.out.println("  - Tên Service Binding:  " + serviceName);
        System.out.println("--------------------------------------------------------------------");

        // 2. Kiểm tra kết nối CSDL SQL Server (Phân biệt rõ trạng thái DB vs Registry)
        System.out.println("[1] Đang kiểm tra kết nối tới Microsoft SQL Server...");
        System.out.println("    URL: " + DBConnection.getDbUrl());
        System.out.println("    User: " + DBConnection.getDbUser());
        boolean dbOk = DBConnection.testConnection();

        if (dbOk) {
            System.out.println("    -> KẾT NỐI CSDL THÀNH CÔNG! Sẵn sàng xử lý nghiệp vụ thư viện.");
        } else {
            System.err.println("    -> [CẢNH BÁO HẠ TẦNG] KẾT NỐI CSDL THẤT BẠI HOẶC CHƯA SẴN SÀNG!");
            System.err.println("    -> Hệ thống sẽ chạy ở trạng thái suy giảm (DEGRADED). Các lời gọi truy vấn CSDL sẽ trả mã lỗi SYSTEM_ERROR.");
        }

        // 3. Khởi tạo RMI Registry và Export Remote Object
        try {
            System.out.println("[2] Đang khởi tạo RMI Registry trên cổng " + registryPort + "...");
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(registryPort);
                System.out.println("    -> Đã tạo mới RMI Registry tại cổng " + registryPort);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(registryPort);
                System.out.println("    -> Đã tìm thấy RMI Registry sẵn có tại cổng " + registryPort);
            }

            System.out.println("[3] Đang khởi tạo và export Remote Object trên cổng dịch vụ " + exportPort + "...");
            ILibraryService libraryService = new LibraryServiceImpl(exportPort);
            registry.rebind(serviceName, libraryService);

            System.out.println("    -> Đã đăng ký dịch vụ thành công: rmi://" + rmiHost + ":" + registryPort + "/" + serviceName);
            System.out.println("====================================================================");
            if (dbOk) {
                System.out.println("  [TRẠNG THÁI: HEALTHY] RMI SERVER SẴN SÀNG TIẾP NHẬN YÊU CẦU TỪ CÁC CLIENT!");
            } else {
                System.out.println("  [TRẠNG THÁI: DEGRADED] RMI SERVER ĐÃ BIND NHƯNG CƠ SỞ DỮ LIỆU ĐANG NGOẠI TUYẾN!");
            }
            System.out.println("====================================================================");

            // Giữ cho Server tiếp tục chạy
            while (true) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException ignored) {}
            }

        } catch (Exception e) {
            System.err.println("[ServerMain] LỖI KHỞI CHẠY RMI SERVER: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int getIntProperty(String propName, String envName, int defaultValue) {
        String val = System.getProperty(propName);
        if (val == null || val.trim().isEmpty()) {
            val = System.getenv(envName);
        }
        if (val != null && !val.trim().isEmpty()) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
