package com.library.client;

import com.library.model.DocGiaDTO;
import com.library.model.LibraryConstants;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;
import com.library.service.ILibraryService;
import com.library.service.LibraryException;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Ứng dụng Java Console Client gọi trực tiếp RMI (Bài 9 - Java RMI).
 * - Kết nối độc lập với Web Gateway (Browser/HTTP).
 * - Sử dụng Java Serialization (Bài 6) để truyền/nhận các DTO từ xa.
 * - Hỗ trợ đầy đủ phân quyền theo vai trò (Độc giả READER vs Thủ thư LIBRARIAN).
 * - Cấu hình linh hoạt Host, Port qua tham số dòng lệnh hoặc biến môi trường.
 */
public class ClientMain {

    private static String rmiHost = "127.0.0.1";
    private static int rmiPort = 1099;
    private static String serviceName = "LibraryService";

    private static ILibraryService service;
    private static UserSessionDTO currentSession = null;
    private static final Scanner scanner = new Scanner(System.in, "UTF-8");

    public static void main(String[] args) {
        if (args.length > 0 && !args[0].trim().isEmpty()) {
            rmiHost = args[0].trim();
        } else if (System.getProperty("rmi.host") != null) {
            rmiHost = System.getProperty("rmi.host");
        }

        if (args.length > 1 && !args[1].trim().isEmpty()) {
            try { rmiPort = Integer.parseInt(args[1].trim()); } catch (Exception ignored) {}
        } else if (System.getProperty("rmi.port") != null) {
            try { rmiPort = Integer.parseInt(System.getProperty("rmi.port")); } catch (Exception ignored) {}
        }

        if (args.length > 2 && !args[2].trim().isEmpty()) {
            serviceName = args[2].trim();
        }

        System.out.println("====================================================================");
        System.out.println("  JAVA RMI CONSOLE CLIENT - REMOTE SCHOOL LIBRARY MANAGEMENT");
        System.out.println("  Học phần: CMP180 - Lập trình mạng máy tính");
        System.out.println("====================================================================");
        System.out.println("  Kết nối tới RMI Registry: " + rmiHost + ":" + rmiPort + " | Service: " + serviceName);

        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            service = (ILibraryService) registry.lookup(serviceName);
            service.ping();
            System.out.println("  -> [THÀNH CÔNG] Đã nhận Remote Stub từ xa! Kết nối RMI sẵn sàng.");
        } catch (Exception e) {
            System.err.println("  -> [THẤT BẠI] Không thể kết nối tới RMI Server tại " + rmiHost + ":" + rmiPort);
            System.err.println("  Chi tiết lỗi: " + e.getMessage());
            System.err.println("  Vui lòng chắc chắn rằng run_server.bat đang chạy trước khi mở Client!");
            return;
        }

        boolean running = true;
        while (running) {
            try {
                if (currentSession == null) {
                    running = showGuestMenu();
                } else if (currentSession.getUser().isLibrarian()) {
                    running = showLibrarianMenu();
                } else {
                    running = showReaderMenu();
                }
            } catch (Exception e) {
                System.err.println("[Lỗi] Có ngoại lệ xảy ra: " + e.getMessage());
            }
        }

