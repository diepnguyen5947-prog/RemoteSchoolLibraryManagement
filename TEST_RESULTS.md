# KẾT QUẢ KIỂM THỬ THỰC TẾ (TEST RESULTS)
**Đồ án:** Remote School Library Management – Hệ thống quản lý thư viện trường học từ xa  
**Học phần:** CMP180 – Lập trình mạng máy tính  
**Tác giả:** Nhóm em  
**Thời gian thực hiện:** Ngày 22/10/2026  
**Môi trường thử nghiệm:**
- **Hệ điều hành:** Windows 10/11 x64
- **Java Runtime / JDK:** OpenJDK 17.0.10 LTS (`C:\Program Files\Apache NetBeans\jdk`)
- **Hệ quản trị CSDL:** Microsoft SQL Server 2022 (v16.0.1000.6) tại `127.0.0.1:1433`, CSDL `ThuVienDB`
- **RMI Server Registry:** `127.0.0.1:1099`, Export Object: `1098`
- **Web Gateway Server:** Sun Java HttpServer tại `http://localhost:8080`

---

## 1. BẢNG TỔNG HỢP KẾT QUẢ KIỂM THỬ (TEST SUMMARY)

| Tầng kiểm thử | Số ca thực thi | Số ca PASS | Số ca FAIL | Tỷ lệ thành công | Trạng thái đánh giá |
|:---|:---:|:---:|:---:|:---:|:---:|
| **Tầng A:** Serialization & DTO Unit Test | 6 | 6 | 0 | 100% | **ĐẠT (PASS)** |
| **Tầng B:** RMI Integration & SQL Server Test | 13 | 13 | 0 | 100% | **ĐẠT (PASS)** |
| **Tầng C (2 Clients):** RMI Concurrency Test | 1 Vòng (2 Luồng) | 1 | 0 | 100% | **ĐẠT (PASS)** |
| **Tầng C (10 Clients):** RMI Concurrency Test | 1 Vòng (10 Luồng) | 1 | 0 | 100% | **ĐẠT (PASS)** |
| **Tầng Web HTTP:** Web Gateway Concurrency Test | 2 Luồng HTTP | 2 | 0 | 100% | **ĐẠT (PASS)** |
| **CSDL Audit:** SQL Server Invariant Inspection | 4 Tiêu chí | 4 | 0 | 100% | **ĐẠT (PASS)** |

---

## 2. NHẬT KÝ VÀ BẰNG CHỨNG THỰC TẾ TỪNG TẦNG

### 2.1. Kết quả Tầng A: SerializationTest (Bài 6 - Java Serialization)
Lệnh chạy: `java -cp "bin;lib/*" com.library.test.SerializationTest`
```
====================================================================
  KIỂM THỬ TẦNG A: TUẦN TỰ HÓA ĐỐI TƯỢNG (JAVA SERIALIZATION - BÀI 6)
====================================================================
[PASS] Ca 1: Tuần tự hóa và phục hồi SachDTO thành công.
[PASS] Ca 2: Tuần tự hóa DocGiaDTO an toàn (Mật khẩu không bị rò rỉ).
[PASS] Ca 3: Tuần tự hóa PhieuMuonDTO thành công (Đầy đủ thuộc tính ngày, hạn giữ, tiền phạt).
[PASS] Ca 4: Tuần tự hóa UserSessionDTO thành công (Token và Thông tin người dùng).
[PASS] Ca 5: Tuần tự hóa LibraryException thành công (Bảo toàn errorCode qua RMI).
[PASS] Ca 6: Xác minh cơ chế Pass-By-Value (Client sửa DTO nhận được không ảnh hưởng Server).
--------------------------------------------------------------------
TỔNG KẾT TẦNG A: 6 PASS | 0 FAIL (Tổng cộng 6 ca kiểm thử)
====================================================================
```
**Nhận xét:** Tất cả các đối tượng DTO và checked exception đều tuần tự hóa và giải tuần tự hóa an toàn, trường `matKhau` được bảo vệ không rò rỉ trên đường truyền mạng.

---

