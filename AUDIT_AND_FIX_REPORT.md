# BÁO CÁO RÀ SOÁT VÀ NÂNG CẤP HỆ THỐNG (AUDIT AND FIX REPORT)
**Đồ án:** Remote School Library Management – Hệ thống quản lý thư viện trường học từ xa  
**Học phần:** CMP180 – Lập trình mạng máy tính  
**Tác giả:** Nhóm em  
**Môi trường:** Java 17 LTS, Microsoft SQL Server 2022 / Express, Java RMI, Java Serialization

---

## 1. TỔNG QUAN VÀ MỤC TIÊU RÀ SOÁT

Đồ án được rà soát và nâng cấp trực tiếp nhằm đáp ứng 100% chuẩn mực học thuật của học phần CMP180, đảm bảo tính chặt chẽ trong kiến trúc phân tán 3 tầng:
```
[Browser / HTTP Client] 
        │ (HTTP / JSON)
        ▼
[Web Gateway: BookApiController / RMI Client Manager] 
        │ (Java RMI Remote Stub - Port 1099/1098)
        │ (Java Serialization DTOs - Bài 6 & Bài 9)
        ▼
[RMI Server: LibraryServiceImpl / ServerSessionManager]
        │ (JDBC Connection Pool)
        ▼
[Database: Microsoft SQL Server - ThuVienDB (Port 1433)]
```
Hệ thống đồng thời cung cấp ứng dụng **Java RMI Console Client (`ClientMain`)** kết nối trực tiếp tới RMI Server không qua HTTP, phục vụ chứng minh thực nghiệm truyền đối tượng và gọi hàm từ xa.

---

## 2. BẢNG TỔNG HỢP RÀ SOÁT CÁC LỖI ĐÃ XÁC MINH VÀ PHƯƠNG ÁN XỬ LÝ (AUDIT MATRIX)

