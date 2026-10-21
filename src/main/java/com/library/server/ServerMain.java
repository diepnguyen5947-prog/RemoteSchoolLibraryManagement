package com.library.server;

import com.library.dao.DBConnection;
import com.library.service.ILibraryService;
import com.library.service.LibraryServiceImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Lớp khởi chạy RMI Server (Bài 9 - Java RMI).
 * Thiết lập kết nối CSDL, tạo RMI Registry tại cổng 1099 và đăng ký dịch vụ LibraryService.
 */
public class ServerMain {

    public static final int RMI_PORT = 1099;
    public static final String SERVICE_NAME = "LibraryService";

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  HỆ THỐNG QUẢN LÝ MƯỢN/TRẢ SÁCH THƯ VIỆN TRƯỜNG HỌC (RMI SERVER)");
        System.out.println("====================================================================");

        // 1. Kiểm tra cấu hình kết nối CSDL
        System.out.println("[1] Đang kiểm tra cấu hình kết nối CSDL SQL Server...");
        System.out.println("    URL: " + DBConnection.getDbUrl());
        boolean dbOk = DBConnection.testConnection();
        if (dbOk) {
            System.out.println("    -> Kết nối CSDL ThuVienDB: THÀNH CÔNG!");
        } else {
            System.out.println("    -> CẢNH BÁO: Kết nối JDBC trực tiếp chưa sẵn sàng hoặc cổng 1433 đang khởi động.");
            System.out.println("    -> RMI Server vẫn tiếp tục khởi chạy để phục vụ các yêu cầu từ Client.");
        }

        // 2. Khởi tạo RMI Registry và Đăng ký dịch vụ Remote
        try {
            System.out.println("[2] Đang khởi tạo RMI Registry trên cổng " + RMI_PORT + "...");
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(RMI_PORT);
                System.out.println("    -> Đã tạo mới RMI Registry tại cổng " + RMI_PORT);
            } catch (Exception e) {
                // Nếu Registry đã tồn tại trên cổng 1099
                registry = LocateRegistry.getRegistry(RMI_PORT);
                System.out.println("    -> Đã tìm thấy RMI Registry sẵn có tại cổng " + RMI_PORT);
            }

            System.out.println("[3] Đang khởi tạo và đăng ký đối tượng Remote Service...");
            ILibraryService libraryService = new LibraryServiceImpl();
            registry.rebind(SERVICE_NAME, libraryService);

            System.out.println("    -> Đã đăng ký dịch vụ: rmi://localhost:" + RMI_PORT + "/" + SERVICE_NAME);
            System.out.println("====================================================================");
            System.out.println("  RMI SERVER ĐÃ SẴN SÀNG TIẾP NHẬN YÊU CẦU TỪ CÁC CLIENT TỪ XA!");
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
        }
    }
}
