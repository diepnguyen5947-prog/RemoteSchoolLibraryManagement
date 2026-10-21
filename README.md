# 📚 HỆ THỐNG QUẢN LÝ MƯỢN/TRẢ SÁCH THƯ VIỆN TRƯỜNG HỌC PHÂN TÁN
> **Distributed Remote School Library Management System**  
> Kiến trúc: **3-Tier Architecture** | **Java RMI** | **Microsoft SQL Server (Transact-SQL)** | **Web Portal**

---

## 🌟 1. GIỚI THIỆU TỔNG QUAN

Dự án **Hệ thống Quản lý Thư viện Trường học Phân tán** giải quyết bài toán mượn/trả sách trực tuyến kết hợp phục vụ tại quầy, giải quyết triệt để vấn đề **tranh chấp đồng thời (Race Condition)** và đảm bảo tính toàn vẹn dữ liệu giao dịch mượn/trả sách.

### 🏛️ Kiến trúc hệ thống 3 tầng (3-Tier Architecture):
1. **Tầng Cơ sở Dữ liệu (Database Tier - Microsoft SQL Server)**:
   - Quản trị CSDL tập trung `ThuVienDB` (Collation: `Vietnamese_CI_AS`).
   - Sử dụng **Stored Procedures**, **Transaction** và cơ chế khóa độc quyền bi quan (**Pessimistic Locking**: `UPDLOCK`, `ROWLOCK`, `HOLDLOCK`) để ngăn chặn tình trạng mượn trùng cuốn sách khi nhiều độc giả thao tác cùng thời điểm.
   - Tự động hóa chính sách khóa tài khoản: Khóa do có sách quá hạn > 10 ngày hoặc báo mất sách chờ đền bù.
2. **Tầng Ứng dụng & Dịch vụ (Application / RMI Server Tier)**:
   - Java RMI Server (`ServerMain`), lắng nghe tại cổng RMI **1099**.
   - Cung cấp giao diện dịch vụ từ xa `ILibraryService` điều phối dữ liệu qua các DAO (`SachDAO`, `PhieuMuonDAO`, `DocGiaDAO`, `PhanHoiDAO`, `YeuThichDAO`).
3. **Tầng Giao diện Người dùng (Presentation / Client Tier)**:
   - **Cổng thông tin Web Client** (`WebServerApp` chạy cổng **8080**): Giao diện hiện đại, trực quan xây dựng trên nền tảng Bootstrap 5, SweetAlert2, Font Awesome.
   - Hỗ trợ 2 phân quyền người dùng chuyên biệt:
     - 👨‍💼 **Thủ thư (Librarian)**: Quản lý kho sách, duyệt giao sách tại quầy, thẩm định trạng thái sách khi trả (Nguyên vẹn / Hư hại), quyết toán đền bù mất sách, mở khóa tài khoản, thống kê doanh thu phạt/đền bù.
     - 🎓 **Độc giả (Reader)**: Tìm kiếm sách, đăng ký giữ chỗ trực tuyến (tùy chọn số ngày mượn 7, 14, 21, 30 ngày), theo dõi hạn trả, yêu cầu trả sách, báo mất sách, quản lý danh sách yêu thích và phản hồi sự cố.

---

## ⚙️ 2. YÊU CẦU MÔI TRƯỜNG (PREREQUISITES)

Để chạy dự án, máy tính cần cài đặt sẵn:
- **Hệ điều hành**: Windows 10/11 (khuyến nghị), macOS hoặc Linux.
- **Java Development Kit (JDK)**: JDK 11, JDK 17 hoặc JDK 21 (Kiểm tra bằng lệnh `java -version` trong Command Prompt).
- **Hệ quản trị CSDL**: Microsoft SQL Server 2016 / 2019 / 2022 hoặc SQL Server Express.
- **Công cụ CSDL**: SQL Server Management Studio (SSMS) hoặc Azure Data Studio.
- **Trình duyệt Web**: Google Chrome, Microsoft Edge, Firefox, Brave, Cốc Cốc.

---

## 🚀 3. HƯỚNG DẪN CÀI ĐẶT & KHỞI CHẠY (QUICK START)

