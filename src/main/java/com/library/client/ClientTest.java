package com.library.client;

import com.library.model.DocGiaDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.service.ILibraryService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Lớp ClientTest mô phỏng một Client từ xa kết nối qua Java RMI (Bài 9)
 * để kiểm thử tính tuần tự hóa (Serialization - Bài 6) và các nghiệp vụ từ xa.
 */
public class ClientTest {

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  KHỞI CHẠY KIỂM THỬ TỰ ĐỘNG JAVA RMI CLIENT (TEST SUITE)");
        System.out.println("====================================================================");

        try {
            // 1. Kết nối tới RMI Registry trên localhost:1099
            System.out.println("[Client] 1. Đang định vị RMI Registry tại localhost:1099...");
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            // 2. Tra cứu (Lookup) Remote Stub
            System.out.println("[Client] 2. Tra cứu dịch vụ 'LibraryService'...");
            ILibraryService service = (ILibraryService) registry.lookup("LibraryService");
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

            // 5. Test lấy danh sách độc giả
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 3] Triệu gọi getAllDocGia() qua RMI:");
            List<DocGiaDTO> readers = service.getAllDocGia();
            if (readers != null) {
                for (DocGiaDTO d : readers) {
                    System.out.println("   " + d);
                }
            }

            // 6. Test mượn sách từ xa qua Stored Procedure sp_KiemTraVaMuonSach
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 4] Triệu gọi muonSach(maDocGia=1, maSach=4, soNgayMuon=14):");
            int maPhieuMoi = -1;
            try {
                maPhieuMoi = service.muonSach(1, 4, 14);
                System.out.println("   -> MƯỢN SÁCH THÀNH CÔNG! Mã phiếu mượn vừa sinh: " + maPhieuMoi);
            } catch (Exception ex) {
                System.out.println("   -> Thông báo: " + ex.getMessage());
            }

            // 7. Test kiểm tra chống mượn trùng (Race Condition qua RMI)
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 5] Kiểm thử mượn trùng cuốn sách vừa mượn (Mã sách 4, Độc giả 2):");
            try {
                service.muonSach(2, 4, 7);
                System.err.println("   -> LỖI: Server lẽ ra phải từ chối!");
            } catch (Exception ex) {
                System.out.println("   -> [CHẶN THÀNH CÔNG] Client nhận được ngoại lệ từ xa: " + ex.getMessage());
            }

            // 8. Test trả sách từ xa qua sp_TraSach
            if (maPhieuMoi > 0) {
                System.out.println("\n--------------------------------------------------------------------");
                System.out.println("[TEST 6] Triệu gọi traSach(maPhieu=" + maPhieuMoi + ") qua RMI:");
                boolean traOk = service.traSach(maPhieuMoi);
                System.out.println("   -> TRẢ SÁCH THÀNH CÔNG: " + traOk);
            }

            // 9. Lấy danh sách phiếu mượn
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("[TEST 7] Triệu gọi getAllPhieuMuon() qua RMI:");
            List<PhieuMuonDTO> phieuMuons = service.getAllPhieuMuon();
            if (phieuMuons != null) {
                for (PhieuMuonDTO pm : phieuMuons) {
                    System.out.println("   " + pm);
                }
            }

            System.out.println("\n====================================================================");
            System.out.println("  TẤT CẢ CÁC CA KIỂM THỬ RMI & SERIALIZATION ĐÃ HOÀN TẤT!");
            System.out.println("====================================================================");

        } catch (Exception e) {
            System.err.println("[ClientTest] Lỗi ngoại lệ trong quá trình kiểm thử: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
