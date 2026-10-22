# KẾ HOẠCH KIỂM THỬ HỆ THỐNG (TEST PLAN)
**Đồ án:** Remote School Library Management – Hệ thống quản lý thư viện trường học từ xa  
**Học phần:** CMP180 – Lập trình mạng máy tính  
**Tác giả:** Nhóm em  

---

## 1. MỤC TIÊU VÀ PHẠM VI KIỂM THỬ

Kế hoạch kiểm thử nhằm xác minh toàn diện tính đúng đắn, an toàn và hiệu năng của hệ thống theo 3 tầng độc lập:
1. **Tầng A (Unit & Serialization):** Kiểm tra tính toàn vẹn của cơ chế tuần tự hóa (Bài 6) và các đối tượng truyền nhận (DTO, Exception) mà không cần CSDL hay kết nối mạng.
2. **Tầng B (RMI Integration & SQL Server):** Kiểm tra lời gọi Remote Method Invocation thật (Bài 9), cơ chế xác thực phiên (Token), phân quyền vai trò (READER vs LIBRARIAN) và chu trình 6 trạng thái mượn/giao/trả sách.
3. **Tầng C (Concurrency & Race Condition Control):** Kiểm thử cạnh tranh đa luồng gọi trực tiếp qua RMI (với 2 Client và 10 Client đồng thời), sử dụng `CountDownLatch` để phát lệnh đồng thời và đối chiếu bất biến dữ liệu trong SQL Server sau khi kết thúc.

---

## 2. PHÂN TẦNG VÀ DANH MỤC CÁC CA KIỂM THỬ (TEST CASES)

### TẦNG A: Serialization & DTO Unit Test (`SerializationTest.java`)
- **Mục đích:** Đảm bảo đối tượng Serializable hoạt động chính xác khi truyền qua luồng nhị phân ObjectStream.
- **Môi trường:** Độc lập (Không cần CSDL, không cần RMI Server).
- **Danh sách ca kiểm thử:**
  - `TC-A01`: Tuần tự hóa và giải tuần tự hóa `SachDTO`.
  - `TC-A02`: Tuần tự hóa `DocGiaDTO` và kiểm tra mật khẩu bị che giấu (`matKhau == null`).
  - `TC-A03`: Tuần tự hóa `PhieuMuonDTO` (bảo toàn ngày tháng, hạn giữ chỗ, hạn trả, tiền phạt).
  - `TC-A04`: Tuần tự hóa `UserSessionDTO` (token và thông tin người dùng).
  - `TC-A05`: Tuần tự hóa `LibraryException` (bảo toàn mã lỗi `errorCode` qua mạng).
  - `TC-A06`: Kiểm chứng nguyên lý Pass-By-Value của Java Serialization (thay đổi bản sao ở Client không ảnh hưởng đối tượng gốc).

---