### 📌 Bước 1: Khởi tạo Cơ sở Dữ liệu `ThuVienDB`
1. Khởi động **SQL Server Management Studio (SSMS)** và kết nối tới SQL Server của bạn.
2. Mở file [**`setup_thuviendb.sql`**](file:///setup_thuviendb.sql) nằm tại thư mục gốc của dự án.
3. Nhấn nút **Execute** (hoặc phím tắt **F5**).
4. *Hoặc chạy nhanh bằng lệnh trong Terminal (Command Prompt / PowerShell):*
   ```bat
   sqlcmd -S 127.0.0.1,1433 -U sa -P 123456 -i setup_thuviendb.sql
   ```
> Script sẽ tự động tạo cơ sở dữ liệu `ThuVienDB`, 5 bảng dữ liệu, 9 Stored Procedures và toàn bộ dữ liệu mẫu ban đầu.

---

### 📌 Bước 2: Kiểm tra cấu hình kết nối SQL Server
Mặc định hệ thống kết nối tới SQL Server với thông số:
- **Host**: `localhost:1433`
- **Database**: `ThuVienDB`
- **User**: `sa`
- **Mật khẩu mặc định**: `123456`

*(Lớp `DBConnection.java` đã được tích hợp cơ chế tự động thử danh sách mật khẩu phổ biến: `123456`, `Password123!`, `Admin@123`, `12345678`, `root`. Nếu SQL Server của bạn đặt mật khẩu khác, bạn có thể chỉnh tại dòng 19 file `src/main/java/com/library/dao/DBConnection.java`)*.

---

### 📌 Bước 3: Biên dịch mã nguồn Java
Nhấp đúp chuột vào file:
👉 **`compile.bat`**
- Script sẽ tự động dò tìm JDK và biên dịch toàn bộ các package Java vào thư mục `bin/`.
- Khi màn hình hiển thị `[THANH CONG] BIEN DICH HOAN TAT!` là đã sẵn sàng.

---

### 📌 Bước 4: Khởi chạy hệ thống

Khởi chạy theo đúng thứ tự 2 bước sau:

#### 1️⃣ Bật Server RMI:
Nhấp đúp chuột vào file:
👉 **`run_server.bat`**
- Cửa sổ console sẽ hiện: `RMI Registry da khoi tao tai cong 1099` và `LibraryService da dang ky thanh cong!`
*(Giữ nguyên cửa sổ này đang chạy)*.

#### 2️⃣ Bật Web Server Client:
Nhấp đúp chuột vào file:
👉 **`run_web_client.bat`**
- Cửa sổ console sẽ hiện: `KHOI CHAY WEB CLIENT SERVER - CONG 8080 | URL: http://localhost:8080`
*(Giữ nguyên cửa sổ này đang chạy)*.

#### 3️⃣ Truy cập ứng dụng:
Mở trình duyệt Web và truy cập:
👉 **[http://localhost:8080/login](http://localhost:8080/login)**

---

## 🔑 4. DANH SÁCH TÀI KHOẢN ĐĂNG NHẬP MẪU

Hệ thống đã chuẩn bị sẵn các tài khoản mẫu để bạn và thầy cô dễ dàng kiểm thử ngay:

| Vai trò | Họ tên | Email đăng nhập | Mật khẩu | Mô tả quyền hạn |
| :--- | :--- | :--- | :--- | :--- |
| 👨‍💼 **Thủ Thư** | Thủ Thư Quản Trị | `admin@school.edu.vn` | `123456` | Quản lý toàn bộ kho sách, duyệt giao sách tại quầy, trả sách & thẩm định, thu đền bù, mở khóa tài khoản, xem thống kê. |
| 🎓 **Độc Giả 1** | Nguyễn Văn An | `nguyenvanan@school.edu.vn` | `123456` | Độc giả chuẩn, có lịch sử mượn trả mẫu nguyên vẹn. |
| 🎓 **Độc Giả 2** | Trần Thị Bình | `tranthibinh@school.edu.vn` | `123456` | Độc giả mẫu có lịch sử trả sách hỏng kèm phụ thu bồi hoàn. |
| 🎓 **Độc Giả 3** | Lê Hoàng Cường | `lehoangcuong@school.edu.vn` | `123456` | Đang trong hạn mượn cuốn *Đắc nhân tâm*. |
| 🎓 **Độc Giả 4** | Phạm Thị Dung | `phamthidung@school.edu.vn` | `123456` | Đang có 1 phiếu giữ chỗ trực tuyến chờ nhận tại quầy. |

> 💡 **Tính năng Tạo tài khoản mới**: Bạn cũng có thể nhấn tab **"Tạo tài khoản mới"** tại trang đăng nhập để tự tạo tài khoản độc giả theo thông tin riêng (hệ thống có kiểm tra form validation đầy đủ, chống bỏ trống và báo lỗi viền đỏ trực quan).

---

## 🎯 5. KỊCH BẢN KIỂM THỬ DEMO (DEMO SCENARIOS)

### 📌 Kịch bản 1: Mượn sách trực tuyến & Nhận sách tại quầy (Hold & Pickup)
1. Đăng nhập tài khoản Độc giả: `nguyenvanan@school.edu.vn` / `123456`.
2. Vào **Kho Sách**, chọn một cuốn sách đang ở trạng thái **Sẵn sàng** (ví dụ: *Cơ sở dữ liệu nâng cao*).
3. Nhấn **Mượn Sách**, chọn thời hạn mượn (ví dụ: 14 ngày) -> Hệ thống báo giữ chỗ thành công với hạn giữ 24 giờ. Trạng thái phiếu là **"Chờ lấy sách"**.
4. Mở tab ẩn danh hoặc đăng xuất, đăng nhập tài khoản Thủ thư: `admin@school.edu.vn` / `123456`.
5. Vào **Quản Lý Mượn/Trả**, tìm phiếu vừa tạo -> Nhấn **Giao Sách**.
6. Phiếu mượn lập tức chuyển sang **"Đang mượn"** và tính hạn trả chính xác.

### 📌 Kịch bản 2: Trả sách & Thẩm định tình trạng tại quầy
1. Tại trang **Quản Lý Mượn/Trả** của Thủ thư, chọn một phiếu đang ở trạng thái **"Đang mượn"** -> Nhấn **Nhận Sách & Trả**.
2. Một hộp thoại thẩm định sách hiện ra:
   - Nếu chọn **"Nguyên vẹn"**: Phụ thu = 0đ, trạng thái hiển thị màu xanh lá.
   - Nếu chọn **"Hỏng hóc / Hư hại"**: Nhập số tiền phụ thu đền bù (ví dụ: 20.000 VNĐ).
3. Nhấn **Xác nhận hoàn tất trả sách**.
4. Vào trang **Lịch Sử Mượn Sách** của Thủ thư: Phiếu đã được lưu trữ vào lịch sử, nhấn nút **"Chi tiết"** sẽ hiển thị đầy đủ tình trạng sách khi nhận và quyết toán tài chính nếu có.

### 📌 Kịch bản 3: Báo mất sách & Tự động khóa / Mở khóa tài khoản
1. Độc giả có sách đang mượn nhấn nút **Báo Mất Sách**.
2. Hệ thống tự động tính tiền đền bù (Giá sách + Phạt quá hạn nếu có), chuyển phiếu sang **"Chờ đền bù"**, sách sang **"Đã mất"**, và tự động khóa tài khoản độc giả với lý do:  
   *`"TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH"`*.
3. Độc giả bị chặn không thể tiếp tục mượn sách.
4. Thủ thư vào mục **Quản lý Vi phạm & Đền bù**, nhấn **Xác nhận thu tiền đền bù** tại quầy.
5. Sau khi thanh toán xong, tài khoản độc giả được tự động mở khóa bình thường.

### 📌 Kịch bản 4: Kiểm thử tranh chấp đồng thời (Concurrency Control Test)
Để chứng minh với giảng viên hệ thống giải quyết triệt để lỗi Race Condition:
1. Chạy file: 👉 **`run_concurrency_test.bat`**
2. Chương trình sẽ khởi tạo đồng thời **10 luồng (Threads)** đại diện cho 10 độc giả cùng tranh nhau mượn **1 cuốn sách duy nhất** trong cùng 1 mili-giây.
3. **Kết quả**: Chỉ duy nhất **1 giao dịch thành công**, 9 giao dịch còn lại nhận thông báo lỗi an toàn và được rollback hoàn toàn, số lượng sách không bao giờ bị âm hoặc mượn trùng.

---

## 📂 6. CẤU TRÚC THƯ MỤC DỰ ÁN

```plaintext
RemoteSchoolLibrary/
├── src/main/java/com/library/
│   ├── model/                  # Các lớp thực thể & DTO (Sach, DocGia, PhieuMuon...)
│   ├── dao/                    # Xử lý kết nối CSDL và gọi Stored Procedures
│   ├── service/                # Interface và triển khai dịch vụ RMI (ILibraryService)
│   ├── server/                 # Điểm khởi chạy RMI Registry Server (ServerMain)
│   ├── client/                 # Mã nguồn RMI Client Swing/Console
│   └── web/                    # Máy chủ Web HTTP, Web Controller, API Restful & Giao diện
│       ├── config/             # Cấu hình Web và RMI Client Manager
│       ├── controller/         # BookApiController, WebStaticController
│       └── util/               # Bộ phân giải JSON và xử lý HTTP
├── lib/                        # Thư viện ngoài (mssql-jdbc-12.8.1.jre11.jar)
├── sql_patches/                # Thư mục lưu lịch sử các bản vá SQL đã thực hiện
├── setup_thuviendb.sql         # File SQL TỔNG HỢP TOÀN BỘ CSDL (chạy file này khi cài mới)
├── verify_thuviendb.sql        # Script kiểm thử tự động toàn diện CSDL
├── compile.bat                 # Script 1-click tự động biên dịch toàn bộ dự án
├── run_server.bat              # Script 1-click khởi chạy RMI Server (Port 1099)
├── run_web_client.bat          # Script 1-click khởi chạy Web Portal (Port 8080)
├── run_client.bat              # Script 1-click khởi chạy Java Client RMI
├── run_concurrency_test.bat    # Script 1-click chạy kiểm thử tranh chấp đồng thời
├── restart_sql_server.bat      # Script hỗ trợ khởi động lại dịch vụ SQL Server
├── pom.xml                     # Cấu hình dự án Maven tiêu chuẩn
└── .gitignore                  # Cấu hình loại trừ file rác khi đẩy lên GitHub
```

---

## 🛠️ 7. XỬ LÝ SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

| Sự cố | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- |
| **`Connection refused: connect`** | SQL Server chưa bật dịch vụ TCP/IP hoặc dịch vụ SQL Server chưa chạy. | 1. Mở **Sql Server Configuration Manager** -> **Protocols for MSSQLSERVER** -> Bật **TCP/IP** (Port 1433).<br>2. Nhấp đúp file `restart_sql_server.bat` bằng quyền Administrator. |
| **`Login failed for user 'sa'`** | Mật khẩu tài khoản `sa` khác với `123456`. | Mở file `src/main/java/com/library/dao/DBConnection.java`, sửa lại mật khẩu đúng tại mảng `CANDIDATE_PASSWORDS`, sau đó chạy lại `compile.bat`. |
| **`Port 1099 already in use`** | RMI Server cũ vẫn đang chạy nền. | Mở Task Manager, kết thúc các tiến trình `java.exe` đang chạy nền hoặc đổi port RMI. |
| **`Port 8080 already in use`** | Đang có ứng dụng khác (Tomcat, NetBeans, IIS...) dùng cổng 8080. | Tắt ứng dụng đang chiếm cổng 8080 hoặc mở Task Manager để đóng tiến trình. |

---

## 👥 THÔNG TIN ĐỒ ÁN & TÁC GIẢ
- **Tên đề tài**: Hệ thống Quản Lý Mượn / Trả Sách Thư Viện Trường Học Phân Tán.
- **Công nghệ chính**: Java RMI, Microsoft SQL Server (Pessimistic Locking / Transact-SQL), Java HTTP Web Server, Bootstrap 5 UI.
- **Phát hành**: Sẵn sàng triển khai, kiểm thử và bảo vệ đồ án.
