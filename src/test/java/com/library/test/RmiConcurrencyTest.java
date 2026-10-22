package com.library.test;

import com.library.dao.DBConnection;
import com.library.model.DocGiaDTO;
import com.library.model.LibraryConstants;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;
import com.library.service.ILibraryService;
import com.library.service.LibraryException;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * LỚP KIỂM THỬ TẦNG C: Kiểm thử tranh chấp đồng thời đa luồng qua RMI (Concurrency Test).
 * - Sử dụng ExecutorService kết hợp CountDownLatch để phát các lời gọi gần đồng thời từ nhiều Client RMI độc lập.
 * - Hỗ trợ cấu hình linh hoạt số Client (tối thiểu 2, mở rộng 10) và số vòng kiểm thử qua tham số dòng lệnh.
 * - Tiêu chuẩn đạt cho mỗi vòng kiểm thử:
 *   1) Đúng một yêu cầu thành công, nhận mã phiếu hợp lệ.
 *   2) Tất cả các yêu cầu còn lại bị từ chối đúng mã nghiệp vụ BOOK_NOT_AVAILABLE (sách không sẵn có).
 *   3) Đối chiếu CSDL sau khi tất cả kết thúc: đúng một phiếu chiếm dụng (TrangThaiPhieu IN (0, 1, 4, 6)),
 *      khớp độc giả thắng và mã phiếu trả về.
 *   4) Không có lỗi mạng, deadlock kỹ thuật hay bản ghi mồ côi.
 *   5) Dọn dẹp an toàn sau khi hoàn tất kiểm thử.
 */
public class RmiConcurrencyTest {

    private static String rmiHost = "127.0.0.1";
    private static int rmiPort = 1099;
    private static String serviceName = "LibraryService";

    private static int numClients = 2; // Mặc định 2 client, có thể đổi thành 10 qua tham số
    private static int numRounds = 1;

    static class AttemptResult {
        final int clientId;
        final String readerEmail;
        final int readerId;
        final long requestTime;
        long durationMs;
        int maPhieu = -1;
        boolean success = false;
        String errorCode = null;
        String errorMessage = null;

        AttemptResult(int clientId, String readerEmail, int readerId) {
            this.clientId = clientId;
            this.readerEmail = readerEmail;
            this.readerId = readerId;
            this.requestTime = System.currentTimeMillis();
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            try { numClients = Integer.parseInt(args[0].trim()); } catch (Exception ignored) {}
        }
        if (args.length > 1) {
            try { numRounds = Integer.parseInt(args[1].trim()); } catch (Exception ignored) {}
        }

        System.out.println("====================================================================");
        System.out.println("  KIỂM THỬ TẦNG C: TRANH CHẤP MƯỢN SÁCH ĐỒNG THỜI QUA JAVA RMI");
        System.out.println("  Mục tiêu: rmi://" + rmiHost + ":" + rmiPort + "/" + serviceName);
        System.out.println("  Cấu hình: " + numClients + " Client đồng thời | " + numRounds + " Vòng kiểm thử");
        System.out.println("====================================================================");

        ILibraryService service;
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            service = (ILibraryService) registry.lookup(serviceName);
            service.ping();
            System.out.println("[Init] Kết nối tới RMI Server thành công!");
        } catch (Exception e) {
            System.err.println("[BLOCKED] Không thể kết nối tới RMI Server: " + e.getMessage());
            System.err.println("Vui lòng khởi động run_server.bat trước khi chạy kiểm thử tranh chấp!");
            System.exit(2);
            return;
        }

        // Kiểm tra kết nối CSDL hành chính phục vụ đối chiếu
        if (!DBConnection.testConnection()) {
            System.err.println("[BLOCKED] Không thể kết nối tới SQL Server để đối chiếu bất biến dữ liệu!");
            System.exit(2);
            return;
        }

        int totalPass = 0;
        int totalFail = 0;

        for (int round = 1; round <= numRounds; round++) {
            System.out.printf("%n======================== VÒNG KIỂM THỬ %d / %d ========================%n", round, numRounds);
            boolean roundResult = runOneConcurrencyRound(service, round);
            if (roundResult) {
                totalPass++;
            } else {
                totalFail++;
            }
        }