### TẦNG B: RMI Integration & Real SQL Server Test (`RmiIntegrationTest.java`)
- **Mục đích:** Xác minh kết nối Remote Stub, đăng nhập, phân quyền và chu trình xử lý nghiệp vụ với SQL Server thật.
- **Môi trường:** RMI Server (cổng 1099/1098), Microsoft SQL Server (`ThuVienDB`, cổng 1433).
- **Danh sách ca kiểm thử:**
  - `TC-B01`: Kết nối RMI Registry, tra cứu Stub `LibraryService` và gọi `ping()`.
  - `TC-B02`: Xác thực đăng nhập:
    - `TC-B02a`: Đăng nhập sai mật khẩu -> Kỳ vọng `LibraryException(ERR_UNAUTHORIZED)`.
    - `TC-B02b`: Đăng nhập đúng tài khoản Độc giả (`nguyenvanan@school.edu.vn`) -> Nhận Token hợp lệ.
    - `TC-B02c`: Đăng nhập đúng tài khoản Thủ thư (`admin@school.edu.vn`) -> Nhận Token quyền LIBRARIAN.
  - `TC-B03`: Kiểm tra phân quyền tại RMI Server:
    - `TC-B03a`: Độc giả gọi phương thức thủ thư (`xacNhanGiaoSach`) -> Kỳ vọng `LibraryException(ERR_FORBIDDEN)`.
    - `TC-B03b`: Triệu gọi với token không hợp lệ -> Kỳ vọng `LibraryException(ERR_UNAUTHORIZED)`.
  - `TC-B04`: Tra cứu danh mục sách qua RMI (`getAllBooks()`, `searchBooks()`).
  - `TC-B05`: Chu trình 6 trạng thái mượn/trả sách và đối chiếu CSDL:
    - `TC-B05a`: Độc giả đăng ký giữ chỗ qua RMI -> Sinh mã phiếu; DB `Sach=2` (Giữ chỗ), DB `PhieuMuon=0` (Chờ lấy).
    - `TC-B05b`: Độc giả khác mượn trùng cuốn sách đang giữ chỗ -> Bị từ chối với `ERR_BOOK_NOT_AVAILABLE`.
    - `TC-B05c`: Thủ thư xác nhận giao sách tại quầy -> DB `Sach=1` (Đang mượn), DB `PhieuMuon=1` (Đang mượn).
    - `TC-B05d`: Độc giả gửi yêu cầu trả sách -> DB `PhieuMuon=6` (Chờ duyệt trả).
    - `TC-B05e`: Thủ thư xác nhận trả sách -> DB `Sach=0` (Sẵn sàng), DB `PhieuMuon=2` (Đã kết thúc).

---

### TẦNG C: Concurrency Control Direct via RMI (`RmiConcurrencyTest.java`)
- **Mục đích:** Chứng minh cơ chế chống mượn trùng (Race Condition) bằng khóa `UPDLOCK, HOLDLOCK` và `Unique Filtered Index` trên CSDL thật.
- **Phương pháp:**
  - `ExecutorService` tạo nhóm luồng Client độc lập.
  - `CountDownLatch(1)` làm cổng xuất phát để các luồng phát lệnh gọi `service.muonSach(...)` tại cùng thời điểm.
  - `CountDownLatch(N)` chờ tất cả các luồng hoàn tất và thu thập thời gian thực thi, mã phiếu và mã lỗi.
- **Tiêu chí đạt chuẩn:**
  1. Đúng 1 yêu cầu thành công (`maPhieu > 0`).
  2. Toàn bộ `N - 1` yêu cầu còn lại bị từ chối với mã lỗi `ERR_BOOK_NOT_AVAILABLE`.
  3. Không có lỗi mạng ngoại lai, deadlock hay exception không xử lý.
  4. Đối chiếu trực tiếp qua kết nối CSDL hành chính: Đúng 1 phiếu chiếm dụng duy nhất (`TrangThaiPhieu IN (0, 1, 4, 6)`) cho cuốn sách kiểm thử, khớp với mã phiếu của người thắng.
  5. Trạng thái cuốn sách trong CSDL là 2 (Giữ chỗ 24h).

---

## 3. LỆNH THỰC THI BỘ KIỂM THỬ

```bat
:: 1. Biên dịch toàn bộ mã nguồn và các lớp kiểm thử
compile.bat --no-pause

:: 2. Khởi chạy RMI Server (cửa sổ dòng lệnh 1)
run_server.bat

:: 3. Chạy kiểm thử Tầng A (Serialization Unit Test)
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.SerializationTest

:: 4. Chạy kiểm thử Tầng B (RMI Integration Test)
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiIntegrationTest

:: 5. Chạy kiểm thử Tầng C (RMI Concurrency Test - 2 Clients)
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 2 1

:: 6. Chạy kiểm thử Tầng C (RMI Concurrency Test - 10 Clients)
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 10 1

:: 7. Chạy kiểm thử Web Gateway HTTP (Yêu cầu run_web_client.bat đang chạy)
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" ConcurrencyTest
```