### 2.2. Kết quả Tầng B: RmiIntegrationTest (Bài 9 - Java RMI)
Lệnh chạy: `java -cp "bin;lib/*" com.library.test.RmiIntegrationTest`
```
====================================================================
  KIỂM THỬ TẦNG B: TÍCH HỢP RMI THẬT & SQL SERVER THẬT (BÀI 9)
  Mục tiêu: rmi://127.0.0.1:1099/LibraryService
====================================================================
[PASS] Ca 1: Kết nối Remote Stub và triệu gọi ping() thành công.
[PASS] Ca 2a: Chặn đăng nhập sai mật khẩu thành công (Mã lỗi: UNAUTHORIZED).
[PASS] Ca 2b: Đăng nhập Độc giả thành công (Token: 60de032d...).
[PASS] Ca 2c: Đăng nhập Thủ thư thành công (Vai trò: LIBRARIAN).
[PASS] Ca 3a: Chặn độc giả gọi phương thức Thủ thư thành công (Mã lỗi: FORBIDDEN).
[PASS] Ca 3b: Chặn token không hợp lệ thành công (Mã lỗi: UNAUTHORIZED).
[PASS] Ca 4a: getAllBooks() trả về 12 cuốn sách.
[PASS] Ca 4b: searchBooks('Java') trả về 0 kết quả.

--- BẮT ĐẦU CHU TRÌNH MƯỢN/TRẢ SÁCH VỚI CUỐN SÁCH #1 ---
[PASS] Ca 5a: Đăng ký giữ chỗ thành công (Mã phiếu #7, DB Sach=2, DB Phieu=0).
[PASS] Ca 5b: Chặn mượn trùng cuốn sách đang giữ chỗ thành công (Mã lỗi: BOOK_NOT_AVAILABLE).
[PASS] Ca 5c: Thủ thư xác nhận giao sách thành công (Sach=1, Phieu=1).
[PASS] Ca 5d: Độc giả gửi yêu cầu trả sách thành công (Phieu=6).
[PASS] Ca 5e: Thủ thư xác nhận trả sách thành công (Sach=0 - Sẵn sàng, Phieu=2 - Đã kết thúc).
--------------------------------------------------------------------
TỔNG KẾT TẦNG B: 13 PASS | 0 FAIL (Tổng cộng các ca kiểm thử tích hợp)
====================================================================
```
**Nhận xét:** Toàn bộ chu trình 6 trạng thái mượn/giao/trả sách hoạt động hoàn hảo trên RMI thật và đối chiếu khớp 100% với dữ liệu SQL Server.

---

### 2.3. Kết quả Tầng C: RmiConcurrencyTest (2 Clients đồng thời)
Lệnh chạy: `java -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 2 1`
```
====================================================================
  KIỂM THỬ TẦNG C: TRANH CHẤP MƯỢN SÁCH ĐỒNG THỜI QUA JAVA RMI
  Mục tiêu: rmi://127.0.0.1:1099/LibraryService
  Cấu hình: 2 Client đồng thời | 1 Vòng kiểm thử
====================================================================
[Init] Kết nối tới RMI Server thành công!

======================== VÒNG KIỂM THỬ 1 / 1 ========================
[TestRunId: RUN_208fd09d] Chuẩn bị dữ liệu kiểm thử...
  -> Cuốn sách kiểm thử: Mã sách #1 (Đã đưa về trạng thái Sẵn sàng 0)
  -> Đã xác thực thành công 2 phiên độc giả độc lập.
  -> Kích hoạt 2 luồng phát lệnh mượn sách đồng thời qua RMI...
--------------------------------------------------------------------
Kết quả thu thập sau 86 ms:
  - Client #1 (Độc giả #2 - nguyenvanan@school.edu.vn): THÀNH CÔNG -> Cấp phiếu mượn #8 (73 ms)
  - Client #2 (Độc giả #3 - tranthibinh@school.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (82 ms)
--------------------------------------------------------------------
[Đối chiếu CSDL SQL Server ThuVienDB]
  - Số phiếu chiếm dụng chưa kết thúc trong CSDL: 1
  - Trạng thái cuốn sách trong CSDL:              2 (2: Đang giữ chỗ)

[ĐÁNH GIÁ TIÊU CHÍ VÒNG KIỂM THỬ]:
  [1] Đúng 1 yêu cầu thành công có mã phiếu:                ĐẠT (PASS)
  [2] Đúng 1 yêu cầu bị từ chối BOOK_NOT_AVAILABLE:    ĐẠT (PASS)
  [3] Không có lỗi kỹ thuật/lỗi kết nối ngoại lai:          ĐẠT (PASS)
  [4] Đối chiếu CSDL: Đúng 1 phiếu chiếm dụng duy nhất:     ĐẠT (PASS)
  [5] Trạng thái sách chuyển sang Giữ chỗ (2):              ĐẠT (PASS)

====================================================================
TỔNG KẾT KIỂM THỬ TRANH CHẤP: 1 / 1 VÒNG ĐẠT TIÊU CHUẨN
KẾT LUẬN: ĐẠT! Cơ chế UPDLOCK + Unique Filtered Index đã ngăn chặn triệt để mượn trùng.
====================================================================
```