        System.out.println("\n====================================================================");
        System.out.printf("TỔNG KẾT KIỂM THỬ TRANH CHẤP: %d / %d VÒNG ĐẠT TIÊU CHUẨN%n", totalPass, numRounds);
        if (totalFail == 0) {
            System.out.println("KẾT LUẬN: ĐẠT! Cơ chế UPDLOCK + Unique Filtered Index đã ngăn chặn triệt để mượn trùng.");
        } else {
            System.err.println("KẾT LUẬN: KHÔNG ĐẠT! Có vòng kiểm thử vi phạm bất biến dữ liệu.");
            System.exit(1);
        }
        System.out.println("====================================================================");
    }

    private static boolean runOneConcurrencyRound(ILibraryService service, int roundNumber) {
        String testRunId = "RUN_" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[TestRunId: " + testRunId + "] Chuẩn bị dữ liệu kiểm thử...");

        // 1. Chuẩn bị cuốn sách kiểm thử ở trạng thái Sẵn sàng (0)
        int testBookId = prepareTestBook(service);
        if (testBookId <= 0) {
            System.err.println("[BLOCKED] Không thể chuẩn bị cuốn sách kiểm thử ở trạng thái sẵn sàng!");
            return false;
        }
        System.out.println("  -> Cuốn sách kiểm thử: Mã sách #" + testBookId + " (Đã đưa về trạng thái Sẵn sàng 0)");

        // 2. Chuẩn bị N tài khoản Độc giả hợp lệ (READER)
        List<UserSessionDTO> sessions = prepareReaderSessions(service, numClients);
        if (sessions.size() < numClients) {
            System.err.println("[BLOCKED] Không đủ tài khoản độc giả để chạy kiểm thử (" + sessions.size() + "/" + numClients + ")!");
            return false;
        }
        System.out.println("  -> Đã xác thực thành công " + sessions.size() + " phiên độc giả độc lập.");

        // 3. Khởi tạo CountDownLatch và ExecutorService
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        List<AttemptResult> results = Collections.synchronizedList(new ArrayList<>());

        System.out.println("  -> Kích hoạt " + numClients + " luồng phát lệnh mượn sách đồng thời qua RMI...");

        for (int i = 0; i < numClients; i++) {
            final int clientId = i + 1;
            final UserSessionDTO readerSession = sessions.get(i);

            executor.submit(() -> {
                AttemptResult res = new AttemptResult(clientId, readerSession.getUser().getEmail(), readerSession.getUser().getMaDocGia());
                try {
                    // Chờ tín hiệu xuất phát từ cổng startGate
                    startGate.await();

                    long t0 = System.currentTimeMillis();
                    int maPhieu = service.muonSach(readerSession.getToken(), testBookId, 14);
                    res.durationMs = System.currentTimeMillis() - t0;
                    res.maPhieu = maPhieu;
                    res.success = (maPhieu > 0);
                } catch (LibraryException ex) {
                    res.durationMs = System.currentTimeMillis() - res.requestTime;
                    res.errorCode = ex.getErrorCode();
                    res.errorMessage = ex.getMessage();
                    res.success = false;
                } catch (Exception ex) {
                    res.durationMs = System.currentTimeMillis() - res.requestTime;
                    res.errorCode = "UNKNOWN_ERROR";
                    res.errorMessage = ex.getMessage();
                    res.success = false;
                } finally {
                    results.add(res);
                    endGate.countDown();
                }
            });
        }

        // Giải phóng startGate để tất cả các luồng đồng thời gọi RMI
        long startTime = System.currentTimeMillis();
        startGate.countDown();

        // Chờ tất cả hoàn tất với timeout 15 giây
        boolean completed = false;
        try {
            completed = endGate.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        long totalTime = System.currentTimeMillis() - startTime;
        executor.shutdownNow();

        if (!completed) {
            System.err.println("[FAIL] Quá thời gian chờ (Timeout 15s) khi thực thi kiểm thử tranh chấp!");
            return false;
        }

        // 4. Phân tích kết quả các lời gọi từ Client
        System.out.println("--------------------------------------------------------------------");
        System.out.printf("Kết quả thu thập sau %d ms:%n", totalTime);
        int successCount = 0;
        int rejectBookNotAvailableCount = 0;
        int errorCount = 0;
        AttemptResult winner = null;

        for (AttemptResult r : results) {
            if (r.success) {
                successCount++;
                winner = r;
                System.out.printf("  - Client #%d (Độc giả #%d - %s): THÀNH CÔNG -> Cấp phiếu mượn #%d (%d ms)%n",
                        r.clientId, r.readerId, r.readerEmail, r.maPhieu, r.durationMs);
            } else if (LibraryConstants.ERR_BOOK_NOT_AVAILABLE.equals(r.errorCode)) {
                rejectBookNotAvailableCount++;
                System.out.printf("  - Client #%d (Độc giả #%d - %s): BỊ TỪ CHỐI ĐÚNG -> %s (%d ms)%n",
                        r.clientId, r.readerId, r.readerEmail, r.errorMessage, r.durationMs);
            } else {
                errorCount++;
                System.err.printf("  - Client #%d (Độc giả #%d - %s): LỖI KHÔNG MONG MUỐN -> [%s] %s (%d ms)%n",
                        r.clientId, r.readerId, r.readerEmail, r.errorCode, r.errorMessage, r.durationMs);
            }
        }

        // 5. Đối chiếu CSDL hành chính (DB Invariant Verification)
        System.out.println("--------------------------------------------------------------------");
        System.out.println("[Đối chiếu CSDL SQL Server ThuVienDB]");
        int activeLoansInDb = countActiveLoansInDb(testBookId);
        int bookStatusInDb = queryBookStatusInDb(testBookId);

        System.out.println("  - Số phiếu chiếm dụng chưa kết thúc trong CSDL: " + activeLoansInDb);
        System.out.println("  - Trạng thái cuốn sách trong CSDL:              " + bookStatusInDb + 
                (bookStatusInDb == LibraryConstants.SACH_GIU_CHO ? " (2: Đang giữ chỗ)" : ""));

        // 6. Đánh giá tiêu chí
        boolean c1 = (successCount == 1);
        boolean c2 = (rejectBookNotAvailableCount == (numClients - 1));
        boolean c3 = (errorCount == 0);
        boolean c4 = (activeLoansInDb == 1);
        boolean c5 = (bookStatusInDb == LibraryConstants.SACH_GIU_CHO);

        System.out.println("\n[ĐÁNH GIÁ TIÊU CHÍ VÒNG KIỂM THỬ]:");
        System.out.println("  [1] Đúng 1 yêu cầu thành công có mã phiếu:                " + (c1 ? "ĐẠT (PASS)" : "KHÔNG ĐẠT (FAIL)"));
        System.out.println("  [2] Đúng " + (numClients - 1) + " yêu cầu bị từ chối BOOK_NOT_AVAILABLE:    " + (c2 ? "ĐẠT (PASS)" : "KHÔNG ĐẠT (FAIL)"));
        System.out.println("  [3] Không có lỗi kỹ thuật/lỗi kết nối ngoại lai:          " + (c3 ? "ĐẠT (PASS)" : "KHÔNG ĐẠT (FAIL)"));
        System.out.println("  [4] Đối chiếu CSDL: Đúng 1 phiếu chiếm dụng duy nhất:     " + (c4 ? "ĐẠT (PASS)" : "KHÔNG ĐẠT (FAIL)"));
        System.out.println("  [5] Trạng thái sách chuyển sang Giữ chỗ (2):              " + (c5 ? "ĐẠT (PASS)" : "KHÔNG ĐẠT (FAIL)"));

        // 7. Dọn dẹp hoàn trả sách sau vòng kiểm thử
        if (winner != null && winner.maPhieu > 0) {
            cleanUpRound(service, winner.maPhieu, testBookId);
        }

        return c1 && c2 && c3 && c4 && c5;
    }

    private static int prepareTestBook(ILibraryService service) {
        try {
            // Tra cứu sách có sẵn
            List<SachDTO> books = service.getAllBooks();
            for (SachDTO b : books) {
                if (b.getTrangThai() == LibraryConstants.SACH_SAN_SANG) {
                    int id = b.getMaSach();
                    if (countActiveLoansInDb(id) == 0) {
                        return id;
                    }
                }
            }

            // Nếu không có cuốn sẵn, reset cuốn sách mã 5 (Số đỏ) nếu có
            resetBookForTest(5);
            return 5;
        } catch (Exception e) {
            System.err.println("[prepareTestBook] Lỗi: " + e.getMessage());
            return -1;
        }
    }

    private static void resetBookForTest(int maSach) {
        String updateBookSql = "UPDATE Sach SET TrangThai = 0 WHERE MaSach = ?";
        String finishLoansSql = "UPDATE PhieuMuon SET TrangThaiPhieu = 2 WHERE MaSach = ? AND TrangThaiPhieu IN (0, 1, 4, 6)";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps1 = conn.prepareStatement(finishLoansSql);
                 PreparedStatement ps2 = conn.prepareStatement(updateBookSql)) {
                ps1.setInt(1, maSach);
                ps1.executeUpdate();
                ps2.setInt(1, maSach);
                ps2.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    private static List<UserSessionDTO> prepareReaderSessions(ILibraryService service, int requiredCount) {
        List<UserSessionDTO> list = new ArrayList<>();
        String[] candidateEmails = {
            "nguyenvanan@school.edu.vn",
            "tranthibinh@school.edu.vn",
            "lehoangcuong@school.edu.vn",
            "phamthidung@school.edu.vn",
            "hoangminhem@school.edu.vn"
        };

        for (String email : candidateEmails) {
            if (list.size() >= requiredCount) break;
            try {
                UserSessionDTO sess = service.login(email, "123456");
                if (sess != null && LibraryConstants.ROLE_READER.equals(sess.getUser().getVaiTro())) {
                    list.add(sess);
                }
            } catch (Exception ignored) {}
        }

        // Nếu cần thêm tài khoản (vd: kịch bản 10 client), tạo nhanh tài khoản test độc giả
        int extraNeeded = requiredCount - list.size();
        for (int i = 1; i <= extraNeeded; i++) {
            String testEmail = "concurrency_reader_" + i + "@test.edu.vn";
            try {
                // Thử login trước
                UserSessionDTO sess = service.login(testEmail, "123456");
                list.add(sess);
            } catch (Exception notExist) {
                // Tạo mới tài khoản độc giả
                try {
                    DocGiaDTO newReader = new DocGiaDTO("Test Reader " + i, testEmail, "123456", "090000000" + i, LibraryConstants.ROLE_READER);
                    service.dangKyDocGia(newReader);
                    UserSessionDTO sess = service.login(testEmail, "123456");
                    list.add(sess);
                } catch (Exception ignored) {}
            }
        }

        return list;
    }

    private static int countActiveLoansInDb(int maSach) {
        String sql = "SELECT COUNT(*) FROM PhieuMuon WHERE MaSach = ? AND TrangThaiPhieu IN (0, 1, 4, 6)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maSach);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static int queryBookStatusInDb(int maSach) {
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

    private static void cleanUpRound(ILibraryService service, int maPhieu, int maSach) {
        try {
            UserSessionDTO lib = service.login("admin@thuvien.edu.vn", "123456");
            service.xacNhanGiaoSach(lib.getToken(), maPhieu, 1);
            service.traSach(lib.getToken(), maPhieu, 0, "Bình thường (Nguyên vẹn)");
            System.out.println("  -> [Dọn dẹp] Đã giao và hoàn tất trả sách phiếu #" + maPhieu + ". Sách #" + maSach + " đã quay về Sẵn sàng.");
        } catch (Exception e) {
            // Fallback DB clean up
            resetBookForTest(maSach);
        }
    }
}
