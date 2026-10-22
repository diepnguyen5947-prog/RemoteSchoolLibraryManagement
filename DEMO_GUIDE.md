# HƯỚNG DẪN DEMO HỆ THỐNG DÀNH CHO BÁO CÁO (DEMO GUIDE)
**Đồ án:** Remote School Library Management – Hệ thống quản lý thư viện trường học từ xa  
**Học phần:** CMP180 – Lập trình mạng máy tính  
**Tác giả:** Nhóm em  

---

## 1. BẢNG TRA CỨU CÔNG NGHỆ VÀ THÀNH PHẦN MẠNG (RMI & SERIALIZATION MAPPING)

Khi trình bày cho giảng viên, bảng sau chỉ rõ vị trí từng thành phần kỹ thuật mạng:

| Khái niệm môn học CMP180 | Lớp Java / File mã nguồn trong dự án | Vị trí thực thi | Vai trò kỹ thuật |
|:---|:---|:---:|:---|
| **Remote Interface (Bài 9)** | `com.library.service.ILibraryService` | Shared Contract | Kế thừa `java.rmi.Remote`, mọi phương thức ném `RemoteException` và `LibraryException`. |
| **Remote Object / Skeleton (Bài 9)** | `com.library.service.LibraryServiceImpl` | RMI Server JVM | Kế thừa `UnicastRemoteObject`, cài đặt nghiệp vụ, export trên cổng cố định **1098**. |
| **RMI Registry (Bài 9)** | `java.rmi.registry.LocateRegistry` | RMI Server JVM | Dịch vụ định danh chạy tại cổng **1099**, quản lý tên dịch vụ `"LibraryService"`. |
| **Remote Stub (Proxy)** | Được trả về từ `registry.lookup()` | RMI Client / Web Gateway | Client chỉ nhận đối tượng Stub tham chiếu từ xa, **không `new` trực tiếp `LibraryServiceImpl`**. |
| **Java Serialization (Bài 6)** | `SachDTO`, `DocGiaDTO`, `PhieuMuonDTO`, `UserSessionDTO`, `LibraryException` | Mạng TCP / Socket | Kế thừa `Serializable`, có `serialVersionUID`, chuyển đối tượng thành chuỗi byte qua mạng. |
| **Pass-By-Value (Bài 6)** | `SerializationTest.java (Ca 6)` | Client & Server | Thay đổi trên DTO tại Client không làm biến đổi dữ liệu trên Server trừ khi gọi hàm update. |
| **Quản lý đa luồng & Khóa (Bài 3)** | `sp_KiemTraVaMuonSach`, `RmiConcurrencyTest` | RMI Server & SQL Server | Sử dụng `ExecutorService`, `CountDownLatch`, và khóa `UPDLOCK, HOLDLOCK` của SQL Server. |

---

## 2. PHÂN BIỆT RÕ LỜI GỌI RMI THẬT VÀ LỜI GỌI WEB HTTP

1. **Lời gọi RMI thật (Độc lập, không qua HTTP):**
   - Ứng dụng **`ClientMain.java` (chạy qua `run_client.bat`)** kết nối trực tiếp cổng 1099 tới RMI Server qua giao thức Java RMI (`rmi://...`).
   - Các chương trình test tự động **`RmiIntegrationTest`** và **`RmiConcurrencyTest`** phát lệnh trực tiếp qua Remote Stub.
2. **Lời gọi HTTP (Web Gateway):**
   - Trình duyệt Web (Chrome, Edge) gọi REST API HTTP/JSON tới `BookApiController` (cổng 8080).
   - `BookApiController` đóng vai trò là một **RMI Client** tiếp nhận request, chuyển đổi tham số thành lời gọi RMI tới RMI Server, sau đó gói kết quả thành JSON trả về cho trình duyệt.
   - **Web Gateway không bao giờ truy cập trực tiếp CSDL (không gọi DAO).**

---

## 3. CÁC TÀI KHOẢN KIỂM THỬ TRONG HỆ THỐNG (TEST ACCOUNTS)

Mọi tài khoản đều có mật khẩu mặc định: **`123456`**