---

### 2.4. Kết quả Tầng C: RmiConcurrencyTest (10 Clients đồng thời)
Lệnh chạy: `java -cp "bin;lib/*" com.library.test.RmiConcurrencyTest 10 1`
```
====================================================================
  KIỂM THỬ TẦNG C: TRANH CHẤP MƯỢN SÁCH ĐỒNG THỜI QUA JAVA RMI
  Mục tiêu: rmi://127.0.0.1:1099/LibraryService
  Cấu hình: 10 Client đồng thời | 1 Vòng kiểm thử
====================================================================
[Init] Kết nối tới RMI Server thành công!

======================== VÒNG KIỂM THỬ 1 / 1 ========================
[TestRunId: RUN_2208108f] Chuẩn bị dữ liệu kiểm thử...
  -> Cuốn sách kiểm thử: Mã sách #1 (Đã đưa về trạng thái Sẵn sàng 0)
  -> Đã xác thực thành công 10 phiên độc giả độc lập.
  -> Kích hoạt 10 luồng phát lệnh mượn sách đồng thời qua RMI...
--------------------------------------------------------------------
Kết quả thu thập sau 223 ms:
  - Client #9 (Độc giả #10 - concurrency_reader_4@test.edu.vn): THÀNH CÔNG -> Cấp phiếu mượn #9 (211 ms)
  - Client #4 (Độc giả #5 - phamthidung@school.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (219 ms)
  - Client #5 (Độc giả #6 - hoangminhem@school.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (219 ms)
  - Client #7 (Độc giả #8 - concurrency_reader_2@test.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (219 ms)
  - Client #3 (Độc giả #4 - lehoangcuong@school.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (219 ms)
  - Client #6 (Độc giả #7 - concurrency_reader_1@test.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (219 ms)
  - Client #1 (Độc giả #2 - nguyenvanan@school.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (219 ms)
  - Client #8 (Độc giả #9 - concurrency_reader_3@test.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (219 ms)
  - Client #2 (Độc giả #3 - tranthibinh@school.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (220 ms)
  - Client #10 (Độc giả #11 - concurrency_reader_5@test.edu.vn): BỊ TỪ CHỐI ĐÚNG -> Sách đang được mượn hoặc giữ chỗ bởi độc giả khác! (223 ms)
--------------------------------------------------------------------
[Đối chiếu CSDL SQL Server ThuVienDB]
  - Số phiếu chiếm dụng chưa kết thúc trong CSDL: 1
  - Trạng thái cuốn sách trong CSDL:              2 (2: Đang giữ chỗ)

[ĐÁNH GIÁ TIÊU CHÍ VÒNG KIỂM THỬ]:
  [1] Đúng 1 yêu cầu thành công có mã phiếu:                ĐẠT (PASS)
  [2] Đúng 9 yêu cầu bị từ chối BOOK_NOT_AVAILABLE:    ĐẠT (PASS)
  [3] Không có lỗi kỹ thuật/lỗi kết nối ngoại lai:          ĐẠT (PASS)
  [4] Đối chiếu CSDL: Đúng 1 phiếu chiếm dụng duy nhất:     ĐẠT (PASS)
  [5] Trạng thái sách chuyển sang Giữ chỗ (2):              ĐẠT (PASS)

====================================================================
TỔNG KẾT KIỂM THỬ TRANH CHẤP: 1 / 1 VÒNG ĐẠT TIÊU CHUẨN
KẾT LUẬN: ĐẠT! Cơ chế UPDLOCK + Unique Filtered Index đã ngăn chặn triệt để mượn trùng.
====================================================================
```

