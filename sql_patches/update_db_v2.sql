USE ThuVienDB;
GO

-- 1. Cập nhật Stored Procedure sp_KiemTraVaMuonSach bổ sung kiểm tra giới hạn 3 cuốn sách
CREATE OR ALTER PROCEDURE sp_KiemTraVaMuonSach
    @MaDocGia INT,
    @MaSach INT,
    @SoNgayMuon INT = 14,
    @MaPhieu INT = NULL OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        -- Kiểm tra sự tồn tại của độc giả
        IF NOT EXISTS (SELECT 1 FROM DocGia WHERE MaDocGia = @MaDocGia)
        BEGIN
            RAISERROR(N'Lỗi: Độc giả có mã %d không tồn tại trong hệ thống!', 16, 1, @MaDocGia);
            RETURN -1;
        END

        -- Bắt đầu Transaction
        BEGIN TRANSACTION;

        -- KIỂM TRA GIỚI HẠN MƯỢN SÁCH: Tối đa 3 cuốn sách chưa trả
        DECLARE @SoLuongDangMuon INT;
        SELECT @SoLuongDangMuon = COUNT(*)
        FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
        WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 0;

        IF @SoLuongDangMuon >= 3
        BEGIN
            RAISERROR(N'Lỗi: Độc giả mã %d đã mượn tối đa 3 cuốn sách chưa trả. Vui lòng trả bớt sách trước khi mượn tiếp!', 16, 1, @MaDocGia);
            ROLLBACK TRANSACTION;
            RETURN -4;
        END

        DECLARE @TrangThaiSach INT;

        -- Khóa độc quyền dòng sách bằng UPDLOCK, ROWLOCK, HOLDLOCK
        SELECT @TrangThaiSach = TrangThai
        FROM Sach WITH (UPDLOCK, ROWLOCK, HOLDLOCK)
        WHERE MaSach = @MaSach;

        -- Kiểm tra cuốn sách có tồn tại không
        IF @TrangThaiSach IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Cuốn sách có mã %d không tồn tại trong thư viện!', 16, 1, @MaDocGia);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        -- Kiểm tra trạng thái sách: chỉ cho mượn khi TrangThai = 0 (Sẵn sàng)
        IF @TrangThaiSach <> 0
        BEGIN
            RAISERROR(N'Lỗi: Cuốn sách mã %d hiện ĐANG ĐƯỢC MƯỢN bởi độc giả khác. Không thể mượn trùng!', 16, 1, @MaSach);
            ROLLBACK TRANSACTION;
            RETURN -3;
        END

        -- Cập nhật trạng thái sách sang 1 (Đang mượn)
        UPDATE Sach
        SET TrangThai = 1
        WHERE MaSach = @MaSach;

        -- Tính ngày trả dự kiến
        DECLARE @NgayMuonHienTai DATETIME = GETDATE();
        DECLARE @NgayHenTraDuKien DATETIME = DATEADD(DAY, @SoNgayMuon, @NgayMuonHienTai);

        -- Thêm bản ghi mới vào PhieuMuon
        INSERT INTO PhieuMuon (MaDocGia, MaSach, NgayMuon, NgayHenTra, NgayTraThucTe, TrangThaiPhieu)
        VALUES (@MaDocGia, @MaSach, @NgayMuonHienTai, @NgayHenTraDuKien, NULL, 0);

        -- Lấy mã phiếu vừa sinh
        SET @MaPhieu = SCOPE_IDENTITY();

        -- Xác nhận thành công
        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaDocGia AS MaDocGia,
            @MaSach AS MaSach,
            @NgayMuonHienTai AS NgayMuon,
            @NgayHenTraDuKien AS NgayHenTra,
            N'Mượn sách thành công!' AS ThongBaoKetQua;

        RETURN 0;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;

        DECLARE @ErrorMessage NVARCHAR(4000) = ERROR_MESSAGE();
        DECLARE @ErrorSeverity INT = ERROR_SEVERITY();
        DECLARE @ErrorState INT = ERROR_STATE();

        RAISERROR(@ErrorMessage, @ErrorSeverity, @ErrorState);
        RETURN -99;
    END CATCH
END;
GO

-- 2. Cập nhật ảnh bìa mẫu đẹp mắt cho các đầu sách hiện có
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=400&q=80' WHERE MaSach = 1; -- C++
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400&q=80' WHERE MaSach = 2; -- Cấu trúc dữ liệu
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80' WHERE MaSach = 3; -- AI
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&q=80' WHERE MaSach = 4; -- Lão Hạc
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=400&q=80' WHERE MaSach = 5; -- Số đỏ
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=400&q=80' WHERE MaSach = 6; -- Lịch sử văn minh
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400&q=80' WHERE MaSach = 7; -- Vũ trụ
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1635070041078-e363dbe005cb?w=400&q=80' WHERE MaSach = 8; -- Giải tích
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=400&q=80' WHERE MaSach = 9; -- SQL Server
UPDATE Sach SET HinhAnh = 'https://images.unsplash.com/photo-1512820790803-83ca734da794?w=400&q=80' WHERE MaSach = 10; -- Đắc nhân tâm
GO

-- 3. Tạo một phiếu mượn quá hạn mẫu (để kiểm tra tính ngày quá hạn & tiền phạt)
-- Thêm một phiếu mượn cũ đã quá hạn 5 ngày
IF NOT EXISTS (SELECT 1 FROM PhieuMuon WHERE MaDocGia = 3 AND MaSach = 8)
BEGIN
    INSERT INTO PhieuMuon (MaDocGia, MaSach, NgayMuon, NgayHenTra, NgayTraThucTe, TrangThaiPhieu)
    VALUES (3, 8, DATEADD(DAY, -20, GETDATE()), DATEADD(DAY, -5, GETDATE()), NULL, 0);
    UPDATE Sach SET TrangThai = 1 WHERE MaSach = 8;
END
GO

PRINT 'Cap nhat CSDL thanh cong!';