| Vai trò | Họ và tên | Email đăng nhập | Quyền hạn trong hệ thống |
|:---|:---|:---|:---|
| **LIBRARIAN (Thủ thư)** | Thủ Thư Quản Trị | `admin@school.edu.vn` | Toàn quyền: Xem tất cả phiếu, giao sách, thu hồi sách, thu tiền đền bù, quản lý sách, mở khóa tài khoản. |
| **READER (Độc giả 1)** | Nguyễn Văn An | `nguyenvanan@school.edu.vn` | Tra cứu sách, giữ chỗ mượn sách, xem phiếu của mình, yêu cầu trả sách, báo mất sách. |
| **READER (Độc giả 2)** | Trần Thị Bình | `tranthibinh@school.edu.vn` | Độc giả độc lập dùng để demo tranh chấp mượn trùng với Độc giả 1. |
| **READER (Độc giả 3)** | Lê Hoàng Cường | `lehoangcuong@school.edu.vn` | Độc giả dự phòng dùng cho kịch bản nhiều người dùng. |

---

## 4. KỊCH BẢN DEMO 1: CHẠY TRÊN 1 MÁY TÍNH (SINGLE-MACHINE DEMO)

### Bước 1: Khởi động SQL Server và kiểm tra CSDL
- Đảm bảo SQL Server đang chạy cổng 1433.
- Chạy kiểm tra CSDL nhanh:
  ```bat
  verify_thuviendb.sql
  ```
  Kết quả hiển thị: `7 / 7 CA KIỂM THỬ ĐẠT (PASS)`.

### Bước 2: Biên dịch sạch dự án
- Mở Terminal tại thư mục dự án và chạy:
  ```bat
  compile.bat
  ```
  Thông báo thành công: `[THANH CONG] BIEN DICH HOAN TAT TOAN BO DU AN!`.

### Bước 3: Khởi động RMI Server
- Nhấp đúp hoặc chạy script:
  ```bat
  run_server.bat
  ```
- Quan sát thông báo:
  - Cổng RMI Registry: `1099`
  - Cổng Export Object: `1098`
  - Trạng thái: `[HEALTHY] RMI SERVER SẴN SÀNG TIẾP NHẬN YÊU CẦU TỪ CÁC CLIENT!`.

### Bước 4: Khởi động Java Console Client (RMI trực tiếp)
- Mở cửa sổ dòng lệnh thứ 2 và chạy:
  ```bat
  run_client.bat
  ```
- Quan sát log kết nối: `-> [THÀNH CÔNG] Đã nhận Remote Stub từ xa! Kết nối RMI sẵn sàng.`.
- Thao tác:
  1. Chọn `3` -> Đăng nhập bằng `nguyenvanan@school.edu.vn` / `123456`.
  2. Chọn `1` -> Tra cứu toàn bộ sách qua RMI.
  3. Chọn `3` -> Nhập mã sách (vd: `5`) để mượn sách -> Nhận mã phiếu giữ chỗ.
  4. Mở thêm một cửa sổ `run_client.bat` khác, đăng nhập bằng `tranthibinh@school.edu.vn`, thử mượn cùng cuốn sách `5` -> Server từ chối ngay lập tức: `Sách đang được mượn hoặc giữ chỗ bởi độc giả khác!`.

### Bước 5: Khởi động Web Gateway (Giao diện Web)
- Mở cửa sổ dòng lệnh thứ 3 và chạy:
  ```bat
  run_web_client.bat
  ```
- Truy cập trình duyệt tại: `http://localhost:8080`.
- Đăng nhập tài khoản Thủ thư: `admin@school.edu.vn` / `123456`.
- Vào mục **Quản lý mượn/trả sách**: Sẽ thấy phiếu mượn vừa được tạo từ Java Console Client xuất hiện ngay lập tức (chứng minh dữ liệu tập trung qua RMI Server).
- Nhấn **Xác nhận giao sách** -> Trạng thái chuyển sang Đang mượn thực tế.
- Nhấn **Thu hồi & Trả sách** -> Sách được phục hồi về Sẵn sàng mượn.

---

## 5. KỊCH BẢN DEMO 2: CHẠY TRÊN NHIỀU MÁY TRONG MẠNG LAN (MULTI-MACHINE LAN DEMO)

Giả sử:
- **Máy A (Server):** Địa chỉ IP LAN là `192.168.1.50`.
- **Máy B (Client):** Máy tính của bạn học trong cùng lớp/mạng Wi-Fi.

### Cấu hình Firewall trên Máy A:
Mở các cổng vào (Inbound Rules) trên Windows Defender Firewall của Máy A:
- Cổng **1099** (TCP - RMI Registry)
- Cổng **1098** (TCP - RMI Object Export)
- Cổng **8080** (TCP - Web Server nếu cho máy khác vào Web)
- Cổng **1433** (TCP - SQL Server)

