-- ====================================================================
-- KỊCH BẢN AUDIT CƠ SỞ DỮ LIỆU THƯ VIỆN (CHẾ ĐỘ READ-ONLY)
-- Hệ thống: Remote School Library Management - CMP180
-- ====================================================================

USE ThuVienDB;
GO

PRINT N'====================================================================';
PRINT N'  BÁO CÁO TOÀN VẸN CƠ SỞ DỮ LIỆU THUVIENDB (AUDIT INSPECTION)';
PRINT N'====================================================================';

-- 1. Kiểm tra bất biến cốt lõi: Có bản sách nào bị mượn/giữ chỗ trùng (nhiều hơn 1 phiếu active) không?
PRINT N'[KIỂM TRA 1] Quét các bản sách bị vi phạm chiếm dụng trùng (Active Loans > 1)...';
SELECT 
    pm.MaSach,
    s.TenSach,
    COUNT(*) AS SoLuongPhieuChiemDung,
    STRING_AGG(CONVERT(NVARCHAR(20), pm.MaPhieu), N', ') AS DanhSachMaPhieu,
    STRING_AGG(CONVERT(NVARCHAR(10), pm.TrangThaiPhieu), N', ') AS DanhSachTrangThaiPhieu
FROM PhieuMuon pm
JOIN Sach s ON pm.MaSach = s.MaSach
WHERE pm.TrangThaiPhieu IN (0, 1, 4, 6)
GROUP BY pm.MaSach, s.TenSach
HAVING COUNT(*) > 1;

IF @@ROWCOUNT = 0
    PRINT N'-> ĐẠT: Không có bất kỳ bản sách nào bị mượn/giữ chỗ trùng!';
ELSE
    PRINT N'-> CẢNH BÁO: Phát hiện vi phạm bất biến mượn trùng!';

-- 2. Kiểm tra tính nhất quán giữa trạng thái Sách và Phiếu mượn
PRINT N'[KIỂM TRA 2] Quét lệch pha trạng thái giữa bảng Sach và bảng PhieuMuon...';
-- 2a. Sách có TrangThai = 0 (Sẵn sàng) nhưng vẫn có phiếu mượn active
SELECT 
    s.MaSach, s.TenSach, s.TrangThai AS TrangThaiSach,
    pm.MaPhieu, pm.TrangThaiPhieu
FROM Sach s
JOIN PhieuMuon pm ON s.MaSach = pm.MaSach
WHERE s.TrangThai = 0 AND pm.TrangThaiPhieu IN (0, 1, 4, 6);

IF @@ROWCOUNT = 0
    PRINT N'-> ĐẠT: Không có sách sẵn sàng nào bị kẹt phiếu mượn active.';
ELSE
    PRINT N'-> CẢNH BÁO: Có sách sẵn sàng nhưng phiếu mượn vẫn active!';

-- 2b. Sách có TrangThai = 1 (Đang mượn) hoặc 2 (Giữ chỗ) nhưng KHÔNG có phiếu mượn active tương ứng
SELECT 
    s.MaSach, s.TenSach, s.TrangThai AS TrangThaiSach
FROM Sach s
WHERE s.TrangThai IN (1, 2)
  AND NOT EXISTS (
      SELECT 1 FROM PhieuMuon pm 
      WHERE pm.MaSach = s.MaSach AND pm.TrangThaiPhieu IN (0, 1, 4, 6)
  );

IF @@ROWCOUNT = 0
    PRINT N'-> ĐẠT: Mọi sách đang mượn/giữ chỗ đều có phiếu mượn active hợp lệ.';
ELSE
    PRINT N'-> CẢNH BÁO: Phát hiện sách bị khóa trạng thái mồ côi (không có phiếu active tương ứng)!';

-- 3. Kiểm tra dữ liệu mồ côi (Orphaned records)
PRINT N'[KIỂM TRA 3] Quét bản ghi mồ côi (Foreign Key Integrity)...';
SELECT pm.MaPhieu, pm.MaDocGia, pm.MaSach
FROM PhieuMuon pm
LEFT JOIN DocGia dg ON pm.MaDocGia = dg.MaDocGia
LEFT JOIN Sach s ON pm.MaSach = s.MaSach
WHERE dg.MaDocGia IS NULL OR s.MaSach IS NULL;

IF @@ROWCOUNT = 0
    PRINT N'-> ĐẠT: Toàn bộ khóa ngoại giữa PhieuMuon, DocGia và Sach hoàn toàn toàn vẹn.';
ELSE
    PRINT N'-> CẢNH BÁO: Phát hiện phiếu mượn mồ côi không có độc giả hoặc sách tương ứng!';

-- 4. Kiểm tra sự tồn tại của Unique Filtered Index bảo vệ bất biến
PRINT N'[KIỂM TRA 4] Kiểm tra Unique Filtered Index UQ_PhieuMuon_ActiveSach...';
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_PhieuMuon_ActiveSach' AND object_id = OBJECT_ID('PhieuMuon'))
    PRINT N'-> ĐẠT: Index UQ_PhieuMuon_ActiveSach đang hoạt động bảo vệ tầng CSDL!';
ELSE
    PRINT N'-> CẢNH BÁO: Chưa tìm thấy Index UQ_PhieuMuon_ActiveSach!';

PRINT N'====================================================================';
PRINT N'  HOÀN TẤT AUDIT KIỂM TRA DỮ LIỆU THÀNH CÔNG!';
PRINT N'====================================================================';
GO
