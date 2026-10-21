package com.library.web;

import com.library.web.config.RMIClientManager;
import com.library.web.controller.BookApiController;
import com.library.web.controller.WebStaticController;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Ứng dụng Web Client Server (Giai đoạn 3).
 * Đóng vai trò là Web Server phục vụ giao diện HTML/Bootstrap và REST API,
 * đồng thời là RMI Client kết nối tới RMI Server (rmi://localhost:1099/LibraryService).
 */
public class WebServerApp {

    public static final int HTTP_PORT = Integer.getInteger("server.port", 8080);

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  KHỞI ĐỘNG ỨNG DỤNG WEB CLIENT (REMOTE LIBRARY MANAGEMENT)");
        System.out.println("====================================================================");

        try {
            // 1. Kiểm tra kết nối sơ bộ tới RMI Server
            System.out.println("[1] Đang kết nối tới RMI Server tại: " + RMIClientManager.getRmiUrl() + "...");
            try {
                RMIClientManager.getInstance().getService();
                System.out.println("    -> Kết nối RMI Server THÀNH CÔNG!");
            } catch (Exception ex) {
                System.out.println("    -> CẢNH BÁO: Chưa kết nối được RMI Server (" + ex.getMessage() + ").");
                System.out.println("    -> Web Server sẽ tự động thử kết nối lại khi có yêu cầu từ người dùng.");
            }

            // 2. Xác định thư mục chứa static HTML files
            String staticDir = "src/main/resources/static";
            if (!new File(staticDir).exists()) {
                staticDir = "resources/static";
            }
            System.out.println("[2] Thư mục tài nguyên Web tĩnh: " + new File(staticDir).getAbsolutePath());

            // 3. Khởi tạo HTTP Server trên cổng 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

            // Gán Controller phục vụ giao diện Web HTML/Bootstrap
            WebStaticController staticController = new WebStaticController(staticDir);
            server.createContext("/", staticController);

            // Gán Controller phục vụ REST API Endpoints
            BookApiController apiController = new BookApiController();
            server.createContext("/api/", apiController);

            // Thiết lập Thread Pool đa luồng (20 worker threads) phục vụ kiểm thử đồng thời (Concurrency Test)
            server.setExecutor(Executors.newFixedThreadPool(20));

            // Bắt đầu lắng nghe
            server.start();

            System.out.println("====================================================================");
            System.out.println("  WEB CLIENT ĐÃ KHỞI CHẠY THÀNH CÔNG TRÊN CỔNG " + HTTP_PORT + "!");
            System.out.println("  -> URL TRANG CHỦ TRA CỨU:    http://localhost:" + HTTP_PORT);
            System.out.println("  -> URL QUẢN LÝ PHIẾU MƯỢN:   http://localhost:" + HTTP_PORT + "/phieu-muon");
            System.out.println("====================================================================");

            // Giữ cho Web Server tiếp tục chạy
            while (true) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException ignored) {}
            }

        } catch (Exception e) {
            System.err.println("[WebServerApp] Lỗi khởi chạy Web Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
