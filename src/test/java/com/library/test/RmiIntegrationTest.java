package com.library.test;

import com.library.dao.DBConnection;
import com.library.model.DocGiaDTO;
import com.library.model.LibraryConstants;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;
import com.library.service.ILibraryService;
import com.library.service.LibraryException;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * LỚP KIỂM THỬ TẦNG B: RMI Integration & Real SQL Server Test (Bài 9 - Java RMI).
 * - Kết nối trực tiếp tới RMI Registry thật (cổng 1099) và SQL Server thật (ThuVienDB).
 * - Không sử dụng HTTP, chứng minh đầy đủ lời gọi Remote Stub thật.
 * - Kiểm tra xác thực, phân quyền tại tầng RMI, và chu trình 6 trạng thái mượn/trả sách.
 */
public class RmiIntegrationTest {

    private static final String RMI_HOST = System.getProperty("rmi.host", "127.0.0.1");
    private static final int RMI_PORT = Integer.getInteger("rmi.port", 1099);
    private static final String SERVICE_NAME = System.getProperty("rmi.service", "LibraryService");

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  KIỂM THỬ TẦNG B: TÍCH HỢP RMI THẬT & SQL SERVER THẬT (BÀI 9)");
        System.out.println("  Mục tiêu: rmi://" + RMI_HOST + ":" + RMI_PORT + "/" + SERVICE_NAME);
        System.out.println("====================================================================");

        int passed = 0;
        int failed = 0;

        ILibraryService service;

        // 1. Kiểm tra kết nối RMI Registry & Ping
        try {
            Registry registry = LocateRegistry.getRegistry(RMI_HOST, RMI_PORT);
            service = (ILibraryService) registry.lookup(SERVICE_NAME);
            boolean pingOk = service.ping();
            if (pingOk) {
                System.out.println("[PASS] Ca 1: Kết nối Remote Stub và triệu gọi ping() thành công.");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 1: ping() trả về false!");
                failed++;
                return;
            }
        } catch (Exception e) {
            System.err.println("[BLOCKED] Không thể kết nối tới RMI Server: " + e.getMessage());
            System.err.println("Vui lòng khởi động run_server.bat trước khi chạy kiểm thử tích hợp!");
            System.exit(2);
            return;
        }

