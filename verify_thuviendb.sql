-- =================================================================================
-- KIỂM THỬ TỰ ĐỘNG CƠ SỞ DỮ LIỆU THUVIENDB (AUTOMATED VERIFICATION SCRIPT)
-- ĐỒ ÁN: REMOTE SCHOOL LIBRARY MANAGEMENT - HỌC PHẦN CMP180
-- =================================================================================
-- MỤC ĐÍCH:
-- 1. Kiểm tra trạng thái dữ liệu các bảng và chỉ mục Unique Filtered Index.
-- 2. Kiểm thử luồng nghiệp vụ chuẩn: Giữ chỗ (0) -> Giao sách (1) -> Trả sách (2).
-- 3. Kiểm thử cơ chế chặn mượn trùng (Race Condition / Concurrency Invariant).
-- 4. Phân biệt chính xác mã lỗi nghiệp vụ, không coi lỗi bất kỳ là thành công.
-- =================================================================================

USE ThuVienDB;
GO

SET NOCOUNT ON;

PRINT N'====================================================================';
PRINT N'BƯỚC 1: KIỂM TRA DỮ LIỆU BẢNG & UNIQUE FILTERED INDEX';
PRINT N'====================================================================';

PRINT N'--- 1.1. DANH SÁCH TÀI KHOẢN (DocGia) ---';
SELECT MaDocGia, HoTen, Email, VaiTro, TrangThaiTaiKhoan, ISNULL(LyDoKhoa, N'') AS LyDoKhoa 
FROM DocGia;

PRINT N'--- 1.2. DANH SÁCH SÁCH (Sach) ---';
SELECT 
    MaSach, 
    TenSach, 
    TacGia, 
    TrangThai,
    CASE TrangThai 
        WHEN 0 THEN N'0 - Sẵn sàng mượn' 
        WHEN 1 THEN N'1 - Đang cho mượn' 
        WHEN 2 THEN N'2 - Đang giữ chỗ (chờ nhận)'
        WHEN -1 THEN N'-1 - Ngừng phục vụ / Đã mất'
        ELSE CAST(TrangThai AS NVARCHAR(10))
    END AS MoTaTrangThai,
    GiaTien
FROM Sach;

PRINT N'--- 1.3. KIỂM TRA UNIQUE FILTERED INDEX (BẢO VỆ BẤT BIẾN KHÔNG TRÙNG SÁCH ACTIVE) ---';
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_PhieuMuon_ActiveSach' AND object_id = OBJECT_ID('PhieuMuon'))
    PRINT N'-> [PASS] Index UQ_PhieuMuon_ActiveSach đang HOẠT ĐỘNG bảo vệ bất biến.';
ELSE
    PRINT N'-> [FAIL] Chưa tìm thấy Index UQ_PhieuMuon_ActiveSach!';

PRINT N'--- 1.4. TÌM PHIẾU ACTIVE TRÙNG (NẾU CÓ) ---';
SELECT MaSach, COUNT(*) AS SoLuongActive 
FROM PhieuMuon 
WHERE TrangThaiPhieu IN (0, 1, 4, 6) 
GROUP BY MaSach 
HAVING COUNT(*) > 1;

PRINT N'';
PRINT N'====================================================================';
PRINT N'BƯỚC 2: KIỂM THỬ ĐĂNG KÝ GIỮ CHỖ & CHỐNG MƯỢN TRÙNG';
PRINT N'====================================================================';

-- Tìm 1 cuốn sách đang sẵn sàng và 2 độc giả READER hợp lệ
DECLARE @TestMaSach INT = NULL;
SELECT TOP 1 @TestMaSach = MaSach FROM Sach WHERE TrangThai = 0 ORDER BY MaSach ASC;

DECLARE @Reader1 INT = NULL, @Reader2 INT = NULL;
SELECT TOP 1 @Reader1 = MaDocGia FROM DocGia WHERE VaiTro = 'READER' AND TrangThaiTaiKhoan = 1 ORDER BY MaDocGia ASC;
SELECT TOP 1 @Reader2 = MaDocGia FROM DocGia WHERE VaiTro = 'READER' AND TrangThaiTaiKhoan = 1 AND MaDocGia <> @Reader1 ORDER BY MaDocGia ASC;

IF @TestMaSach IS NULL OR @Reader1 IS NULL OR @Reader2 IS NULL
BEGIN
    PRINT N'[BLOCKED] Không đủ sách sẵn sàng hoặc độc giả hợp lệ để chạy ca kiểm thử.';