### Khởi chạy trên Máy A:
```bat
:: Truyền IP LAN của Máy A vào thuộc tính rmi.host
java -Dfile.encoding=UTF-8 -Drmi.host=192.168.1.50 -cp "bin;lib/*" com.library.server.ServerMain
```
Khi này RMI Server sẽ thông báo: `Server Host IP: 192.168.1.50 (java.rmi.server.hostname)`.

### Khởi chạy Client trên Máy B:
Trên Máy B, mở dòng lệnh và kết nối tới IP của Máy A:
```bat
run_client.bat 192.168.1.50 1099 LibraryService
```
Client trên Máy B sẽ thực hiện tra cứu registry, nhận Remote Stub và gọi hàm mượn/trả sách trên Máy A xuyên qua mạng LAN!

---

## 6. CÂU HỎI THƯỜNG GẶP VÀ CÁCH TRẢ LỜI BẢO VỆ ĐỒ ÁN (VIVA DEFENSE)

### Câu 1: Vì sao giữ chỗ sách khác với giao sách? Tại sao không giữ khóa SQL trong suốt 24 giờ giữ chỗ?
- **Trả lời:**
  - **Giữ chỗ (Reservation):** Độc giả đăng ký từ xa qua mạng để đặt trước cuốn sách. Hệ thống chuyển `Sach.TrangThai = 2` và `PhieuMuon.TrangThaiPhieu = 0` với hạn giữ 24h. Độc giả chưa cầm sách trên tay.
  - **Giao sách (Handover):** Độc giả đến quầy xuất trình thẻ, thủ thư trao bản sách vật lý và chuyển trạng thái sang `1` (Đang mượn thực tế).
  - **Tại sao không giữ khóa SQL 24 giờ?**
    Khóa SQL (`UPDLOCK`, `HOLDLOCK`) là khóa giao dịch ngắn hạn mức độ mili-giây (chỉ giữ trong lúc execute stored procedure). Nếu giữ connection và transaction SQL mở suốt 24 giờ, Connection Pool của CSDL sẽ bị cạn kiệt (exhaustion), gây nghẽn toàn bộ hệ thống và nguy cơ deadlock nghiêm trọng.
    Bất biến 24 giờ được bảo vệ bằng **trạng thái logic trong CSDL kết hợp với Unique Filtered Index**:
    `CREATE UNIQUE NONCLUSTERED INDEX UQ_PhieuMuon_ActiveSach ON PhieuMuon(MaSach) WHERE TrangThaiPhieu IN (0, 1, 4, 6);`.

### Câu 2: Cơ chế chống Race Condition hoạt động như thế nào khi 2 người cùng bấm mượn 1 cuốn sách tại cùng 1 mili-giây?
- **Trả lời:**
  Hệ thống bảo vệ bằng 3 tầng phòng thủ phối hợp:
  1. **Tầng 1 (Stored Procedure & Transaction Lock):**
     Câu lệnh `SELECT ... WITH (UPDLOCK, ROWLOCK, HOLDLOCK)` nằm trong `BEGIN TRANSACTION`. Khi Luồng 1 đọc bản ghi cuốn sách, SQL Server cấp khóa cập nhật độc quyền trên hàng đó. Luồng 2 gọi đến cùng lúc sẽ bị chặn (chờ khóa) cho đến khi Luồng 1 hoàn tất.
     Khi Luồng 1 mượn thành công và đổi `TrangThai = 2` rồi commit, Luồng 2 tiếp tục đọc thấy trạng thái không còn là `0` và ném lỗi `ERR_BOOK_RESERVED`.
  2. **Tầng 2 (Bảo vệ bất biến cấp CSDL bằng Unique Filtered Index):**
     Ngay cả trong trường hợp xấu nhất nếu có bypass logic code, chỉ mục duy nhất `UQ_PhieuMuon_ActiveSach` sẽ từ chối dòng lệnh INSERT thứ hai với lỗi Msg 2601/2627.
  3. **Tầng 3 (RMI Server Error Translation):**
     RMI Server bắt ngoại lệ vi phạm và chuyển đổi thành `LibraryException` với mã lỗi nghiệp vụ chuẩn `ERR_BOOK_NOT_AVAILABLE`, Web Gateway dịch thành HTTP 409 Conflict.
