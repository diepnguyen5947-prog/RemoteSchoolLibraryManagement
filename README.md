# 📚 HỆ THỐNG QUẢN LÝ THƯ VIỆN TRƯỜNG HỌC TỪ XA (REMOTE SCHOOL LIBRARY MANAGEMENT)
> **Distributed Remote School Library Management System**  
> **Học phần:** CMP180 – Lập trình mạng máy tính  
> **Nhóm thực hiện:** Nhóm em  
> **Kiến trúc cốt lõi:** **3-Tier Architecture** | **Java RMI (Bài 9)** | **Java Serialization (Bài 6)** | **Đa luồng & Đồng bộ (Bài 3)** | **Microsoft SQL Server (Transact-SQL)** | **Web Gateway**

---

## 🌟 1. GIỚI THIỆU TỔNG QUAN

Dự án **Hệ thống Quản lý Thư viện Trường học Từ xa** được nhóm em thiết kế và xây dựng theo chuẩn mực môn học Lập trình mạng máy tính (CMP180):
- **Giao tiếp từ xa qua Java RMI (Bài 9):** Toàn bộ nghiệp vụ tra cứu sách, đăng ký mượn/giữ chỗ, xác nhận giao sách, thu hồi sách và xử lý tài chính đền bù đều được thực thi thông qua lời gọi hàm từ xa RMI.
- **Truyền nhận dữ liệu qua Java Serialization (Bài 6):** Các thực thể nghiệp vụ (`SachDTO`, `DocGiaDTO`, `PhieuMuonDTO`, `UserSessionDTO`, `LibraryException`) được tuần tự hóa nhị phân qua luồng mạng theo nguyên lý Pass-By-Value an toàn, không rò rỉ thông tin mật khẩu.
- **Kiểm soát đồng thời và chống mượn trùng (Bài 3 & SQL Server):** Ngăn chặn triệt để tình trạng hai độc giả cùng chiếm dụng một bản sách vật lý bằng sự kết hợp giữa giao dịch khóa độc quyền bi quan (`UPDLOCK`, `ROWLOCK`, `HOLDLOCK`) và chỉ mục duy nhất có điều kiện (`Unique Filtered Index`) tại tầng lưu trữ CSDL.

### 🏛️ Mô hình kiến trúc 3 tầng chuẩn (3-Tier Architecture):
```
[Browser / Web Client]
        │ HTTP / JSON
        ▼
[Web Gateway: BookApiController / RMI Client Manager] (Port 8080)
        │ Java RMI Stub (Remote Proxy)
        │ Java Serialization (Bài 6 & Bài 9)
        ▼
[RMI Server: LibraryServiceImpl / ServerSessionManager] (Port 1099 / 1098)
        │ JDBC Connection (db.properties)
        ▼
[Database: Microsoft SQL Server - ThuVienDB] (Port 1433)
```
> **Lưu ý chuẩn mực học thuật:**
> - Trình duyệt Web **không phải** là Java RMI Client trực tiếp. Web Gateway đóng vai trò là một RMI Client trung gian chuyển tiếp yêu cầu và chuyển đổi kết quả.
> - Web Gateway **không đi tắt** xuống DAO hay truy cập trực tiếp CSDL. Mọi nghiệp vụ đều đi qua RMI Server.
> - Hệ thống cung cấp đồng thời ứng dụng **`ClientMain` (Java Console RMI Client)** để thực hiện tra cứu, đăng nhập và mượn/trả sách độc lập hoàn toàn với Web HTTP.

---

## ⚙️ 2. YÊU CẦU MÔI TRƯỜNG (PREREQUISITES)

- **Hệ điều hành:** Windows 10/11 (khuyến nghị cho môi trường thực hành), macOS hoặc Linux.
- **Java Development Kit (JDK):** JDK 17 LTS (OpenJDK hoặc Oracle JDK).
- **Hệ quản trị CSDL:** Microsoft SQL Server 2016 / 2019 / 2022 hoặc SQL Server Express (cổng 1433).
- **Trình duyệt Web:** Google Chrome, Microsoft Edge, Firefox.

---

## 🚀 3. HƯỚNG DẪN CÀI ĐẶT & KHỞI CHẠY (QUICK START)

### 📌 Bước 1: Khởi tạo Cơ sở Dữ liệu `ThuVienDB`
Mở SQL Server Management Studio (SSMS) hoặc chạy lệnh trong Terminal:
```bat
sqlcmd -S 127.0.0.1,1433 -U sa -P 123456 -i setup_thuviendb.sql
```
Sau đó chạy kịch bản kiểm tra toàn vẹn CSDL:
```bat
sqlcmd -S 127.0.0.1,1433 -U sa -P 123456 -d ThuVienDB -i verify_thuviendb.sql
```
*(Kết quả hiển thị: `7 / 7 CA KIỂM THỬ ĐẠT (PASS)`)*.