        System.out.println("Tạm biệt! Cảm ơn bạn đã sử dụng Hệ thống Quản lý Thư viện từ xa.");
    }

    private static boolean showGuestMenu() throws Exception {
        System.out.println("\n--------------------------------------------------------------------");
        System.out.println("                   MENU KHÁCH (CHƯA ĐĂNG NHẬP)");
        System.out.println("--------------------------------------------------------------------");
        System.out.println("1. Tra cứu toàn bộ danh mục sách (RMI: getAllBooks)");
        System.out.println("2. Tìm kiếm sách theo từ khóa (RMI: searchBooks)");
        System.out.println("3. Đăng nhập hệ thống (RMI: login)");
        System.out.println("4. Đăng ký tài khoản độc giả mới (RMI: dangKyDocGia)");
        System.out.println("0. Thoát chương trình");
        System.out.print("Vui lòng chọn chức năng (0-4): ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                handleViewAllBooks();
                break;
            case "2":
                handleSearchBooks();
                break;
            case "3":
                handleLogin();
                break;
            case "4":
                handleRegister();
                break;
            case "0":
                return false;
            default:
                System.out.println("Lựa chọn không hợp lệ, vui lòng thử lại!");
        }
        return true;
    }

    private static boolean showReaderMenu() throws Exception {
        DocGiaDTO user = currentSession.getUser();
        System.out.println("\n--------------------------------------------------------------------");
        System.out.println("         CỔNG ĐỘC GIẢ (RMI) - " + user.getHoTen() + " [ID #" + user.getMaDocGia() + "]");
        System.out.println("--------------------------------------------------------------------");
        System.out.println("1. Tra cứu danh mục sách");
        System.out.println("2. Tìm kiếm sách theo tên/tác giả");
        System.out.println("3. Đăng ký giữ chỗ / Mượn sách từ xa (RMI: muonSach)");
        System.out.println("4. Xem danh sách phiếu mượn của tôi (RMI: getPhieuMuonByDocGia)");
        System.out.println("5. Gửi yêu cầu trả sách (RMI: yeuCauTraSach)");
        System.out.println("6. Báo mất sách (RMI: baoMatSach)");
        System.out.println("7. Xem thông tin cá nhân & tình trạng tài khoản");
        System.out.println("0. Đăng xuất tài khoản");
        System.out.print("Vui lòng chọn chức năng (0-7): ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                handleViewAllBooks();
                break;
            case "2":
                handleSearchBooks();
                break;
            case "3":
                handleReaderBorrow();
                break;
            case "4":
                handleReaderLoans();
                break;
            case "5":
                handleReaderReturnRequest();
                break;
            case "6":
                handleReaderReportLost();
                break;
            case "7":
                handleViewProfile();
                break;
            case "0":
                handleLogout();
                break;
            default:
                System.out.println("Lựa chọn không hợp lệ!");
        }
        return true;
    }

    private static boolean showLibrarianMenu() throws Exception {
        DocGiaDTO user = currentSession.getUser();
        System.out.println("\n====================================================================");
        System.out.println("       CỔNG THỦ THƯ QUẢN TRỊ (RMI) - " + user.getHoTen());
        System.out.println("====================================================================");
        System.out.println("1. Xem toàn bộ danh sách phiếu mượn (RMI: getAllPhieuMuon)");
        System.out.println("2. Xác nhận giao sách tại quầy (RMI: xacNhanGiaoSach)");
        System.out.println("3. Xác nhận thu hồi và trả sách (RMI: traSach)");
        System.out.println("4. Xác nhận thu tiền đền bù / mở khóa (RMI: xacNhanDenBu)");
        System.out.println("5. Quét dọn dẹp phiếu giữ chỗ quá hạn 24h (RMI: huyPhieuChoQuaHan)");
        System.out.println("6. Quản lý kho sách (Tra cứu / Thêm / Bổ sung / Xóa)");
        System.out.println("7. Quản lý người dùng (Danh sách / Mở khóa)");
        System.out.println("8. Xem thống kê thư viện (RMI: layThongKe)");
        System.out.println("0. Đăng xuất");
        System.out.print("Vui lòng chọn chức năng (0-8): ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                handleLibrarianAllLoans();
                break;
            case "2":
                handleLibrarianConfirmHandover();
                break;
            case "3":
                handleLibrarianConfirmReturn();
                break;
            case "4":
                handleLibrarianConfirmCompensation();
                break;
            case "5":
                handleLibrarianCleanExpired();
                break;
            case "6":
                handleLibrarianBookManagement();
                break;
            case "7":
                handleLibrarianUserManagement();
                break;
            case "8":
                handleLibrarianStats();
                break;
            case "0":
                handleLogout();
                break;
            default:
                System.out.println("Lựa chọn không hợp lệ!");
        }
        return true;
    }

    // --- CÁC HÀM XỬ LÝ NGHIỆP VỤ QUA RMI ---

    private static void handleViewAllBooks() {
        try {
            System.out.println("\n>>> [RMI Call] service.getAllBooks()...");
            List<SachDTO> list = service.getAllBooks();
            printBooks(list);
        } catch (Exception e) {
            System.err.println("Lỗi tra cứu sách: " + e.getMessage());
        }
    }

    private static void handleSearchBooks() {
        try {
            System.out.print("Nhập từ khóa tìm kiếm (tên sách hoặc tác giả): ");
            String kw = scanner.nextLine().trim();
            System.out.println(">>> [RMI Call] service.searchBooks('" + kw + "')...");
            List<SachDTO> list = service.searchBooks(kw);
            printBooks(list);
        } catch (Exception e) {
            System.err.println("Lỗi tìm kiếm sách: " + e.getMessage());
        }
    }

    private static void printBooks(List<SachDTO> list) {
        if (list == null || list.isEmpty()) {
            System.out.println("Không có cuốn sách nào.");
            return;
        }
        System.out.printf("%-6s | %-32s | %-20s | %-8s | %-15s | %-10s%n",
                "MÃ", "TÊN SÁCH", "TÁC GIẢ", "NĂM XB", "TRẠNG THÁI", "GIÁ NIÊM YẾT");
        System.out.println("-------------------------------------------------------------------------------------------------");
        for (SachDTO s : list) {
            String status;
            switch (s.getTrangThai()) {
                case LibraryConstants.SACH_SAN_SANG: status = "Sẵn sàng (0)"; break;
                case LibraryConstants.SACH_DANG_MUON: status = "Đang mượn (1)"; break;
                case LibraryConstants.SACH_GIU_CHO: status = "Giữ chỗ 24h (2)"; break;
                case LibraryConstants.SACH_NGUNG_PHUC_VU: status = "Báo mất (-1)"; break;
                default: status = "Mã: " + s.getTrangThai();
            }
            System.out.printf("%-6d | %-32s | %-20s | %-8d | %-15s | %-10s%n",
                    s.getMaSach(),
                    truncate(s.getTenSach(), 32),
                    truncate(s.getTacGia(), 20),
                    s.getNamXuatBan(),
                    status,
                    s.getGiaTien() + " đ");
        }
    }

    private static void handleLogin() {
        try {
            System.out.print("Nhập Email: ");
            String email = scanner.nextLine().trim();
            System.out.print("Nhập Mật khẩu: ");
            String matKhau = scanner.nextLine().trim();

            System.out.println(">>> [RMI Call] service.login('" + email + "', '***')...");
            currentSession = service.login(email, matKhau);

            DocGiaDTO u = currentSession.getUser();
            System.out.println("\n====================================================================");
            System.out.println("  [ĐĂNG NHẬP THÀNH CÔNG] Xin chào: " + u.getHoTen() + "!");
            System.out.println("  - Vai trò: " + (u.isLibrarian() ? "Thủ thư (LIBRARIAN)" : "Độc giả (READER)"));
            System.out.println("  - RMI Session Token: " + currentSession.getToken().substring(0, 8) + "...");
            System.out.println("====================================================================");
        } catch (LibraryException e) {
            System.err.println("Đăng nhập thất bại: " + e.getMessage() + " (Mã lỗi: " + e.getErrorCode() + ")");
        } catch (Exception e) {
            System.err.println("Lỗi RMI khi đăng nhập: " + e.getMessage());
        }
    }

    private static void handleRegister() {
        try {
            System.out.println("\n--- ĐĂNG KÝ TÀI KHOẢN ĐỘC GIẢ MỚI ---");
            System.out.print("Họ và tên: ");
            String hoTen = scanner.nextLine().trim();
            System.out.print("Email: ");
            String email = scanner.nextLine().trim();
            System.out.print("Mật khẩu: ");
            String matKhau = scanner.nextLine().trim();
            System.out.print("Số điện thoại: ");
            String sdt = scanner.nextLine().trim();

            DocGiaDTO dg = new DocGiaDTO(hoTen, email, matKhau, sdt, LibraryConstants.ROLE_READER);
            System.out.println(">>> [RMI Call] service.dangKyDocGia(...)...");
            int id = service.dangKyDocGia(dg);
            System.out.println("[THÀNH CÔNG] Đã đăng ký tài khoản thành công! Mã độc giả mới: #" + id);
            System.out.println("Bây giờ bạn có thể dùng Email và Mật khẩu vừa tạo để đăng nhập.");
        } catch (LibraryException e) {
            System.err.println("Đăng ký không thành công: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Lỗi RMI: " + e.getMessage());
        }
    }

    private static void handleLogout() {
        if (currentSession != null) {
            try {
                System.out.println(">>> [RMI Call] service.logout(token)...");
                service.logout(currentSession.getToken());
            } catch (Exception ignored) {}
            currentSession = null;
            System.out.println("Đã đăng xuất phiên làm việc!");
        }
    }

    private static void handleReaderBorrow() {
        try {
            System.out.print("Nhập Mã sách (MaSach) muốn mượn: ");
            int maSach = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Nhập số ngày mượn (mặc định 14 ngày): ");
            String strDays = scanner.nextLine().trim();
            int days = strDays.isEmpty() ? 14 : Integer.parseInt(strDays);

            System.out.println(">>> [RMI Call] service.muonSach(token, maSach=" + maSach + ", soNgay=" + days + ")...");
            int maPhieu = service.muonSach(currentSession.getToken(), maSach, days);
            System.out.println("\n[THÀNH CÔNG] Đăng ký giữ chỗ sách thành công!");
            System.out.println("  - Mã phiếu mượn: #" + maPhieu);
            System.out.println("  - Trạng thái phiếu: Chờ lấy sách (0)");
            System.out.println("  - Thời hạn giữ chỗ: 24 giờ. Vui lòng đến quầy Thư viện để nhận sách!");
        } catch (LibraryException e) {
            System.err.println("\n[TỪ CHỐI] Không thể mượn sách: " + e.getMessage());
            System.err.println("  Mã lỗi nghiệp vụ: " + e.getErrorCode());
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    private static void handleReaderLoans() {
        try {
            System.out.println(">>> [RMI Call] service.getPhieuMuonByDocGia(token)...");
            List<PhieuMuonDTO> list = service.getPhieuMuonByDocGia(currentSession.getToken());
            printLoans(list);
        } catch (Exception e) {
            System.err.println("Lỗi tra cứu phiếu mượn: " + e.getMessage());
        }
    }

    private static void handleReaderReturnRequest() {
        try {
            System.out.print("Nhập Mã phiếu (MaPhieu) cần yêu cầu trả sách: ");
            int maPhieu = Integer.parseInt(scanner.nextLine().trim());
            System.out.println(">>> [RMI Call] service.yeuCauTraSach(token, " + maPhieu + ")...");
            boolean ok = service.yeuCauTraSach(currentSession.getToken(), maPhieu);
            if (ok) {
                System.out.println("[THÀNH CÔNG] Đã gửi yêu cầu trả sách tới Thủ thư! Vui lòng mang sách tới quầy để hoàn tất.");
            } else {
                System.out.println("[THẤT BẠI] Phiếu không ở trạng thái đang mượn hợp lệ.");
            }
        } catch (LibraryException e) {
            System.err.println("Từ chối: " + e.getMessage() + " (Mã lỗi: " + e.getErrorCode() + ")");
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    private static void handleReaderReportLost() {
        try {
            System.out.print("Nhập Mã phiếu (MaPhieu) bạn muốn báo mất sách: ");
            int maPhieu = Integer.parseInt(scanner.nextLine().trim());
            System.out.println(">>> [RMI Call] service.baoMatSach(token, " + maPhieu + ")...");
            boolean ok = service.baoMatSach(currentSession.getToken(), maPhieu);
            if (ok) {
                System.out.println("[THÀNH CÔNG] Đã ghi nhận báo mất sách!");
                System.out.println("  - Phiếu chuyển sang Chờ đền bù (4).");
                System.out.println("  - Vui lòng đến quầy Thư viện nộp tiền phạt đền bù theo niêm yết để mở lại tài khoản.");
            }
        } catch (LibraryException e) {
            System.err.println("Từ chối: " + e.getMessage() + " (Mã lỗi: " + e.getErrorCode() + ")");
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    private static void handleViewProfile() {
        try {
            System.out.println(">>> [RMI Call] service.getProfile(token)...");
            DocGiaDTO user = service.getProfile(currentSession.getToken());
            System.out.println("\n--- THÔNG TIN TÀI KHOẢN ---");
            System.out.println("Mã độc giả: #" + user.getMaDocGia());
            System.out.println("Họ tên:     " + user.getHoTen());
            System.out.println("Email:      " + user.getEmail());
            System.out.println("SĐT:        " + user.getSoDienThoai());
            System.out.println("Vai trò:    " + user.getVaiTro());
            System.out.println("Trạng thái: " + (user.isLocked() ? "BỊ KHÓA (" + user.getLyDoKhoa() + ")" : "HOẠT ĐỘNG BÌNH THƯỜNG"));
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    // --- CÁC HÀM DÀNH CHO THỦ THƯ ---

    private static void handleLibrarianAllLoans() {
        try {
            System.out.println(">>> [RMI Call] service.getAllPhieuMuon(token)...");
            List<PhieuMuonDTO> list = service.getAllPhieuMuon(currentSession.getToken());
            printLoans(list);
        } catch (LibraryException e) {
            System.err.println("Lỗi: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Lỗi RMI: " + e.getMessage());
        }
    }

    private static void printLoans(List<PhieuMuonDTO> list) {
        if (list == null || list.isEmpty()) {
            System.out.println("Không có phiếu mượn nào.");
            return;
        }
        System.out.printf("%-6s | %-16s | %-26s | %-12s | %-12s | %-18s | %-10s%n",
                "MÃ PHIẾU", "ĐỘC GIẢ", "TÊN SÁCH", "NGÀY MƯỢN", "HẠN TRẢ", "TRẠNG THÁI PHIẾU", "ĐỀN BÙ");
        System.out.println("-----------------------------------------------------------------------------------------------------------------------");
        for (PhieuMuonDTO pm : list) {
            String st;
            switch (pm.getTrangThaiPhieu()) {
                case LibraryConstants.PHIEU_CHO_LAY: st = "0 - Chờ lấy 24h"; break;
                case LibraryConstants.PHIEU_DANG_MUON: st = "1 - Đang mượn"; break;
                case LibraryConstants.PHIEU_DA_TRA: st = "2 - Đã trả / Kết thúc"; break;
                case LibraryConstants.PHIEU_DA_HUY: st = "3 - Đã hủy quá hạn"; break;
                case LibraryConstants.PHIEU_CHO_DEN_BU: st = "4 - Chờ đền bù"; break;
                case LibraryConstants.PHIEU_CHO_XAC_NHAN_TRA: st = "6 - Chờ duyệt trả"; break;
                default: st = "Mã " + pm.getTrangThaiPhieu();
            }
            System.out.printf("%-8d | %-16s | %-26s | %-12s | %-12s | %-18s | %-10s%n",
                    pm.getMaPhieu(),
                    truncate(pm.getTenDocGia(), 16),
                    truncate(pm.getTenSach(), 26),
                    formatDate(pm.getNgayMuon()),
                    formatDate(pm.getNgayHenTra()),
                    st,
                    pm.getTienDenBu() + " đ");
        }
    }

    private static void handleLibrarianConfirmHandover() {
        try {
            System.out.print("Nhập Mã phiếu cần giao sách (Trạng thái 0): ");
            int maPhieu = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Số ngày mượn thực tế (Enter để giữ nguyên): ");
            String strDays = scanner.nextLine().trim();
            int days = strDays.isEmpty() ? 0 : Integer.parseInt(strDays);

            System.out.println(">>> [RMI Call] service.xacNhanGiaoSach(token, " + maPhieu + ", " + days + ")...");
            boolean ok = service.xacNhanGiaoSach(currentSession.getToken(), maPhieu, days);
            if (ok) {
                System.out.println("[THÀNH CÔNG] Đã xác nhận giao sách! Phiếu chuyển sang Đang mượn (1), Sách chuyển sang Đang mượn (1).");
            }
        } catch (LibraryException e) {
            System.err.println("Lỗi: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Lỗi RMI: " + e.getMessage());
        }
    }

    private static void handleLibrarianConfirmReturn() {
        try {
            System.out.print("Nhập Mã phiếu cần thu hồi và trả sách: ");
            int maPhieu = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Nhập tiền phụ thu hỏng hóc nếu có (0 nếu bình thường): ");
            String strFee = scanner.nextLine().trim();
            int fee = strFee.isEmpty() ? 0 : Integer.parseInt(strFee);
            System.out.print("Tình trạng sách khi trả (Enter: Bình thường (Nguyên vẹn)): ");
            String cond = scanner.nextLine().trim();
            if (cond.isEmpty()) cond = "Bình thường (Nguyên vẹn)";

            System.out.println(">>> [RMI Call] service.traSach(token, " + maPhieu + ", " + fee + ", '" + cond + "')...");
            boolean ok = service.traSach(currentSession.getToken(), maPhieu, fee, cond);
            if (ok) {
                System.out.println("[THÀNH CÔNG] Đã hoàn tất trả sách! Phiếu chuyển sang Đã trả (2), Sách chuyển về Sẵn sàng (0).");
            }
        } catch (LibraryException e) {
            System.err.println("Lỗi: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Lỗi RMI: " + e.getMessage());
        }
    }

    private static void handleLibrarianConfirmCompensation() {
        try {
            System.out.print("Nhập Mã phiếu xác nhận đã thu đủ tiền đền bù: ");
            int maPhieu = Integer.parseInt(scanner.nextLine().trim());
            System.out.println(">>> [RMI Call] service.xacNhanDenBu(token, " + maPhieu + ")...");
            boolean ok = service.xacNhanDenBu(currentSession.getToken(), maPhieu);
            if (ok) {
                System.out.println("[THÀNH CÔNG] Đã hoàn tất đền bù! Tài khoản độc giả đã được mở khóa tự động.");
            }
        } catch (LibraryException e) {
            System.err.println("Lỗi: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Lỗi RMI: " + e.getMessage());
        }
    }

    private static void handleLibrarianCleanExpired() {
        try {
            System.out.println(">>> [RMI Call] service.huyPhieuChoQuaHan(token)...");
            int count = service.huyPhieuChoQuaHan(currentSession.getToken());
            System.out.println("[HOÀN TẤT] Số lượng phiếu giữ chỗ quá hạn 24h đã được hủy: " + count);
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    private static void handleLibrarianBookManagement() {
        System.out.println("\n--- QUẢN LÝ KHO SÁCH ---");
        System.out.println("1. Thêm đầu sách mới");
        System.out.println("2. Bổ sung sách thay thế cho sách bị mất");
        System.out.println("3. Xóa sách khỏi hệ thống");
        System.out.print("Chọn thao tác (1-3): ");
        String c = scanner.nextLine().trim();

        try {
            if ("1".equals(c)) {
                System.out.print("Tên sách: ");
                String ten = scanner.nextLine().trim();
                System.out.print("Tác giả: ");
                String tg = scanner.nextLine().trim();
                System.out.print("Năm xuất bản: ");
                int nam = Integer.parseInt(scanner.nextLine().trim());
                System.out.print("Giá niêm yết: ");
                int gia = Integer.parseInt(scanner.nextLine().trim());
                SachDTO s = new SachDTO(ten, tg, nam, 0, "", gia);
                int id = service.themSach(currentSession.getToken(), s);
                System.out.println("[THÀNH CÔNG] Đã thêm sách mới! Mã sách: #" + id);
            } else if ("2".equals(c)) {
                System.out.print("Nhập Mã sách cần bổ sung: ");
                int id = Integer.parseInt(scanner.nextLine().trim());
                System.out.print("Giá nhập mới: ");
                int gia = Integer.parseInt(scanner.nextLine().trim());
                boolean ok = service.boSungSachMoi(currentSession.getToken(), id, gia);
                if (ok) System.out.println("[THÀNH CÔNG] Sách đã được bổ sung và đưa về trạng thái Sẵn sàng mượn.");
            } else if ("3".equals(c)) {
                System.out.print("Nhập Mã sách cần xóa: ");
                int id = Integer.parseInt(scanner.nextLine().trim());
                boolean ok = service.xoaSach(currentSession.getToken(), id);
                if (ok) System.out.println("[THÀNH CÔNG] Đã xóa cuốn sách khỏi hệ thống.");
            }
        } catch (LibraryException e) {
            System.err.println("Từ chối: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    private static void handleLibrarianUserManagement() {
        System.out.println("\n--- QUẢN LÝ NGƯỜI DÙNG ---");
        System.out.println("1. Xem danh sách toàn bộ người dùng");
        System.out.println("2. Cấp tài khoản mới");
        System.out.println("3. Mở khóa tài khoản cho độc giả");
        System.out.print("Chọn thao tác (1-3): ");
        String c = scanner.nextLine().trim();

        try {
            if ("1".equals(c)) {
                List<DocGiaDTO> list = service.getAllDocGia(currentSession.getToken());
                System.out.printf("%-6s | %-20s | %-24s | %-12s | %-10s | %-15s%n",
                        "MÃ ĐG", "HỌ TÊN", "EMAIL", "SĐT", "VAI TRÒ", "TRẠNG THÁI");
                System.out.println("-------------------------------------------------------------------------------------------------");
                for (DocGiaDTO d : list) {
                    System.out.printf("%-6d | %-20s | %-24s | %-12s | %-10s | %-15s%n",
                            d.getMaDocGia(), truncate(d.getHoTen(), 20), truncate(d.getEmail(), 24),
                            d.getSoDienThoai(), d.getVaiTro(), d.isLocked() ? "Bị khóa" : "Hoạt động");
                }
            } else if ("2".equals(c)) {
                System.out.print("Họ và tên: ");
                String name = scanner.nextLine().trim();
                System.out.print("Email: ");
                String email = scanner.nextLine().trim();
                System.out.print("Mật khẩu: ");
                String pass = scanner.nextLine().trim();
                System.out.print("SĐT: ");
                String phone = scanner.nextLine().trim();
                System.out.print("Vai trò (READER / LIBRARIAN): ");
                String role = scanner.nextLine().trim().toUpperCase();
                if (!"LIBRARIAN".equals(role)) role = "READER";

                DocGiaDTO dg = new DocGiaDTO(name, email, pass, phone, role);
                int newId = service.themDocGia(currentSession.getToken(), dg);
                System.out.println("[THÀNH CÔNG] Đã cấp tài khoản thành công! Mã ID: #" + newId);
            } else if ("3".equals(c)) {
                System.out.print("Nhập Mã độc giả cần mở khóa: ");
                int id = Integer.parseInt(scanner.nextLine().trim());
                boolean ok = service.moKhoaTaiKhoan(currentSession.getToken(), id);
                if (ok) System.out.println("[THÀNH CÔNG] Đã mở khóa tài khoản cho độc giả #" + id);
            }
        } catch (LibraryException e) {
            System.err.println("Lỗi: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    private static void handleLibrarianStats() {
        try {
            System.out.println(">>> [RMI Call] service.layThongKe(token)...");
            Map<String, Object> stats = service.layThongKe(currentSession.getToken());
            System.out.println("\n--- BÁO CÁO THỐNG KÊ THƯ VIỆN HIỆN TẠI ---");
            stats.forEach((k, v) -> System.out.printf("  %-25s: %s%n", k, v));
        } catch (Exception e) {
            System.err.println("Lỗi: " + e.getMessage());
        }
    }

    private static String formatDate(java.util.Date d) {
        if (d == null) return "N/A";
        return new java.text.SimpleDateFormat("dd/MM/yyyy").format(d);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