        // 2. Kiểm tra xác thực (Đúng & Sai)
        UserSessionDTO readerSession = null;
        UserSessionDTO libSession = null;
        try {
            // Đăng nhập sai mật khẩu -> Phải ném LibraryException UNAUTHORIZED
            try {
                service.login("nguyenvanan@school.edu.vn", "sai_mat_khau_123");
                System.err.println("[FAIL] Ca 2a: Đăng nhập sai mật khẩu nhưng không bị từ chối!");
                failed++;
            } catch (LibraryException ex) {
                if (LibraryConstants.ERR_UNAUTHORIZED.equals(ex.getErrorCode())) {
                    System.out.println("[PASS] Ca 2a: Chặn đăng nhập sai mật khẩu thành công (Mã lỗi: " + ex.getErrorCode() + ").");
                    passed++;
                } else {
                    System.err.println("[FAIL] Ca 2a: Mã lỗi không đúng: " + ex.getErrorCode());
                    failed++;
                }
            }

            // Đăng nhập đúng Độc giả
            readerSession = service.login("nguyenvanan@school.edu.vn", "123456");
            if (readerSession != null && LibraryConstants.ROLE_READER.equals(readerSession.getUser().getVaiTro())) {
                System.out.println("[PASS] Ca 2b: Đăng nhập Độc giả thành công (Token: " + readerSession.getToken().substring(0, 8) + "...).");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 2b: Độc giả đăng nhập thất bại!");
                failed++;
            }

            // Đăng nhập đúng Thủ thư
            libSession = service.login("admin@school.edu.vn", "123456");
            if (libSession != null && LibraryConstants.ROLE_LIBRARIAN.equals(libSession.getUser().getVaiTro())) {
                System.out.println("[PASS] Ca 2c: Đăng nhập Thủ thư thành công (Vai trò: LIBRARIAN).");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 2c: Thủ thư đăng nhập thất bại!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 2: Ngoại lệ không mong muốn: " + e.getMessage());
            failed++;
        }

        // 3. Kiểm tra phân quyền RMI Server (Authorization Test)
        try {
            // Độc giả gọi phương thức của Thủ thư -> Phải ném LibraryException FORBIDDEN
            try {
                service.xacNhanGiaoSach(readerSession.getToken(), 99999, 14);
                System.err.println("[FAIL] Ca 3a: Độc giả gọi hàm Thủ thư nhưng Server không chặn!");
                failed++;
            } catch (LibraryException ex) {
                if (LibraryConstants.ERR_FORBIDDEN.equals(ex.getErrorCode())) {
                    System.out.println("[PASS] Ca 3a: Chặn độc giả gọi phương thức Thủ thư thành công (Mã lỗi: FORBIDDEN).");
                    passed++;
                } else {
                    System.err.println("[FAIL] Ca 3a: Nhận mã lỗi khác: " + ex.getErrorCode());
                    failed++;
                }
            }

            // Token rác hoặc hết hạn -> Phải ném UNAUTHORIZED
            try {
                service.muonSach("token_fake_khong_hop_le", 1, 14);
                System.err.println("[FAIL] Ca 3b: Token rác không bị từ chối!");
                failed++;
            } catch (LibraryException ex) {
                if (LibraryConstants.ERR_UNAUTHORIZED.equals(ex.getErrorCode())) {
                    System.out.println("[PASS] Ca 3b: Chặn token không hợp lệ thành công (Mã lỗi: UNAUTHORIZED).");
                    passed++;
                } else {
                    System.err.println("[FAIL] Ca 3b: Nhận mã lỗi khác: " + ex.getErrorCode());
                    failed++;
                }
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 3: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        // 4. Kiểm tra tra cứu sách qua RMI
        int testBookId = -1;
        try {
            List<SachDTO> books = service.getAllBooks();
            if (books != null && !books.isEmpty()) {
                System.out.println("[PASS] Ca 4a: getAllBooks() trả về " + books.size() + " cuốn sách.");
                passed++;

                // Tìm cuốn sách sẵn sàng (TrangThai = 0)
                for (SachDTO b : books) {
                    if (b.getTrangThai() == LibraryConstants.SACH_SAN_SANG) {
                        testBookId = b.getMaSach();
                        break;
                    }
                }
            } else {
                System.err.println("[FAIL] Ca 4a: Không lấy được danh sách sách!");
                failed++;
            }

            List<SachDTO> search = service.searchBooks("Java");
            if (search != null) {
                System.out.println("[PASS] Ca 4b: searchBooks('Java') trả về " + search.size() + " kết quả.");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 4b: searchBooks trả về null!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 4: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        // 5. Kiểm thử toàn vẹn chu trình mượn - giao - trả qua RMI & đối chiếu CSDL thật
        if (testBookId > 0 && readerSession != null && libSession != null) {
            try {
                System.out.println("\n--- BẮT ĐẦU CHU TRÌNH MƯỢN/TRẢ SÁCH VỚI CUỐN SÁCH #" + testBookId + " ---");

                // Bước 1: Độc giả đăng ký giữ chỗ (Trạng thái phiếu: 0, Sách: 2)
                int maPhieu = service.muonSach(readerSession.getToken(), testBookId, 14);
                if (maPhieu > 0) {
                    // Đối chiếu CSDL
                    int dbSachStatus = queryBookStatus(testBookId);
                    int dbLoanStatus = queryLoanStatus(maPhieu);

                    if (dbSachStatus == LibraryConstants.SACH_GIU_CHO && dbLoanStatus == LibraryConstants.PHIEU_CHO_LAY) {
                        System.out.println("[PASS] Ca 5a: Đăng ký giữ chỗ thành công (Mã phiếu #" + maPhieu + 
                                ", DB Sach=" + dbSachStatus + ", DB Phieu=" + dbLoanStatus + ").");
                        passed++;
                    } else {
                        System.err.println("[FAIL] Ca 5a: Trạng thái DB không khớp: Sach=" + dbSachStatus + ", Phieu=" + dbLoanStatus);
                        failed++;
                    }

                    // Bước 2: Độc giả khác thử mượn trùng cuốn sách đang giữ chỗ -> BẮT BUỘC BỊ TỪ CHỐI
                    UserSessionDTO reader2 = service.login("tranthibinh@school.edu.vn", "123456");
                    try {
                        service.muonSach(reader2.getToken(), testBookId, 7);
                        System.err.println("[FAIL] Ca 5b: LỖI NGHIÊM TRỌNG: Cho phép mượn trùng cuốn sách đang được giữ chỗ!");
                        failed++;
                    } catch (LibraryException ex) {
                        if (LibraryConstants.ERR_BOOK_NOT_AVAILABLE.equals(ex.getErrorCode())) {
                            System.out.println("[PASS] Ca 5b: Chặn mượn trùng cuốn sách đang giữ chỗ thành công (Mã lỗi: BOOK_NOT_AVAILABLE).");
                            passed++;
                        } else {
                            System.err.println("[FAIL] Ca 5b: Nhận mã lỗi không đúng: " + ex.getErrorCode());
                            failed++;
                        }
                    }

                    // Bước 3: Thủ thư xác nhận giao sách tại quầy -> Phiếu chuyển sang 1, Sách chuyển sang 1
                    boolean giaoOk = service.xacNhanGiaoSach(libSession.getToken(), maPhieu, 14);
                    int dbSachStatusAfterHandover = queryBookStatus(testBookId);
                    int dbLoanStatusAfterHandover = queryLoanStatus(maPhieu);

                    if (giaoOk && dbSachStatusAfterHandover == LibraryConstants.SACH_DANG_MUON &&
                        dbLoanStatusAfterHandover == LibraryConstants.PHIEU_DANG_MUON) {
                        System.out.println("[PASS] Ca 5c: Thủ thư xác nhận giao sách thành công (Sach=" + 
                                dbSachStatusAfterHandover + ", Phieu=" + dbLoanStatusAfterHandover + ").");
                        passed++;
                    } else {
                        System.err.println("[FAIL] Ca 5c: Xác nhận giao sách thất bại!");
                        failed++;
                    }

                    // Bước 4: Độc giả gửi yêu cầu trả sách -> Phiếu chuyển sang 6 (Chờ duyệt trả)
                    boolean reqReturnOk = service.yeuCauTraSach(readerSession.getToken(), maPhieu);
                    int dbLoanStatusAfterReq = queryLoanStatus(maPhieu);
                    if (reqReturnOk && dbLoanStatusAfterReq == LibraryConstants.PHIEU_CHO_XAC_NHAN_TRA) {
                        System.out.println("[PASS] Ca 5d: Độc giả gửi yêu cầu trả sách thành công (Phieu=" + dbLoanStatusAfterReq + ").");
                        passed++;
                    } else {
                        System.err.println("[FAIL] Ca 5d: Yêu cầu trả sách thất bại!");
                        failed++;
                    }

                    // Bước 5: Thủ thư xác nhận nhận sách tại quầy -> Phiếu chuyển sang 2 (Đã trả), Sách về 0 (Sẵn sàng)
                    boolean traOk = service.traSach(libSession.getToken(), maPhieu, 0, "Bình thường (Nguyên vẹn)");
                    int dbSachFinal = queryBookStatus(testBookId);
                    int dbLoanFinal = queryLoanStatus(maPhieu);

                    if (traOk && dbSachFinal == LibraryConstants.SACH_SAN_SANG &&
                        dbLoanFinal == LibraryConstants.PHIEU_DA_TRA) {
                        System.out.println("[PASS] Ca 5e: Thủ thư xác nhận trả sách thành công (Sach=" + dbSachFinal + 
                                " - Sẵn sàng, Phieu=" + dbLoanFinal + " - Đã kết thúc).");
                        passed++;
                    } else {
                        System.err.println("[FAIL] Ca 5e: Xác nhận trả sách thất bại!");
                        failed++;
                    }

                } else {
                    System.err.println("[FAIL] Ca 5: muonSach trả về mã phiếu không hợp lệ: " + maPhieu);
                    failed++;
                }
            } catch (Exception e) {
                System.err.println("[FAIL] Ca 5: Ngoại lệ chu trình mượn/trả: " + e.getMessage());
                e.printStackTrace();
                failed++;
            }
        } else {
            System.out.println("[SKIP] Ca 5: Bỏ qua do không tìm thấy cuốn sách sẵn sàng hoặc tài khoản chưa sẵn sàng.");
        }

        System.out.println("--------------------------------------------------------------------");
        System.out.printf("TỔNG KẾT TẦNG B: %d PASS | %d FAIL (Tổng cộng các ca kiểm thử tích hợp)%n", passed, failed);
        System.out.println("====================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int queryBookStatus(int maSach) {
        String sql = "SELECT TrangThai FROM Sach WHERE MaSach = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maSach);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return -999;
    }

    private static int queryLoanStatus(int maPhieu) {
        String sql = "SELECT TrangThaiPhieu FROM PhieuMuon WHERE MaPhieu = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maPhieu);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return -999;
    }
}
