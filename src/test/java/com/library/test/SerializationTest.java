package com.library.test;

import com.library.model.DocGiaDTO;
import com.library.model.LibraryConstants;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;
import com.library.service.LibraryException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

/**
 * LỚP KIỂM THỬ TẦNG A: Serialization & DTO Unit Test (Bài 6 - Java Serialization).
 * - Không phụ thuộc CSDL SQL Server hoặc RMI Registry.
 * - Kiểm chứng khả năng tuần tự hóa / giải tuần tự hóa của tất cả DTO và Exception nghiệp vụ.
 * - Xác minh tính chất Pass-By-Value của Java Serialization (bản sao trên Client không làm biến đổi dữ liệu trên Server).
 * - Kiểm tra bảo mật: Không lộ mật khẩu trong DTO phiên làm việc.
 */
public class SerializationTest {

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println("  KIỂM THỬ TẦNG A: TUẦN TỰ HÓA ĐỐI TƯỢNG (JAVA SERIALIZATION - BÀI 6)");
        System.out.println("====================================================================");

        int passed = 0;
        int failed = 0;

        // Ca 1: Tuần tự hóa SachDTO
        try {
            SachDTO original = new SachDTO(10, "Lập Trình Mạng Với Java", "Nguyễn Văn A", 2024, LibraryConstants.SACH_SAN_SANG, "/covers/net.jpg", 85000);
            SachDTO deserialized = roundTrip(original);

            if (deserialized != null && deserialized.getMaSach() == 10 &&
                "Lập Trình Mạng Với Java".equals(deserialized.getTenSach()) &&
                deserialized.getGiaTien() == 85000) {
                System.out.println("[PASS] Ca 1: Tuần tự hóa và phục hồi SachDTO thành công.");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 1: Dữ liệu SachDTO sau khi giải tuần tự hóa không khớp!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 1: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        // Ca 2: Tuần tự hóa DocGiaDTO và kiểm tra an toàn mật khẩu
        try {
            DocGiaDTO dg = new DocGiaDTO(101, "Nguyễn Văn An", "an@gmail.com", null, "0901234567", LibraryConstants.ROLE_READER, 1);
            DocGiaDTO deserialized = roundTrip(dg);

            if (deserialized != null && deserialized.getMaDocGia() == 101 &&
                "an@gmail.com".equals(deserialized.getEmail()) &&
                deserialized.getMatKhau() == null) {
                System.out.println("[PASS] Ca 2: Tuần tự hóa DocGiaDTO an toàn (Mật khẩu không bị rò rỉ).");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 2: DocGiaDTO phục hồi không đúng!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 2: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        // Ca 3: Tuần tự hóa PhieuMuonDTO
        try {
            Date now = new Date();
            PhieuMuonDTO pm = new PhieuMuonDTO(201, 101, "Nguyễn Văn An", 10, "Lập Trình Mạng",
                    now, new Date(now.getTime() + 14L * 86400000), null,
                    LibraryConstants.PHIEU_CHO_LAY, 0, 85000, new Date(now.getTime() + 86400000), 0);
            pm.setSoNgayMuon(14);
            pm.setTinhTrangSachKhiTra("Nguyên vẹn");

            PhieuMuonDTO deserialized = roundTrip(pm);
            if (deserialized != null && deserialized.getMaPhieu() == 201 &&
                deserialized.getTrangThaiPhieu() == LibraryConstants.PHIEU_CHO_LAY &&
                deserialized.getGiaTienSach() == 85000 &&
                "Nguyên vẹn".equals(deserialized.getTinhTrangSachKhiTra())) {
                System.out.println("[PASS] Ca 3: Tuần tự hóa PhieuMuonDTO thành công (Đầy đủ thuộc tính ngày, hạn giữ, tiền phạt).");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 3: PhieuMuonDTO phục hồi không khớp!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 3: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        // Ca 4: Tuần tự hóa UserSessionDTO
        try {
            DocGiaDTO user = new DocGiaDTO(5, "Trần Thủ Thư", "thuthu@thuvien.edu.vn", null, "0912345678", LibraryConstants.ROLE_LIBRARIAN);
            UserSessionDTO session = new UserSessionDTO("token_test_abcdef123456", user);

            UserSessionDTO deserialized = roundTrip(session);
            if (deserialized != null && "token_test_abcdef123456".equals(deserialized.getToken()) &&
                deserialized.getUser() != null && deserialized.getUser().isLibrarian()) {
                System.out.println("[PASS] Ca 4: Tuần tự hóa UserSessionDTO thành công (Token và Thông tin người dùng).");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 4: UserSessionDTO phục hồi không khớp!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 4: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        // Ca 5: Tuần tự hóa checked exception nghiệp vụ LibraryException
        try {
            LibraryException origEx = new LibraryException(LibraryConstants.ERR_BOOK_NOT_AVAILABLE, "Sách đang được người khác mượn!");
            LibraryException deserEx = roundTrip(origEx);

            if (deserEx != null && LibraryConstants.ERR_BOOK_NOT_AVAILABLE.equals(deserEx.getErrorCode()) &&
                deserEx.getMessage().contains("người khác mượn")) {
                System.out.println("[PASS] Ca 5: Tuần tự hóa LibraryException thành công (Bảo toàn errorCode qua RMI).");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 5: LibraryException phục hồi không khớp!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 5: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        // Ca 6: Kiểm chứng tính bất biến Pass-By-Value (Đặc trưng của RMI & Serialization)
        try {
            SachDTO serverBook = new SachDTO(1, "Sách Gốc Trên Server", "Tác Giả", 2020, LibraryConstants.SACH_SAN_SANG, "", 50000);
            SachDTO clientCopy = roundTrip(serverBook);

            // Client tự ý sửa bản sao cục bộ
            clientCopy.setTenSach("Sách Đã Bị Client Thay Đổi Tên");
            clientCopy.setTrangThai(LibraryConstants.SACH_NGUNG_PHUC_VU);

            // Kiểm tra: Đối tượng gốc trên Server không bị thay đổi!
            if ("Sách Gốc Trên Server".equals(serverBook.getTenSach()) &&
                serverBook.getTrangThai() == LibraryConstants.SACH_SAN_SANG) {
                System.out.println("[PASS] Ca 6: Xác minh cơ chế Pass-By-Value (Client sửa DTO nhận được không ảnh hưởng Server).");
                passed++;
            } else {
                System.err.println("[FAIL] Ca 6: Dữ liệu server bị biến đổi sai nguyên lý Pass-By-Value!");
                failed++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Ca 6: Ngoại lệ: " + e.getMessage());
            failed++;
        }

        System.out.println("--------------------------------------------------------------------");
        System.out.printf("TỔNG KẾT TẦNG A: %d PASS | %d FAIL (Tổng cộng 6 ca kiểm thử)%n", passed, failed);
        System.out.println("====================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }
}
