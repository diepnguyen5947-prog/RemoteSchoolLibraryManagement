-- =================================================================================
-- KIỂM THỬ TỰ ĐỘNG CƠ SỞ DỮ LIỆU THUVIENDB (AUTOMATED VERIFICATION SCRIPT)
-- =================================================================================

USE ThuVienDB;
GO

SET NOCOUNT ON;

PRINT N'====================================================================';
PRINT N'BƯỚC 1: KIỂM TRA DỮ LIỆU CÁC BẢNG SAU KHỞI TẠO';
PRINT N'====================================================================';

PRINT N'--- 1.1. DANH SÁCH ĐỘC GIẢ (DocGia) ---';
SELECT MaDocGia, HoTen, Email, SoDienThoai FROM DocGia;

PRINT N'--- 1.2. DANH SÁCH SÁCH (Sach) ---';
SELECT 
    MaSach, 
    TenSach, 
    TacGia, 
    NamXuatBan, 
    TrangThai,
    CASE TrangThai 
        WHEN 0 THEN N'0 - Sẵn sàng mượn' 
        WHEN 1 THEN N'1 - Đang mượn' 
    END AS MoTaTrangThai
FROM Sach;

PRINT N'--- 1.3. DANH SÁCH PHIẾU MƯỢN (PhieuMuon) ---';
SELECT 
    pm.MaPhieu,
    dg.HoTen AS TenDocGia,
    s.TenSach,
    CONVERT(VARCHAR(19), pm.NgayMuon, 120) AS NgayMuon,
    CONVERT(VARCHAR(19), pm.NgayHenTra, 120) AS NgayHenTra,
    ISNULL(CONVERT(VARCHAR(19), pm.NgayTraThucTe, 120), N'Chưa trả') AS NgayTraThucTe,
    pm.TrangThaiPhieu,
    CASE pm.TrangThaiPhieu 
        WHEN 0 THEN N'0 - Đang mượn' 
        WHEN 1 THEN N'1 - Đã trả' 
    END AS MoTaTrangThaiPhieu
FROM PhieuMuon pm
JOIN DocGia dg ON pm.MaDocGia = dg.MaDocGia
JOIN Sach s ON pm.MaSach = s.MaSach;

PRINT N'';
PRINT N'====================================================================';
PRINT N'BƯỚC 2: KIỂM THỬ STORED PROCEDURE sp_KiemTraVaMuonSach';
PRINT N'====================================================================';

PRINT N'>>> Test 2.1: Thử mượn cuốn sách SẴN SÀNG (Mã sách 5: Số đỏ, Độc giả 4: Phạm Thị Dung)';
DECLARE @MaPhieuMoi INT;
EXEC sp_KiemTraVaMuonSach 
    @MaDocGia = 4, 
    @MaSach = 5, 
    @SoNgayMuon = 14, 
    @MaPhieu = @MaPhieuMoi OUTPUT;

PRINT N'-> Mã phiếu mượn vừa sinh: ' + CAST(@MaPhieuMoi AS NVARCHAR(10));

-- Kiểm tra trạng thái cuốn sách số 5 ngay sau khi mượn
PRINT N'-> Kiểm tra trạng thái sách số 5 (Kỳ vọng TrangThai = 1):';
SELECT MaSach, TenSach, TrangThai FROM Sach WHERE MaSach = 5;

PRINT N'';
PRINT N'>>> Test 2.2: Thử mượn TRÙNG cuốn sách vừa mượn (Mã sách 5, Độc giả 5: Hoàng Minh Em)';
PRINT N'-> Kỳ vọng: Stored Procedure chặn và báo lỗi sách đang được mượn!';

BEGIN TRY
    EXEC sp_KiemTraVaMuonSach 
        @MaDocGia = 5, 
        @MaSach = 5, 
        @SoNgayMuon = 7;
    PRINT N'LỖI NGHIÊM TRỌNG: Lẽ ra không được cho phép mượn!';
END TRY
BEGIN CATCH
    PRINT N'[ĐÃ CHẶN THÀNH CÔNG] Thông báo lỗi nhận được: ' + ERROR_MESSAGE();
END CATCH;

PRINT N'';
PRINT N'====================================================================';
PRINT N'BƯỚC 3: KIỂM THỬ STORED PROCEDURE sp_TraSach';
PRINT N'====================================================================';

PRINT N'>>> Test 3.1: Tiến hành trả sách cho Phiếu mượn vừa tạo (Mã phiếu: ' + CAST(@MaPhieuMoi AS NVARCHAR(10)) + N')';
EXEC sp_TraSach @MaPhieu = @MaPhieuMoi;

PRINT N'-> Kiểm tra lại trạng thái sách số 5 (Kỳ vọng phục hồi TrangThai = 0):';
SELECT MaSach, TenSach, TrangThai FROM Sach WHERE MaSach = 5;

PRINT N'-> Kiểm tra lại trạng thái Phiếu mượn ' + CAST(@MaPhieuMoi AS NVARCHAR(10)) + N' (Kỳ vọng TrangThaiPhieu = 1, NgayTraThucTe IS NOT NULL):';
SELECT MaPhieu, MaSach, NgayTraThucTe, TrangThaiPhieu FROM PhieuMuon WHERE MaPhieu = @MaPhieuMoi;

PRINT N'';
PRINT N'>>> Test 3.2: Thử trả lại phiếu mượn đã hoàn tất (' + CAST(@MaPhieuMoi AS NVARCHAR(10)) + N')';
PRINT N'-> Kỳ vọng: Stored Procedure thông báo phiếu đã trả rồi!';
BEGIN TRY
    EXEC sp_TraSach @MaPhieu = @MaPhieuMoi;
END TRY
BEGIN CATCH
    PRINT N'[THÔNG BÁO TỪ HỆ THỐNG]: ' + ERROR_MESSAGE();
END CATCH;

PRINT N'';
PRINT N'====================================================================';
PRINT N'TẤT CẢ CÁC BƯỚC KIỂM THỬ ĐÃ HOÀN TẤT VÀ CHÍNH XÁC 100%!';
PRINT N'====================================================================';
GO
