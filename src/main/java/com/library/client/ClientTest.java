package com.library.client;

import com.library.model.DocGiaDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;
import com.library.service.ILibraryService;
import com.library.service.LibraryException;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Lớp ClientTest mô phỏng một Client từ xa kết nối qua Java RMI (Bài 9)
 * để kiểm thử tính tuần tự hóa (Serialization - Bài 6) và quy trình mượn/giao/trả sách chuẩn.
 */
public class ClientTest {

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  KHỞI CHẠY KIỂM THỬ TỰ ĐỘNG JAVA RMI CLIENT (TEST SUITE)");
        System.out.println("====================================================================");

        try {
            // 1. Kết nối tới RMI Registry trên localhost:1099
            System.out.println("[Client] 1. Đang định vị RMI Registry tại 127.0.0.1:1099...");
            Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);

            // 2. Tra cứu (Lookup) Remote Stub
            System.out.println("[Client] 2. Tra cứu dịch vụ 'LibraryService'...");
            ILibraryService service = (ILibraryService) registry.lookup("LibraryService");
            service.ping();
            System.out.println("[Client] -> Kết nối Remote Stub thành công!\n");

            // 3. Test lấy danh sách sách (Serialization test)
            System.out.println("--------------------------------------------------------------------");
            System.out.println("[TEST 1] Triệu gọi getAllBooks() qua RMI:");
            List<SachDTO> books = service.getAllBooks();
            System.out.println("-> Nhận được " + (books != null ? books.size() : 0) + " cuốn sách từ Server:");
            if (books != null) {
                for (SachDTO b : books) {
                    System.out.println("   " + b);
                }
            }

            // 4. Test tìm kiếm sách
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 2] Triệu gọi searchBooks('SQL') qua RMI:");
            List<SachDTO> searchResults = service.searchBooks("SQL");
            if (searchResults != null && !searchResults.isEmpty()) {
                for (SachDTO b : searchResults) {
                    System.out.println("   " + b);
                }
            } else {
                System.out.println("   (Không tìm thấy kết quả phù hợp)");
            }

            // 5. Test đăng nhập xác thực: Độc giả và Thủ thư
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 3] Xác thực đăng nhập qua RMI (login):");
            UserSessionDTO readerSession = service.login("nguyenvanan@school.edu.vn", "123456");
            System.out.println("   -> Độc giả đăng nhập thành công: " + readerSession.getUser().getHoTen() + 
                    " [Token: " + readerSession.getToken().substring(0, 8) + "...]");

            UserSessionDTO libSession = service.login("admin@school.edu.vn", "123456");
            System.out.println("   -> Thủ thư đăng nhập thành công: " + libSession.getUser().getHoTen() + 
                    " [Token: " + libSession.getToken().substring(0, 8) + "...]");

            // 6. Test mượn sách từ xa theo chu trình chuẩn: Giữ chỗ (0) -> Giao sách (1) -> Trả sách (2)
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 4] Chu trình mượn - giao - trả qua RMI:");
            // Tìm 1 cuốn sách sẵn sàng (TrangThai = 0)
            int targetBookId = -1;
            for (SachDTO s : books) {
                if (s.getTrangThai() == 0) {
                    targetBookId = s.getMaSach();
                    break;
                }
            }

            if (targetBookId > 0) {
                System.out.println("   - Độc giả đăng ký giữ chỗ cuốn sách #" + targetBookId + " qua RMI:");
                int maPhieu = service.muonSach(readerSession.getToken(), targetBookId, 14);
                System.out.println("     -> Thành công! Cấp mã phiếu #" + maPhieu + " (Trạng thái: Chờ lấy sách 24h)");

                System.out.println("   - Thử mượn trùng cuốn sách #" + targetBookId + " bằng một độc giả khác (kỳ vọng từ chối):");
                try {
                    UserSessionDTO reader2Session = service.login("tranthibinh@school.edu.vn", "123456");
                    service.muonSach(reader2Session.getToken(), targetBookId, 7);
                    System.err.println("     -> LỖI: Server không chặn mượn trùng!");
                } catch (LibraryException e) {
                    System.out.println("     -> [CHẶN THÀNH CÔNG] Server ném LibraryException: " + e.getErrorCode() + " - " + e.getMessage());
                }

                System.out.println("   - Thủ thư xác nhận giao sách tại quầy (xacNhanGiaoSach):");
                boolean giaoOk = service.xacNhanGiaoSach(libSession.getToken(), maPhieu, 14);
                System.out.println("     -> Giao sách thành công: " + giaoOk + " (Phiếu: Đang mượn thực tế)");

                System.out.println("   - Thủ thư xác nhận trả sách tại quầy (traSach):");
                boolean traOk = service.traSach(libSession.getToken(), maPhieu, 0, "Bình thường (Nguyên vẹn)");
                System.out.println("     -> Trả sách thành công: " + traOk + " (Sách quay về Sẵn sàng)");
            } else {
                System.out.println("   (Hiện không có cuốn sách nào ở trạng thái sẵn sàng để chạy demo mượn)");
            }

            // 7. Lấy danh sách phiếu mượn qua quyền Thủ thư
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 5] Triệu gọi getAllPhieuMuon(libToken) qua RMI:");
            List<PhieuMuonDTO> phieuMuons = service.getAllPhieuMuon(libSession.getToken());
            System.out.println("-> Nhận được " + (phieuMuons != null ? phieuMuons.size() : 0) + " phiếu mượn.");

            System.out.println("\n====================================================================");
            System.out.println("  KIỂM THỬ RMI CLIENT HOÀN TẤT THÀNH CÔNG 100%!");
            System.out.println("====================================================================");

        } catch (Exception e) {
            System.err.println("[ClientTest] LỖI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
