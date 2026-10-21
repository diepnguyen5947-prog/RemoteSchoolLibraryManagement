# 📖 HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG HỆ THỐNG
# QUẢN LÝ MƯỢN/TRẢ SÁCH THƯ VIỆN TRƯỜNG HỌC (REMOTE SCHOOL LIBRARY)

> Tài liệu này được biên soạn dành cho bạn bè trong nhóm và giảng viên để có thể cài đặt, chạy thử và kiểm tra toàn bộ các tính năng của hệ thống một cách nhanh chóng và thuận tiện nhất.

---

## 📋 MỤC LỤC
1. [Yêu cầu phần mềm trước khi chạy](#1-yeu-cau-phan-mem-truoc-khi-chay)
2. [Hướng dẫn cài đặt nhanh trong 3 bước](#2-huong-dan-cai-dat-nhanh-trong-3-buoc)
3. [Danh sách tài khoản kiểm thử có sẵn](#3-danh-sach-tai-khoan-kiem-thu-co-san)
4. [Hướng dẫn sử dụng chi tiết theo vai trò](#4-huong-dan-su-dung-chi-tiet-theo-vai-tro)
   - [Dành cho Thủ thư (Librarian)](#41-danh-cho-thu-thu-librarian)
   - [Dành cho Độc giả (Reader)](#42-danh-cho-doc-gia-reader)
5. [Hướng dẫn chạy kiểm thử tranh chấp đồng thời (Concurrency Test)](#5-huong-dan-chay-kiem-thu-tranh-chap-dong-thoi)
6. [Các lỗi thường gặp và cách khắc phục nhanh](#6-cac-loi-thuong-gap-va-cach-khac-phuc-nhanh)

---

## 1. YÊU CẦU PHẦN MỀM TRƯỚC KHI CHẠY
Trước khi bắt đầu, hãy đảm bảo máy tính của bạn đã có:
1. **Java Development Kit (JDK)**: JDK 11, JDK 17 hoặc JDK 21 trở lên.
   - *Cách kiểm tra*: Mở Command Prompt (cmd) gõ `java -version`.
2. **Microsoft SQL Server**: Bản bất kỳ (2014, 2016, 2019, 2022 hoặc Express).
3. **SQL Server Management Studio (SSMS)**: Dùng để chạy file tạo CSDL.
4. **Trình duyệt web**: Chrome, Edge, Firefox, Cốc Cốc...

---

## 2. HƯỚNG DẪN CÀI ĐẶT NHANH TRONG 3 BƯỚC

### 🔹 BƯỚC 1: TẠO CƠ SỞ DỮ LIỆU THUVIENDB
1. Mở phần mềm **SQL Server Management Studio (SSMS)** và đăng nhập vào SQL Server trên máy của bạn.
2. Kéo thả file `setup_thuviendb.sql` (ở thư mục gốc của project) vào cửa sổ SSMS.
3. Nhấn nút **Execute** (hoặc bấm phím **F5**).
4. Quan sát cửa sổ Messages phía dưới, khi thấy dòng chữ:
   ```
   =================================================================================
   ĐÃ TẠO TOÀN DIỆN VÀ HOÀN TẤT THIẾT LẬP CƠ SỞ DỮ LIỆU ThuVienDB THÀNH CÔNG!
   =================================================================================
   ```
   👉 Có nghĩa là toàn bộ 5 bảng dữ liệu, 9 Stored Procedures và các tài khoản mẫu đã được tạo xong 100%!

> **Lưu ý về mật khẩu SQL Server**: Dự án mặc định dùng tài khoản `sa` với mật khẩu `123456`. Hệ thống cũng đã tích hợp sẵn cơ chế tự động thử các mật khẩu phổ biến (`123456`, `Password123!`, `Admin@123`, `12345678`, `root`). Nếu mật khẩu SQL Server trên máy bạn khác, bạn chỉ cần mở file `src/main/java/com/library/dao/DBConnection.java` và thêm mật khẩu của bạn vào dòng 19.

---

### 🔹 BƯỚC 2: BIÊN DỊCH DỰ ÁN
1. Mở thư mục dự án vừa tải về.
2. Nhấp đúp chuột trái vào file:
   👉 **`compile.bat`**
3. Script sẽ tự động tìm trình biên dịch `javac` và biên dịch toàn bộ các file mã nguồn Java vào thư mục `bin/`.
4. Khi thấy thông báo `[THANH CONG] BIEN DICH HOAN TAT!`, bạn nhấn phím bất kỳ để đóng cửa sổ.

---

### 🔹 BƯỚC 3: KHỞI CHẠY HỆ THỐNG
Hệ thống gồm 2 thành phần máy chủ, bạn chỉ cần nhấp đúp 2 file `.bat` sau theo thứ tự:

1. **Khởi chạy RMI Server**:
   - Nhấp đúp vào file: 👉 **`run_server.bat`**
   - Đợi 2-3 giây màn hình hiện thông báo RMI Server đang lắng nghe tại cổng `1099`.
   - *Lưu ý: Giữ nguyên cửa sổ đen này, không tắt đi.*

2. **Khởi chạy Web Client Server**:
   - Nhấp đúp vào file: 👉 **`run_web_client.bat`**
   - Màn hình hiện thông báo máy chủ web đang chạy tại cổng `8080`.
   - *Lưu ý: Giữ nguyên cửa sổ đen này, không tắt đi.*

3. **Mở trình duyệt Web**:
   - Truy cập vào địa chỉ: 👉 **`http://localhost:8080/login`**

---

## 3. DANH SÁCH TÀI KHOẢN KIỂM THỬ CÓ SẴN

Tất cả các tài khoản mẫu đều dùng mật khẩu tiện lợi là: `123456`.

| STT | Phân quyền | Email đăng nhập | Mật khẩu | Ghi chú vai trò |
| :---: | :--- | :--- | :---: | :--- |
| **1** | 👨‍💼 **Thủ Thư (Admin)** | `admin@school.edu.vn` | `123456` | Toàn quyền quản trị kho sách, duyệt giao sách, thẩm định trả sách, đền bù mất sách, mở khóa tài khoản, xem thống kê. |
| **2** | 🎓 **Độc Giả 1** | `nguyenvanan@school.edu.vn` | `123456` | Tài khoản độc giả bình thường (đã có lịch sử trả sách mẫu nguyên vẹn). |
| **3** | 🎓 **Độc Giả 2** | `tranthibinh@school.edu.vn` | `123456` | Độc giả có lịch sử trả sách hỏng kèm phụ thu bảo quản 20.000 VNĐ. |
| **4** | 🎓 **Độc Giả 3** | `lehoangcuong@school.edu.vn` | `123456` | Độc giả đang mượn cuốn *Đắc nhân tâm*. |
| **5** | 🎓 **Độc Giả 4** | `phamthidung@school.edu.vn` | `123456` | Độc giả đang có 1 phiếu giữ chỗ trực tuyến sách *Kẻ Trộm Sách*. |

> 🌟 **Tạo tài khoản mới**: Bạn hoàn toàn có thể nhấn vào tab **"Tạo tài khoản mới"** ngay tại màn hình đăng nhập để tự đăng ký một tài khoản độc giả mới với họ tên, email, mật khẩu và số điện thoại của chính mình.

---

## 4. HƯỚNG DẪN SỬ DỤNG CHI TIẾT THEO VAI TRÒ

### 4.1. Dành cho Thủ thư (Librarian)
Sau khi đăng nhập với tài khoản `admin@school.edu.vn`:

1. **Bảng điều khiển & Thống kê (`/admin/dashboard`)**:
   - Xem tổng số lượng sách, số lượng sách đang cho mượn, số phiếu giữ chỗ trực tuyến chờ nhận, tổng doanh thu phạt quá hạn và phụ thu đền bù.
2. **Quản lý Kho Sách (`/admin/books`)**:
   - Xem danh mục sách, tìm kiếm theo tên/tác giả.
   - Thêm mới đầu sách, chỉnh sửa giá tiền, cập nhật thông tin sách, ảnh bìa.
   - Xóa sách hoặc phục hồi lại sách bị ngừng phục vụ.
3. **Quản lý Mượn / Trả Sách (`/admin/loans`)**:
   - **Xác nhận giao sách tại quầy**: Với các phiếu ở trạng thái *"Chờ lấy sách"*, khi độc giả đến quầy xuất trình thông tin, Thủ thư nhấn **"Giao Sách"** để chuyển sang *"Đang mượn"*.
   - **Nhận sách & Hoàn tất trả**: Khi độc giả mang sách đến trả, Thủ thư nhấn **"Nhận Sách & Trả"**. Một bảng thẩm định tình trạng sẽ hiện ra:
     - Chọn **Nguyên vẹn**: Không tính thêm phụ thu hư hại (background xanh lá cây).
     - Chọn **Hỏng hóc / Hư hại**: Nhập số tiền bồi hoàn phụ thu (hệ thống tự động cộng dồn với tiền phạt quá hạn nếu có).
4. **Lịch Sử Mượn Sách (`/admin/loan-history`)**:
   - Lưu trữ toàn bộ các phiếu mượn đã hoàn tất trả sách.
   - Cột Thao tác có nút **"Chi tiết"**: Khi bấm vào sẽ hiển thị Modal chi tiết bao gồm trạng thái sách khi nhận từ độc giả và bảng quyết toán tài chính (tiền phạt, phụ thu, đền bù).
5. **Quản lý Vi phạm & Đền bù (`/admin/violations`)**:
   - Danh sách các độc giả có sách bị báo mất hoặc sách quá hạn > 10 ngày.
   - Nút **"Thu tiền đền bù"**: Xác nhận độc giả đã nộp tiền đền bù tại quầy, hoàn tất giải quyết phiếu mượn và tự động mở khóa tài khoản cho độc giả.
   - Nút **"Mở khóa tài khoản"**: Mở khóa thủ công cho độc giả khi đã khắc phục đầy đủ các vi phạm.
6. **Quản lý Phản hồi (`/admin/feedback`)**:
   - Tiếp nhận báo cáo sự cố, đề xuất sách mới từ độc giả và phản hồi ghi chú.

---

### 4.2. Dành cho Độc giả (Reader)
Sau khi đăng nhập với tài khoản độc giả (ví dụ: `nguyenvanan@school.edu.vn`):

1. **Tra cứu & Mượn sách trực tuyến (`/reader/books`)**:
   - Tìm kiếm sách theo tên, tác giả, giá tiền.
   - Bấm **"Mượn Sách"**: Độc giả được chủ động chọn thời hạn mượn (ví dụ: 7 ngày, 14 ngày, 21 ngày, 30 ngày).
   - Hệ thống tự động giữ chỗ sách trong vòng **24 giờ**. Trong 24h này sách được bảo lưu riêng cho độc giả và người khác không thể mượn trùng.
2. **Quản lý Phiếu mượn cá nhân (`/reader/my-loans`)**:
   - Xem danh sách sách đang mượn, hạn trả cụ thể và cảnh báo tình trạng (Trong hạn / Quá hạn).
   - Nút **"Yêu cầu trả sách"**: Thông báo trước cho thủ thư chuẩn bị tiếp nhận tại quầy.
   - Nút **"Báo Mất Sách"**: Sử dụng khi làm mất sách. Hệ thống sẽ hiển thị bảng tính tiền đền bù minh bạch (Giá sách + tiền phạt quá hạn nếu có) và chuyển trạng thái chờ đền bù tại quầy.
3. **Sách Yêu Thích (`/reader/favorites`)**:
   - Lưu các cuốn sách hay muốn đọc sau bằng cách nhấn nút hình trái tim.
4. **Gửi Phản Hồi (`/reader/feedback`)**:
   - Gửi góp ý, đề xuất sách mới hoặc báo cáo chất lượng sách cho nhà trường.

---

## 5. HƯỚNG DẪN CHẠY KIỂM THỬ TRANH CHẤP ĐỒNG THỜI
*(Tính năng ghi điểm rất cao khi báo cáo đồ án Hệ phân tán & Cơ sở dữ liệu)*

Để chứng minh hệ thống xử lý hoàn hảo vấn đề **Race Condition / Concurrency Control** (Nhiều độc giả cùng bấm mượn 1 cuốn sách trong cùng 1 thời điểm):

1. Mở thư mục dự án.
2. Nhấp đúp chuột vào file:
   👉 **`run_concurrency_test.bat`**
3. Kịch bản test:
   - Hệ thống tự động tạo **10 luồng song song (Threads)** chạy đồng thời.
   - 10 luồng cùng gửi lệnh mượn cuốn sách số 1 tại cùng một mili-giây.
4. Kết quả quan sát trên màn hình:
   - **Chính xác 1 luồng thành công** (Ghi nhận mã phiếu mượn mới).
   - **9 luồng còn lại bị chặn an toàn** bởi cơ chế khóa bi quan `UPDLOCK, ROWLOCK, HOLDLOCK` trong Stored Procedure SQL Server với thông báo: *"Cuốn sách hiện đang được mượn bởi độc giả khác. Không thể mượn trùng!"*.
   - Số lượng sách trong kho không bao giờ bị âm hoặc sai lệch.

---

## 6. CÁC LỖI THƯỜNG GẶP VÀ CÁCH KHẮC PHỤC NHANH

### 🔴 Lỗi 1: `Connection refused: connect`
- **Nguyên nhân**: SQL Server chưa bật giao thức mạng TCP/IP hoặc dịch vụ SQL Server chưa khởi động.
- **Cách khắc phục**:
  1. Mở menu Start gõ **SQL Server Configuration Manager**.
  2. Chọn mục **SQL Server Network Configuration** -> **Protocols for MSSQLSERVER**.
  3. Nhấp đúp vào **TCP/IP** -> Chuyển **Enabled** thành **Yes**.
  4. Sang tab **IP Addresses**, cuộn xuống dưới cùng tại mục **IPAll**, đặt **TCP Port** là `1433`.
  5. Nhấp chuột phải vào dịch vụ **SQL Server** chọn **Restart** (hoặc nhấp đúp file `restart_sql_server.bat` bằng quyền Run as Administrator).

---

### 🔴 Lỗi 2: `Login failed for user 'sa'`
- **Nguyên nhân**: Mật khẩu tài khoản `sa` của SQL Server trên máy bạn không phải là `123456`.
- **Cách khắc phục**:
  1. Mở file: `src/main/java/com/library/dao/DBConnection.java`.
  2. Tại dòng 19, sửa mật khẩu trong mảng `CANDIDATE_PASSWORDS` thành mật khẩu SQL Server của bạn.
  3. Nhấp đúp file `compile.bat` để biên dịch lại, sau đó chạy lại `run_server.bat`.

---

### 🔴 Lỗi 3: `Port 1099 already in use` hoặc `Port 8080 already in use`
- **Nguyên nhân**: Một cửa sổ server cũ chưa tắt hẳn hoặc có ứng dụng khác đang chiếm cổng.
- **Cách khắc phục**:
  1. Mở **Task Manager** (Ctrl + Shift + Esc).
  2. Tìm trong tab *Processes* hoặc *Details* các tiến trình tên là `java.exe` hoặc `javaw.exe`.
  3. Nhấn **End Task** để giải phóng cổng.
  4. Khởi động lại `run_server.bat` và `run_web_client.bat`.

---

🎉 **Chúc bạn và nhóm có buổi báo cáo đồ án thành công xuất sắc và đạt điểm tối đa!**