| STT | Vấn đề đã xác minh (Audit Point) | Vị trí file / dòng | Mức độ ảnh hưởng | Giải pháp kỹ thuật đã triển khai | Ca kiểm thử xác minh |
|:---:|:---|:---|:---:|:---|:---|
| **a** | `run_client.bat` gọi `ClientMain` nhưng chỉ có `ClientTest`; tiêu đề ghi nhầm Swing dù là Console. | `run_client.bat`, `src/.../client/` | Trung bình | Xây dựng hoàn chỉnh `ClientMain.java` (Console Client tương tác đa vai trò qua RMI); cập nhật tiêu đề script. | `run_client.bat` kết nối RMI thật. |
| **b** | `compile.bat` chỉ quét thư mục `src/`, bỏ sót `ConcurrencyTest.java` ở thư mục gốc khiến clean build không biên dịch test. | `compile.bat` | Cao | Cập nhật `compile.bat` để quét cả `src/*.java` và `ConcurrencyTest.java`, tự động dọn dẹp `bin/` trước khi build. | `compile.bat --no-pause` biên dịch 100% sạch. |
| **c** | `ConcurrencyTest` gửi 2 HTTP requests nhưng log in "chính xác 100%, ngăn chặn hoàn toàn Race Condition". | `ConcurrencyTest.java` | Cao | Viết lại `ConcurrencyTest.java` (HTTP) và bổ sung `RmiConcurrencyTest.java` (RMI đa luồng 2 và 10 clients), bỏ các tuyên bố thổi phồng, chỉ ghi log sự thật khách quan. | `RmiConcurrencyTest 2 1` & `RmiConcurrencyTest 10 1` PASS. |
| **d** | Test hardcode mã độc giả 1 và 2, trong khi ID 1 trong seed data là Thủ thư (`LIBRARIAN`). | `ClientTest.java`, `ConcurrencyTest.java` | Cao | Cập nhật toàn bộ test lấy tài khoản chuẩn từ seed (`admin@school.edu.vn` cho Thủ thư, `nguyenvanan@school.edu.vn`, `tranthibinh@school.edu.vn` cho Độc giả) hoặc sinh tài khoản độc giả động. | `RmiIntegrationTest` & `RmiConcurrencyTest` PASS. |
| **e** | `ClientTest` và `verify_thuviendb.sql` trả sách ngay sau giữ chỗ, bỏ qua bước xác nhận giao; giải thích sai trạng thái. | `ClientTest.java`, `verify_thuviendb.sql` | Cao | Chuẩn hóa quy trình 6 trạng thái: Giữ chỗ (0) -> Giao sách (1) -> Yêu cầu trả (6) -> Xác nhận trả (2). Cập nhật cả `verify_thuviendb.sql` và `ClientTest.java`. | `verify_thuviendb.sql` (7/7 PASS), `ClientTest` PASS. |
| **f** | `BookApiController` gom mọi `RemoteException` thành HTTP 409; lỗi DB hay mạng bị hiểu nhầm là tranh chấp sách. | `BookApiController.java` | Rất cao | Tách bạch: `LibraryException(ERR_BOOK_NOT_AVAILABLE)` -> 409, `ERR_UNAUTHORIZED` -> 401, `ERR_FORBIDDEN` -> 403, `RemoteException` -> 503, lỗi nội bộ -> 500. | Gọi API khi mất RMI/DB nhận đúng 503/500, tranh chấp nhận 409. |
| **g** | RMI Server không kiểm tra token/vai trò; caller có thể gọi thẳng RMI để bypass kiểm tra ở Web. | `ILibraryService.java`, `LibraryServiceImpl.java` | Nghiêm trọng | Đưa xác thực Token và phân quyền vai trò (`ServerSessionManager`) vào tận tầng RMI Server. Mọi thao tác ghi/quản trị đều bắt buộc Token hợp lệ. | Ca 3a, 3b trong `RmiIntegrationTest` (Chặn trái quyền tại RMI). |
| **h** | `DBConnection` có danh sách mật khẩu dự phòng; DAO bắt `SQLException` rồi trả rỗng/false làm lỗi DB giống kết quả bình thường. | `DBConnection.java`, `SachDAO.java`, `PhieuMuonDAO.java`, `DocGiaDAO.java` | Cao | Bỏ vòng lặp thử mật khẩu; nạp cấu hình từ `db.properties` / biến môi trường; các DAO lan truyền `SQLException` lên Service để xử lý chuẩn hóa. | `audit_inspect_db.sql` & tắt DB báo lỗi rõ ràng. |
| **i** | `sp_XacNhanGiaoSach` và thủ tục báo mất/đền bù đọc trạng thái trước `BEGIN TRANSACTION`. | `setup_thuviendb.sql`, `migration_v2_safe_upgrade.sql` | Cao | Bao bọc `BEGIN TRANSACTION` lên trước mọi câu lệnh `SELECT ... WITH (UPDLOCK, HOLDLOCK)` trong toàn bộ các Stored Procedures. | Chạy `migration_v2_safe_upgrade.sql` trên live DB. |
| **j** | `SachDAO.xoaSach` có comment "xóa lịch sử đã trả" nhưng SQL lại xóa `TrangThaiPhieu = 1` (đang mượn). | `SachDAO.java` | Rất cao | Xóa bỏ lệnh `DELETE FROM PhieuMuon`. Sách có lịch sử giao dịch được bảo vệ toàn vẹn lịch sử (từ chối xóa vật lý, hướng dẫn chuyển sang Ngừng phục vụ -1). | Unit test `xoaSach` từ chối xóa sách có lịch sử. |
| **k** | `boSungSachMoi`, `capNhatTrangThai` có thể đưa sách về Sẵn sàng (0) khi vẫn còn phiếu chiếm dụng chưa kết thúc. | `SachDAO.java` | Cao | Bổ sung kiểm tra tiền điều kiện: nếu cuốn sách còn phiếu chiếm dụng (`TrangThaiPhieu IN (0, 1, 4, 6)`) thì từ chối chuyển về Sẵn sàng (0). | `SachDAO.boSungSachMoi` ném `SQLException` khi sách đang mượn. |

---

## 3. CÁC NÂNG CẤP KIẾN TRÚC CHI TIẾT

