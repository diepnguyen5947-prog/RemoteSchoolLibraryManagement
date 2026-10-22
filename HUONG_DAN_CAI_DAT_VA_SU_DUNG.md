# 📖 HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG HỆ THỐNG
# QUẢN LÝ THƯ VIỆN TRƯỜNG HỌC TỪ XA (REMOTE SCHOOL LIBRARY)

> **Học phần:** CMP180 – Lập trình mạng máy tính  
> **Nhóm thực hiện:** Nhóm em  
> Tài liệu này được nhóm em biên soạn chi tiết để thầy cô và các bạn sinh viên có thể dễ dàng cài đặt, biên dịch và chạy thử nghiệm toàn bộ hệ thống từ một máy đơn đến mạng LAN nhiều máy.

---

## 📋 MỤC LỤC
1. [Yêu cầu phần mềm trước khi chạy](#1-yeu-cau-phan-mem-truoc-khi-chay)
2. [Hướng dẫn cài đặt nhanh trong 3 bước](#2-huong-dan-cai-dat-nhanh-trong-3-buoc)
3. [Danh sách tài khoản kiểm thử có sẵn](#3-danh-sach-tai-khoan-kiem-thu-co-san)
4. [Hướng dẫn sử dụng chi tiết theo vai trò](#4-huong-dan-su-dung-chi-tiet-theo-vai-tro)
   - [Dành cho Thủ thư (Librarian)](#41-danh-cho-thu-thu-librarian)
   - [Dành cho Độc giả (Reader)](#42-danh-cho-doc-gia-reader)
5. [Hướng dẫn chạy bộ kiểm thử 3 tầng độc lập](#5-huong-dan-chay-bo-kiem-thu-3-tang-doc-lap)
6. [Các lỗi thường gặp và cách khắc phục nhanh](#6-cac-loi-thuong-gap-va-cach-khac-phuc-nhanh)

---

## 1. YÊU CẦU PHẦN MỀM TRƯỚC KHI CHẠY
Trước khi bắt đầu, hãy đảm bảo máy tính đã cài đặt:
1. **Java Development Kit (JDK)**: JDK 17 LTS (Kiểm tra bằng lệnh `java -version` trong Command Prompt).
2. **Microsoft SQL Server**: Phiên bản 2016, 2019, 2022 hoặc SQL Server Express (chạy cổng 1433).
3. **SQL Server Management Studio (SSMS)** hoặc Azure Data Studio.
4. **Trình duyệt web**: Google Chrome, Microsoft Edge, Firefox.

---

## 2. HƯỚNG DẪN CÀI ĐẶT NHANH TRONG 3 BƯỚC

### 🔹 BƯỚC 1: TẠO CƠ SỞ DỮ LIỆU THUVIENDB
1. Mở phần mềm **SQL Server Management Studio (SSMS)** và đăng nhập vào SQL Server trên máy.
2. Mở file `setup_thuviendb.sql` (ở thư mục gốc của project) và nhấn **Execute (F5)**.
3. Chạy file kiểm tra tự động `verify_thuviendb.sql` để xác minh:
   ```bat
   sqlcmd -S 127.0.0.1,1433 -U sa -P 123456 -d ThuVienDB -i verify_thuviendb.sql
   ```
   👉 Khi màn hình hiện `7 / 7 CA KIỂM THỬ ĐẠT (PASS)` là CSDL đã hoàn tất thiết lập chuẩn mực!

> 💡 **Cấu hình mật khẩu CSDL (`db.properties`)**:  
> Dự án nạp thông số kết nối CSDL từ file [**`db.properties`**](file:///db.properties) ở thư mục gốc (mặc định user `sa`, pass `123456`). Nếu SQL Server của bạn dùng tài khoản hoặc mật khẩu khác, bạn chỉ cần mở file `db.properties` và sửa lại thông số tương ứng mà không cần sửa code.

---

### 🔹 BƯỚC 2: BIÊN DỊCH TOÀN BỘ MÃ NGUỒN
1. Mở thư mục dự án.
2. Nhấp đúp chuột trái vào file: 👉 **`compile.bat`**
3. Script sẽ tự động dọn dẹp thư mục `bin/`, quét toàn bộ các file `.java` trong `src/` cùng các file kiểm thử, và biên dịch sạch sẽ.
4. Khi thấy thông báo `[THANH CONG] BIEN DICH HOAN TAT TOAN BO DU AN!`, nhấn phím bất kỳ để đóng cửa sổ.

---

### 🔹 BƯỚC 3: KHỞI CHẠY HỆ THỐNG THEO THỨ TỰ

1. **Khởi chạy RMI Server (Bắt buộc chạy trước)**:
   - Nhấp đúp vào file: 👉 **`run_server.bat`**
   - Đợi màn hình hiện:
     ```
     [1] Đang kiểm tra kết nối tới Microsoft SQL Server...
         -> KẾT NỐI CSDL THÀNH CÔNG! Sẵn sàng xử lý nghiệp vụ thư viện.
     [2] Đang khởi tạo RMI Registry trên cổng 1099...
     [3] Đang khởi tạo và export Remote Object trên cổng dịch vụ 1098...
     ====================================================================
       [TRẠNG THÁI: HEALTHY] RMI SERVER SẴN SÀNG TIẾP NHẬN YÊU CẦU TỪ CÁC CLIENT!
     ====================================================================
     ```
   - *Lưu ý: Giữ nguyên cửa sổ đen này chạy nền.*

2. **Khởi chạy Java Console Client (Demo RMI trực tiếp)**:
   - Nhấp đúp vào file: 👉 **`run_client.bat`**
   - Ứng dụng Console kết nối trực tiếp tới cổng RMI 1099, không thông qua HTTP, phục vụ thao tác độc lập cho cả Độc giả và Thủ thư.

3. **Khởi chạy Web Gateway (Cổng Web Portal)**:
   - Nhấp đúp vào file: 👉 **`run_web_client.bat`**
   - Cửa sổ console sẽ hiện máy chủ Web đang chạy tại cổng `8080`.
   - Mở trình duyệt Web tại: 👉 **`http://localhost:8080/login`**

---

## 3. DANH SÁCH TÀI KHOẢN KIỂM THỬ CÓ SẴN

Tất cả các tài khoản mẫu đều dùng mật khẩu chung: **`123456`**.

| STT | Phân quyền | Email đăng nhập | Mật khẩu | Ghi chú vai trò |
| :---: | :--- | :--- | :---: | :--- |
| **1** | 👨‍💼 **Thủ Thư (Librarian)** | `admin@school.edu.vn` | `123456` | Toàn quyền quản trị kho sách, duyệt giao sách tại quầy, thẩm định nhận trả sách, thu đền bù, mở khóa tài khoản, xem thống kê. |
| **2** | 🎓 **Độc Giả 1 (Reader)** | `nguyenvanan@school.edu.vn` | `123456` | Tài khoản độc giả chuẩn (đã có lịch sử trả sách mẫu). |
| **3** | 🎓 **Độc Giả 2 (Reader)** | `tranthibinh@school.edu.vn` | `123456` | Độc giả mẫu dùng để kiểm thử mượn trùng cuốn sách đang giữ chỗ. |
| **4** | 🎓 **Độc Giả 3 (Reader)** | `lehoangcuong@school.edu.vn` | `123456` | Độc giả đang mượn cuốn *Đắc nhân tâm*. |
| **5** | 🎓 **Độc Giả 4 (Reader)** | `phamthidung@school.edu.vn` | `123456` | Độc giả đang có phiếu giữ chỗ trực tuyến sách *Kẻ Trộm Sách*. |

> 🌟 **Tạo tài khoản mới**: Bạn có thể nhấn tab **"Tạo tài khoản mới"** tại trang đăng nhập để tự tạo tài khoản độc giả mới với họ tên, email, mật khẩu và số điện thoại của mình.

---

## 4. HƯỚNG DẪN SỬ DỤNG CHI TIẾT THEO VAI TRÒ

### 4.1. Dành cho Thủ thư (Librarian)
Đăng nhập tài khoản `admin@school.edu.vn` / `123456`:
1. **Bảng điều khiển (`/admin/dashboard`)**: Xem tổng quan số lượng sách, số sách đang mượn, số phiếu giữ chỗ, và thống kê vi phạm.
2. **Quản lý Kho Sách (`/admin/books`)**: Tra cứu, thêm mới sách, bổ sung sách mới thay thế cho sách bị mất, chỉnh sửa thông tin sách.
3. **Quản lý Mượn / Trả Sách (`/admin/loans`)**:
   - **Xác nhận giao sách**: Nhấn **"Giao Sách"** cho độc giả đến quầy nhận sách -> Phiếu chuyển từ *Chờ lấy (0)* sang *Đang mượn (1)*.
   - **Nhận sách & Hoàn tất trả**: Khi độc giả mang sách trả, Thủ thư nhấn **"Nhận Sách & Trả"**, chọn trạng thái *Nguyên vẹn* (nền xanh) hoặc *Hư hại* (nhập phụ phí) -> Sách quay về *Sẵn sàng (0)*.
4. **Lịch Sử Mượn Sách (`/admin/loan-history`)**: Xem toàn bộ lịch sử các phiếu đã hoàn tất, nhấn **"Chi tiết"** để xem trạng thái sách và quyết toán tài chính.
5. **Mở khóa tài khoản**: Nhấn mở khóa cho độc giả sau khi đã nộp phạt / giải quyết đền bù.

### 4.2. Dành cho Độc giả (Reader)
Đăng nhập tài khoản `nguyenvanan@school.edu.vn` / `123456`:
1. **Tra cứu & Giữ chỗ trực tuyến (`/reader/books`)**: Tìm kiếm sách, nhấn **"Mượn Sách"** -> Hệ thống tạo phiếu giữ chỗ trong 24 giờ.
2. **Quản lý Phiếu mượn cá nhân (`/reader/my-loans`)**: Xem các cuốn sách đang mượn, hạn trả; gửi **"Yêu cầu trả sách"** hoặc **"Báo Mất Sách"**.
3. **Sách Yêu Thích & Phản Hồi**: Quản lý danh sách sách muốn đọc và gửi báo cáo chất lượng sách cho thư viện.

---

## 5. HƯỚNG DẪN CHẠY BỘ KIỂM THỬ 3 TẦNG ĐỘC LẬP

Nhóm em đã chuẩn bị sẵn các script kiểm thử tự động toàn diện:

### 1️⃣ Kiểm thử Tầng A: Tuần tự hóa đối tượng (Bài 6 - Java Serialization)
```bat
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.SerializationTest
```
*(Kết quả: 6 / 6 ca kiểm thử PASS)*.

### 2️⃣ Kiểm thử Tầng B: Tích hợp RMI thật & SQL Server thật (Bài 9 - Java RMI)
*(Yêu cầu run_server.bat đang chạy)*:
```bat
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiIntegrationTest
```
*(Kết quả: 13 / 13 ca kiểm thử PASS)*.

### 3️⃣ Kiểm thử Tầng C: Tranh chấp mượn sách đồng thời (Bài 3 & SQL Invariant)
Nhấp đúp chuột vào file: 👉 **`run_concurrency_test.bat`**
- Chọn `1` để chạy kiểm thử trực tiếp qua RMI với 2 Client hoặc 10 Client đồng thời:
  ```bat
  java -Dfile.encoding=UTF-8 -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 10 1
  ```
- Kết quả: **Chính xác 1 Client thành công**, 9 Client còn lại nhận mã lỗi `BOOK_NOT_AVAILABLE`, CSDL ghi nhận đúng 1 phiếu chiếm dụng duy nhất.
- Chọn `2` để chạy kiểm thử qua Web Gateway HTTP (`ConcurrencyTest`).

### 4️⃣ Kiểm tra toàn vẹn CSDL (Read-Only Audit)
```bat
sqlcmd -S 127.0.0.1,1433 -U sa -P 123456 -d ThuVienDB -i sql_patches/audit_inspect_db.sql
```

---

## 6. CÁC LỖI THƯỜNG GẶP VÀ CÁCH KHẮC PHỤC NHANH

### 🔴 Lỗi 1: `Connection refused: connect`
- **Nguyên nhân**: SQL Server chưa bật giao thức mạng TCP/IP hoặc dịch vụ SQL Server chưa khởi động.
- **Cách khắc phục**:
  1. Mở menu Start gõ **SQL Server Configuration Manager**.
  2. Chọn mục **SQL Server Network Configuration** -> **Protocols for MSSQLSERVER**.
  3. Nhấp đúp vào **TCP/IP** -> Chuyển **Enabled** thành **Yes**.
  4. Sang tab **IP Addresses**, cuộn xuống dưới cùng tại mục **IPAll**, đặt **TCP Port** là `1433`.
  5. Restart lại SQL Server (hoặc nhấp đúp file `restart_sql_server.bat` bằng quyền Administrator).

### 🔴 Lỗi 2: `Login failed for user 'sa'`
- **Nguyên nhân**: Mật khẩu tài khoản `sa` của SQL Server trên máy khác với `123456`.
- **Cách khắc phục**:
  1. Mở file [**`db.properties`**](file:///db.properties) ở thư mục gốc.
  2. Sửa dòng `db.password=123456` thành mật khẩu SQL Server của máy bạn.
  3. Chạy lại `run_server.bat` (không cần phải sửa file `.java` hay biên dịch lại).

### 🔴 Lỗi 3: `Port 1099 already in use` hoặc `Port 8080 already in use`
- **Nguyên nhân**: Một tiến trình server cũ đang chạy ngầm.
- **Cách khắc phục**:
  1. Mở **Task Manager** (Ctrl + Shift + Esc).
  2. Tìm trong tab *Processes* tiến trình `java.exe` hoặc `javaw.exe`.
  3. Nhấn **End Task** để giải phóng cổng, sau đó khởi động lại.