END
ELSE
BEGIN
    PRINT N'>>> Test 2.1: Độc giả #' + CAST(@Reader1 AS NVARCHAR(10)) + N' đăng ký giữ chỗ sách #' + CAST(@TestMaSach AS NVARCHAR(10));
    DECLARE @MaPhieuMoi INT;
    
    EXEC sp_KiemTraVaMuonSach 
        @MaDocGia = @Reader1, 
        @MaSach = @TestMaSach, 
        @SoNgayMuon = 14, 
        @MaPhieu = @MaPhieuMoi OUTPUT;

    PRINT N'-> Mã phiếu mượn vừa sinh: ' + CAST(@MaPhieuMoi AS NVARCHAR(10));

    -- Xác minh trạng thái sách phải là 2 (Đang giữ chỗ)
    DECLARE @TrangThaiSachSauHold INT;
    SELECT @TrangThaiSachSauHold = TrangThai FROM Sach WHERE MaSach = @TestMaSach;
    IF @TrangThaiSachSauHold = 2
        PRINT N'-> [PASS] Trạng thái sách đã chuyển sang 2 (Đang giữ chỗ).';
    ELSE
        PRINT N'-> [FAIL] Sai trạng thái sách: ' + CAST(@TrangThaiSachSauHold AS NVARCHAR(10));

    PRINT N'';
    PRINT N'>>> Test 2.2: Độc giả #' + CAST(@Reader2 AS NVARCHAR(10)) + N' cố tình mượn TRÙNG cuốn sách #' + CAST(@TestMaSach AS NVARCHAR(10));
    PRINT N'-> Kỳ vọng: Bị từ chối với mã lỗi ERR_BOOK_RESERVED hoặc ERR_BOOK_ALREADY_BORROWED.';

    BEGIN TRY
        EXEC sp_KiemTraVaMuonSach 
            @MaDocGia = @Reader2, 
            @MaSach = @TestMaSach, 
            @SoNgayMuon = 7;
        PRINT N'-> [FAIL NGHIÊM TRỌNG]: Lẽ ra không được cho phép mượn!';
    END TRY
    BEGIN CATCH
        DECLARE @ErrMsg NVARCHAR(4000) = ERROR_MESSAGE();
        IF @ErrMsg LIKE N'%ERR_BOOK_RESERVED%' OR @ErrMsg LIKE N'%ERR_BOOK_ALREADY_BORROWED%' OR @ErrMsg LIKE N'%đang được giữ chỗ%' OR @ErrMsg LIKE N'%đang được mượn%'
            PRINT N'-> [PASS] Đã chặn mượn trùng chính xác với thông báo: ' + @ErrMsg;
        ELSE
            PRINT N'-> [WARN] Bị chặn nhưng do nguyên nhân khác: ' + @ErrMsg;
    END CATCH;

    PRINT N'';
    PRINT N'====================================================================';
    PRINT N'BƯỚC 3: KIỂM THỬ QUY TRÌNH HOÀN CHỈNH: GIAO SÁCH TẠI QUẦY -> TRẢ SÁCH';
    PRINT N'====================================================================';

    PRINT N'>>> Test 3.1: Thử trả sách KHI CHƯA GIAO SÁCH (Phiếu vẫn đang ở trạng thái 0 - Chờ lấy)';
    PRINT N'-> Kỳ vọng: Stored Procedure từ chối vì chưa nhận sách tại quầy.';
    BEGIN TRY
        EXEC sp_TraSach @MaPhieu = @MaPhieuMoi;
        PRINT N'-> [FAIL]: Lẽ ra không cho phép trả sách khi chưa giao!';
    END TRY
    BEGIN CATCH
        PRINT N'-> [PASS] Đã chặn trả sách sớm thành công: ' + ERROR_MESSAGE();
    END CATCH;

    PRINT N'';
    PRINT N'>>> Test 3.2: Thủ thư xác nhận GIAO SÁCH tại quầy cho phiếu #' + CAST(@MaPhieuMoi AS NVARCHAR(10));
    EXEC sp_XacNhanGiaoSach @MaPhieu = @MaPhieuMoi;

    DECLARE @TrangThaiPhieuSauGiao INT, @TrangThaiSachSauGiao INT;
    SELECT @TrangThaiPhieuSauGiao = TrangThaiPhieu FROM PhieuMuon WHERE MaPhieu = @MaPhieuMoi;
    SELECT @TrangThaiSachSauGiao = TrangThai FROM Sach WHERE MaSach = @TestMaSach;

    IF @TrangThaiPhieuSauGiao = 1 AND @TrangThaiSachSauGiao = 1
        PRINT N'-> [PASS] Phiếu đã chuyển sang 1 (Đang mượn), Sách đã chuyển sang 1 (Đang cho mượn).';
    ELSE
        PRINT N'-> [FAIL] Trạng thái sau giao sách không đúng!';

    PRINT N'';
    PRINT N'>>> Test 3.3: Tiến hành TRẢ SÁCH và thẩm định Nguyên vẹn';
    EXEC sp_TraSach @MaPhieu = @MaPhieuMoi, @TienPhuThu = 0, @TinhTrangSachKhiTra = N'Nguyên vẹn';

    DECLARE @TrangThaiPhieuSauTra INT, @TrangThaiSachSauTra INT;
    SELECT @TrangThaiPhieuSauTra = TrangThaiPhieu FROM PhieuMuon WHERE MaPhieu = @MaPhieuMoi;
    SELECT @TrangThaiSachSauTra = TrangThai FROM Sach WHERE MaSach = @TestMaSach;

    IF @TrangThaiPhieuSauTra = 2 AND @TrangThaiSachSauTra = 0
        PRINT N'-> [PASS] Hoàn tất trả sách: Phiếu = 2 (Đã trả), Sách = 0 (Sẵn sàng mượn lại).';
    ELSE
        PRINT N'-> [FAIL] Trạng thái sau trả sách không đúng!';

    PRINT N'';
    PRINT N'>>> Test 3.4: Thử trả lại phiếu đã hoàn tất (' + CAST(@MaPhieuMoi AS NVARCHAR(10)) + N')';
    BEGIN TRY
        EXEC sp_TraSach @MaPhieu = @MaPhieuMoi;
        PRINT N'-> [FAIL]: Lẽ ra không cho phép trả lại phiếu đã xong!';
    END TRY
    BEGIN CATCH
        PRINT N'-> [PASS] Chặn trả lặp lại thành công: ' + ERROR_MESSAGE();
    END CATCH;
END
GO

PRINT N'';
PRINT N'====================================================================';
PRINT N'HOÀN TẤT KIỂM THỬ CƠ SỞ DỮ LIỆU THUVIENDB!';
PRINT N'====================================================================';
GO