### 3.1. Bảo vệ bất biến tại tầng Cơ sở dữ liệu (Database Invariant Protection)
- **Unique Filtered Index:**
  ```sql
  SET ANSI_NULLS ON;
  SET QUOTED_IDENTIFIER ON;
  GO
  CREATE UNIQUE NONCLUSTERED INDEX UQ_PhieuMuon_ActiveSach
  ON PhieuMuon(MaSach)
  WHERE TrangThaiPhieu IN (0, 1, 4, 6);
  ```
  Ngăn chặn triệt để tình trạng hai độc giả chiếm dụng cùng một bản sách vật lý ở mức vật lý lưu trữ của Microsoft SQL Server.
- **Khóa bi quan (Pessimistic Locking) trong Stored Procedure:**
  Đặt `BEGIN TRANSACTION` trước `SELECT ... WITH (UPDLOCK, ROWLOCK, HOLDLOCK)` trong `sp_KiemTraVaMuonSach`, `sp_XacNhanGiaoSach`, `sp_BaoMatVaDenSach`, `sp_TraSach`, đảm bảo khóa dòng được giữ xuyên suốt đến khi commit giao dịch.

### 3.2. Chuẩn hóa Java RMI & Serialization (Bài 6 & Bài 9)
- **Cấu hình mạng LAN:**
  - Thiết lập `System.setProperty("java.rmi.server.hostname", rmiHost)` trước khi export Remote Object, giúp Client ở các máy tính khác trong mạng LAN nhận đúng địa chỉ IP máy chủ thay vì bị trỏ về `127.0.0.1`.
  - Cố định cổng Registry (1099) và cổng Export Remote Object (1098), tạo điều kiện mở Firewall Windows chính xác.
- **Ping nhẹ nhàng:**
  - Thay thế việc gọi `getAllBooks()` tốn tài nguyên DB bằng phương thức nhẹ `service.ping()` trong `RMIClientManager`.
- **Pass-By-Value & Bảo mật Serialization:**
  - Mọi DTO đều khai báo `serialVersionUID` tường minh.
  - Loại bỏ hoàn toàn việc truyền mật khẩu thuần hoặc mã băm trong `DocGiaDTO` gửi về Client thông qua phương thức `user.getSafeCopy()`.
  - Mã hóa mật khẩu bằng thuật toán **SHA-256 kèm Salt ngẫu nhiên 16 bytes** (`PasswordUtil.java`), tự động nâng cấp mật khẩu cũ khi người dùng đăng nhập thành công.

### 3.3. Xác thực & Phân quyền tập trung tại RMI Server
- Xây dựng `ServerSessionManager.java` quản lý phiên bằng Token ngẫu nhiên (UUID), thread-safe bằng `ConcurrentHashMap`.
- Mọi phương thức thay đổi trạng thái hoặc yêu cầu quyền hạn đều kiểm tra `validateToken(token)` và `requireRole(token, ROLE_LIBRARIAN)`.

---

## 4. TÀI LIỆU VÀ SCRIPT ĐÃ BÀN GIAO
1. `sql_patches/migration_v2_safe_upgrade.sql`: Script nâng cấp CSDL bảo tồn 100% dữ liệu.
2. `sql_patches/audit_inspect_db.sql`: Script kiểm tra toàn vẹn CSDL (chế độ Read-Only).
3. `setup_thuviendb.sql`: Script khởi tạo CSDL mới đã tích hợp đầy đủ index và procedure an toàn.
4. `verify_thuviendb.sql`: Kịch bản kiểm tra logic SQL độc lập (7/7 ca kiểm thử ĐẠT).
5. `src/test/java/com/library/test/SerializationTest.java`: Bộ test Tầng A (6/6 ca PASS).
6. `src/test/java/com/library/test/RmiIntegrationTest.java`: Bộ test Tầng B (13/13 ca PASS).
7. `src/test/java/com/library/test/RmiConcurrencyTest.java`: Bộ test Tầng C (2 và 10 luồng đồng thời PASS).
8. `src/main/java/com/library/client/ClientMain.java`: Ứng dụng Console Client RMI trực tiếp.
9. `compile.bat`, `run_server.bat`, `run_client.bat`, `run_web_client.bat`, `run_concurrency_test.bat`.
10. `TEST_PLAN.md`, `TEST_RESULTS.md`, `DEMO_GUIDE.md`.