### 📌 Bước 2: Cấu hình kết nối CSDL (`db.properties`)
Nhóm em đã tách cấu hình CSDL ra file cục bộ để đảm bảo an toàn thông tin:
- Mặc định hệ thống nạp thông số từ file [**`db.properties`**](file:///db.properties) (đã cấu hình sẵn cho tài khoản `sa` / `123456`).
- Nếu môi trường của thầy cô hoặc bạn học sử dụng tài khoản khác, chỉ cần chỉnh sửa các dòng:
  ```properties
  db.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=ThuVienDB;trustServerCertificate=true;
  db.user=sa
  db.password=123456
  ```

### 📌 Bước 3: Biên dịch mã nguồn Java
Chạy file script biên dịch sạch (Clean Build):
👉 **`compile.bat`**
- Script tự động làm sạch `bin/`, quét toàn bộ mã nguồn trong `src/` và các tệp kiểm thử gốc, biên dịch thành các tệp `.class` tương thích Java 17.

### 📌 Bước 4: Khởi chạy các thành phần theo thứ tự

#### 1️⃣ Khởi chạy RMI Server:
Chạy script: 👉 **`run_server.bat`**
- Server sẽ kiểm tra CSDL, mở RMI Registry tại cổng **1099** và export Remote Object tại cổng **1098**.
- Khi màn hình hiện `[TRẠNG THÁI: HEALTHY] RMI SERVER SẴN SÀNG TIẾP NHẬN YÊU CẦU TỪ CÁC CLIENT!`, giữ cửa sổ này chạy nền.

#### 2️⃣ Khởi chạy Java RMI Console Client (Gọi trực tiếp RMI):
Chạy script: 👉 **`run_client.bat`**
- Ứng dụng Console kết nối trực tiếp RMI Stub, cho phép đăng nhập, tra cứu sách và đăng ký mượn/trả mà không cần bật Web Server.

#### 3️⃣ Khởi chạy Web Gateway (Cổng Web Portal):
Chạy script: 👉 **`run_web_client.bat`**
- Máy chủ Web lắng nghe tại cổng **8080**.
- Mở trình duyệt Web tại địa chỉ: 👉 **[http://localhost:8080/login](http://localhost:8080/login)**.

---

## 🔑 4. DANH SÁCH TÀI KHOẢN THỰC THỬ MẪU (TEST ACCOUNTS)

Mật khẩu mặc định cho toàn bộ tài khoản: **`123456`**

| Vai trò | Họ tên | Email đăng nhập | Mô tả quyền hạn |
| :--- | :--- | :--- | :--- |
| 👨‍💼 **Thủ Thư (Librarian)** | Thủ Thư Quản Trị | `admin@school.edu.vn` | Toàn quyền quản trị kho sách, duyệt giao sách tại quầy, thẩm định trả sách, quyết toán đền bù, mở khóa tài khoản, xem thống kê. |
| 🎓 **Độc Giả 1 (Reader)** | Nguyễn Văn An | `nguyenvanan@school.edu.vn` | Tài khoản độc giả chuẩn, dùng mượn sách và kiểm thử quy trình giao/trả. |
| 🎓 **Độc Giả 2 (Reader)** | Trần Thị Bình | `tranthibinh@school.edu.vn` | Độc giả độc lập dùng để kiểm thử mượn trùng đồng thời. |
| 🎓 **Độc Giả 3 (Reader)** | Lê Hoàng Cường | `lehoangcuong@school.edu.vn` | Độc giả bổ sung cho các kịch bản nhiều người dùng. |

---

## 🎯 5. BỘ KIỂM THỬ 3 TẦNG ĐỘC LẬP (VERIFIED TEST SUITE)

Nhóm em đã xây dựng bộ kiểm thử có thể kiểm chứng độc lập theo 3 tầng:

### Tầng A: Serialization & DTO Unit Test (Bài 6)
- **Lớp kiểm thử:** `com.library.test.SerializationTest`
- **Lệnh chạy:** `java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.SerializationTest`
- **Kết quả:** **6 / 6 ca kiểm thử ĐẠT (PASS)** (Bao gồm kiểm chứng Pass-By-Value và bảo mật không rò rỉ mật khẩu).

### Tầng B: RMI Integration & Real SQL Server Test (Bài 9)
- **Lớp kiểm thử:** `com.library.test.RmiIntegrationTest`
- **Lệnh chạy:** `java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiIntegrationTest`
- **Kết quả:** **13 / 13 ca kiểm thử ĐẠT (PASS)** (Kiểm tra Remote Stub, ping, token authentication, role authorization, chu trình 6 trạng thái mượn/giao/trả và đối chiếu CSDL thật).

### Tầng C: RMI Concurrency Test (Bài 3 & SQL Server Invariants)
- **Lớp kiểm thử:** `com.library.test.RmiConcurrencyTest`
- **Chạy kịch bản 2 Clients đồng thời:**
  ```bat
  java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 2 1
  ```
- **Chạy kịch bản 10 Clients đồng thời:**
  ```bat
  java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 10 1
  ```
- **Kết quả:** **100% ĐẠT (PASS)**. Đúng 1 yêu cầu nhận phiếu mượn thành công, các yêu cầu còn lại bị từ chối với mã lỗi `ERR_BOOK_NOT_AVAILABLE`, CSDL ghi nhận chính xác 1 phiếu chiếm dụng duy nhất.

---

## 📂 6. TÀI LIỆU VÀ BÁO CÁO KÈM THEO
- [**`AUDIT_AND_FIX_REPORT.md`**](file:///AUDIT_AND_FIX_REPORT.md): Báo cáo rà soát và khắc phục toàn bộ 11 điểm cốt lõi của đồ án.
- [**`TEST_PLAN.md`**](file:///TEST_PLAN.md): Kế hoạch kiểm thử chi tiết 3 tầng.
- [**`TEST_RESULTS.md`**](file:///TEST_RESULTS.md): Nhật ký thực chạy kiểm thử, môi trường và bằng chứng log.
- [**`DEMO_GUIDE.md`**](file:///DEMO_GUIDE.md): Cẩm nang hướng dẫn demo trên 1 máy tính và demo qua mạng LAN đa máy, kèm bộ câu hỏi vấn đáp.
- [**`sql_patches/audit_inspect_db.sql`**](file:///sql_patches/audit_inspect_db.sql): Kịch bản kiểm tra an toàn dữ liệu CSDL ở chế độ Read-Only.