---

### 2.5. Kết quả Tầng Web HTTP: ConcurrencyTest (HTTP Gateway)
Lệnh chạy: `java -cp "bin;lib/*" ConcurrencyTest`
```
====================================================================
  KIỂM THỬ ĐỒNG THỜI TẦNG WEB GATEWAY (HTTP CONCURRENCY TEST)
====================================================================
[Bước 1] Đăng nhập 2 tài khoản Độc giả (READER):
  -> Độc giả 1 (An):    Token = 9dc9f09e...
  -> Độc giả 2 (Bình):  Token = 3efa7c05...

[Bước 2] Gửi 2 yêu cầu mượn song song cho cuốn sách mã 5...

>>> KẾT QUẢ PHẢN HỒI TỪ YÊU CẦU 1 (Độc giả 1):
    Status Code: 200
    Nội dung:    {"success":true,"message":"Đăng ký giữ sách thành công! Vui lòng đến thư viện nhận sách trước 2026-10-23 22:41:25","data":{"maPhieu":11,"maDocGia":2,"maSach":5,"ngayHetHanGiu":"2026-10-23 22:41:25"}}

>>> KẾT QUẢ PHẢN HỒI TỪ YÊU CẦU 2 (Độc giả 2):
    Status Code: 409
    Nội dung:    {"success":false,"message":"Sách đang được mượn hoặc giữ chỗ bởi độc giả khác!","data":null}

--------------------------------------------------------------------
Thời gian thực thi 2 luồng đồng thời: 180 ms
>>> ĐÁNH GIÁ VÒNG TEST: ĐẠT!
    [OK] Đúng 1 yêu cầu mượn thành công (HTTP 200 - Đã cấp mã phiếu giữ chỗ).
    [OK] Đúng 1 yêu cầu bị từ chối (HTTP 409 - Sách đang được mượn/giữ chỗ bởi người khác).
    [OK] Cơ chế phân quyền và bảo vệ bất biến của SQL Server và RMI Server đã hoạt động đúng.
====================================================================
```

---

### 2.6. Báo cáo Audit toàn vẹn CSDL (sql_patches/audit_inspect_db.sql)
Lệnh chạy: `sqlcmd -S 127.0.0.1,1433 -U sa -P 123456 -d ThuVienDB -i sql_patches/audit_inspect_db.sql`
```
====================================================================
  BÁO CÁO TOÀN VẸN CƠ SỞ DỮ LIỆU THUVIENDB (AUDIT INSPECTION)
====================================================================
[KIỂM TRA 1] Quét các bản sách bị vi phạm chiếm dụng trùng (Active Loans > 1)...
-> ĐẠT: Không có bất kỳ bản sách nào bị mượn/giữ chỗ trùng!

[KIỂM TRA 2] Quét lệch pha trạng thái giữa bảng Sach và bảng PhieuMuon...
-> ĐẠT: Không có sách sẵn sàng nào bị kẹt phiếu mượn active.
-> ĐẠT: Mọi sách đang mượn/giữ chỗ đều có phiếu mượn active hợp lệ.

[KIỂM TRA 3] Quét bản ghi mồ côi (Foreign Key Integrity)...
-> ĐẠT: Toàn bộ khóa ngoại giữa PhieuMuon, DocGia và Sach hoàn toàn toàn vẹn.

[KIỂM TRA 4] Kiểm tra Unique Filtered Index UQ_PhieuMuon_ActiveSach...
-> ĐẠT: Index UQ_PhieuMuon_ActiveSach đang hoạt động bảo vệ tầng CSDL!
====================================================================
  HOÀN TẤT AUDIT KIỂM TRA DỮ LIỆU THÀNH CÔNG!
====================================================================
```
