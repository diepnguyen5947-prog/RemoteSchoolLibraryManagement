-- update_so_ngay_muon.sql
-- Tùy biến thời gian mượn sách, không gán mặc định 14 ngày
USE ThuVienDB;
GO

-- 1. Bổ sung cột SoNgayMuon vào bảng PhieuMuon nếu chưa có
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('PhieuMuon') AND name = 'SoNgayMuon')
BEGIN
    ALTER TABLE PhieuMuon ADD SoNgayMuon INT NULL DEFAULT 14;
    PRINT N'-> Đã bổ sung cột SoNgayMuon vào bảng PhieuMuon thành công.';
END
ELSE
BEGIN
    PRINT N'-> Cột SoNgayMuon đã tồn tại trong bảng PhieuMuon.';
END
GO

-- 2. Cập nhật dữ liệu cũ cho cột SoNgayMuon
UPDATE PhieuMuon
SET SoNgayMuon = CASE 
    WHEN NgayHenTra IS NOT NULL AND NgayMuon IS NOT NULL AND DATEDIFF(DAY, NgayMuon, NgayHenTra) > 0 
        THEN DATEDIFF(DAY, NgayMuon, NgayHenTra)
    ELSE 14
END
WHERE SoNgayMuon IS NULL;
GO

-- Cập nhật 2 phiếu test 44 và 45 mà độc giả vừa chọn 1 ngày nhưng bị gán nhầm +14 ngày
UPDATE PhieuMuon
SET SoNgayMuon = 1,
    NgayHenTra = DATEADD(DAY, 1, NgayMuon)
WHERE MaPhieu IN (44, 45);
PRINT N'-> Đã hiệu chỉnh phiếu test #44 và #45 về đúng hạn 1 ngày.';
GO

-- 3. Cập nhật Stored Procedure: sp_KiemTraVaMuonSach
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
        -- 0. Tự động kiểm tra và quét khóa tài khoản nếu có quá hạn > 10 ngày
        EXEC sp_KiemTraVaCapNhatKhoaTaiKhoan @MaDocGia = @MaDocGia;

        -- 1. Kiểm tra độc giả và trạng thái tài khoản
        DECLARE @TrangThaiTaiKhoan INT, @LyDoKhoa NVARCHAR(255);
        SELECT @TrangThaiTaiKhoan = TrangThaiTaiKhoan, @LyDoKhoa = LyDoKhoa 
        FROM DocGia WHERE MaDocGia = @MaDocGia;

        IF @TrangThaiTaiKhoan IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Độc giả có mã %d không tồn tại trong hệ thống!', 16, 1, @MaDocGia);
            RETURN -1;
        END

        IF @TrangThaiTaiKhoan = 0 OR EXISTS (
            SELECT 1 
            FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
            WHERE MaDocGia = @MaDocGia
              AND TrangThaiPhieu IN (0, 1)
              AND NgayHenTra IS NOT NULL
              AND DATEDIFF(DAY, NgayHenTra, GETDATE()) > 10
        )
        BEGIN
            IF @TrangThaiTaiKhoan <> 0
            BEGIN
                UPDATE DocGia 
                SET TrangThaiTaiKhoan = 0, 
                    LyDoKhoa = N'Có sách mượn quá hạn trên 10 ngày (cần trả sách và xử lý vi phạm tại quầy)' 
                WHERE MaDocGia = @MaDocGia;
            END

            RAISERROR(N'Tài khoản của bạn đã bị KHOÁ do có sách mượn quá hạn trên 10 ngày. Vui lòng mang sách đến quầy Thư viện để trả sách, thanh toán phí phạt và làm thủ tục mở khóa!', 16, 1);
            RETURN -5;
        END

        BEGIN TRANSACTION;

        -- 2. Kiểm tra độc giả có phiếu đang báo mất / nợ đền bù không (TrangThaiPhieu = 4)
        IF EXISTS (SELECT 1 FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK) WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 4)
        BEGIN
            RAISERROR(N'Lỗi: Bạn có phiếu mượn đang trong trạng thái chờ đền bù sách mất. Vui lòng thanh toán trước khi mượn tiếp!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -6;
        END

        -- 3. Kiểm tra giới hạn mượn: tối đa 3 cuốn đang giữ chỗ hoặc đang mượn (TrangThaiPhieu IN (0, 1))
        DECLARE @SoLuongDangGiuHoacMuon INT;
        SELECT @SoLuongDangGiuHoacMuon = COUNT(*)
        FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
        WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu IN (0, 1);

        IF @SoLuongDangGiuHoacMuon >= 3
        BEGIN
            RAISERROR(N'Lỗi: Độc giả đã có 3 cuốn sách đang giữ chỗ hoặc đang mượn chưa trả. Vui lòng nhận sách hoặc trả bớt sách trước!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -4;
        END

        -- 4. Kiểm tra trạng thái sách:
        DECLARE @TrangThaiSach INT;
        SELECT @TrangThaiSach = TrangThai
        FROM Sach WITH (UPDLOCK, ROWLOCK, HOLDLOCK)
        WHERE MaSach = @MaSach;

        IF @TrangThaiSach IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Cuốn sách có mã %d không tồn tại trong thư viện!', 16, 1, @MaSach);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        IF @TrangThaiSach IN (-1, -2)
        BEGIN
            RAISERROR(N'Lỗi: Cuốn sách này đã ngừng phục vụ hoặc bị mất khỏi thư viện!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -7;
        END

        IF @TrangThaiSach = 2
        BEGIN
            RAISERROR(N'Lỗi: Cuốn sách này hiện đang được giữ chỗ bởi một độc giả khác đang chờ đến nhận tại quầy!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -8;
        END

        IF @TrangThaiSach <> 0
        BEGIN
            RAISERROR(N'Lỗi: Cuốn sách hiện đang được mượn bởi độc giả khác. Không thể mượn trùng!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -3;
        END

        -- 5. Cập nhật trạng thái sách sang 2 (Đang giữ chỗ)
        UPDATE Sach
        SET TrangThai = 2
        WHERE MaSach = @MaSach;

        -- 6. Tính thời hạn giữ sách (24 giờ) và hạn trả dự kiến theo số ngày độc giả đã chọn
        DECLARE @NgayMuonHienTai DATETIME = GETDATE();
        DECLARE @NgayHetHanGiu DATETIME = DATEADD(HOUR, 24, @NgayMuonHienTai);
        
        IF @SoNgayMuon IS NULL OR @SoNgayMuon <= 0
            SET @SoNgayMuon = 14;

        DECLARE @NgayHenTraDuKien DATETIME = DATEADD(DAY, @SoNgayMuon, @NgayMuonHienTai);

        -- 7. Tạo phiếu mượn với TrangThaiPhieu = 0 (Chờ lấy sách), lưu hạn trả dự kiến và số ngày mượn đã chọn
        INSERT INTO PhieuMuon (MaDocGia, MaSach, NgayMuon, NgayHenTra, NgayTraThucTe, TrangThaiPhieu, TienDenBu, NgayHetHanGiu, SoNgayMuon)
        VALUES (@MaDocGia, @MaSach, @NgayMuonHienTai, @NgayHenTraDuKien, NULL, 0, 0, @NgayHetHanGiu, @SoNgayMuon);

        SET @MaPhieu = SCOPE_IDENTITY();

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaDocGia AS MaDocGia,
            @MaSach AS MaSach,
            @NgayMuonHienTai AS NgayDangKy,
            @NgayHetHanGiu AS NgayHetHanGiu,
            @NgayHenTraDuKien AS NgayHenTraDuKien,
            @SoNgayMuon AS SoNgayMuon,
            0 AS TrangThaiPhieu,
            N'Đăng ký giữ sách thành công! Vui lòng đến thư viện nhận sách trước ' + CONVERT(NVARCHAR(20), @NgayHetHanGiu, 120) AS ThongBaoKetQua;

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

-- 4. Cập nhật Stored Procedure: sp_XacNhanGiaoSach
CREATE OR ALTER PROCEDURE sp_XacNhanGiaoSach
    @MaPhieu INT,
    @SoNgayMuon INT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        DECLARE @MaSach INT;
        DECLARE @MaDocGia INT;
        DECLARE @TrangThaiPhieu INT;
        DECLARE @NgayHetHanGiu DATETIME;
        DECLARE @SoNgayMuonDaLuu INT;

        SELECT 
            @MaSach = MaSach,
            @MaDocGia = MaDocGia,
            @TrangThaiPhieu = TrangThaiPhieu,
            @NgayHetHanGiu = NgayHetHanGiu,
            @SoNgayMuonDaLuu = ISNULL(SoNgayMuon, 14)
        FROM PhieuMuon WITH (UPDLOCK, ROWLOCK)
        WHERE MaPhieu = @MaPhieu;

        IF @MaSach IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d không tồn tại!', 16, 1, @MaPhieu);
            RETURN -1;
        END

        IF @TrangThaiPhieu <> 0
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d không ở trạng thái Chờ lấy sách (TrangThaiPhieu = %d)!', 16, 1, @MaPhieu, @TrangThaiPhieu);
            RETURN -2;
        END

        BEGIN TRANSACTION;

        -- Kiểm tra nếu đã quá hạn 24 giờ giữ sách
        IF @NgayHetHanGiu IS NOT NULL AND GETDATE() > @NgayHetHanGiu
        BEGIN
            UPDATE PhieuMuon SET TrangThaiPhieu = 3 WHERE MaPhieu = @MaPhieu;
            UPDATE Sach SET TrangThai = 0 WHERE MaSach = @MaSach;
            COMMIT TRANSACTION;

            RAISERROR(N'Lỗi: Phiếu mượn đã quá hạn 24 giờ giữ sách và đã tự động chuyển sang Đã hủy!', 16, 1);
            RETURN -3;
        END

        -- Xác định số ngày mượn: ưu tiên số ngày Thủ thư tùy chỉnh khi giao, nếu không thì dùng số ngày Độc giả đã đăng ký
        DECLARE @SoNgayApDung INT = @SoNgayMuonDaLuu;
        IF @SoNgayMuon IS NOT NULL AND @SoNgayMuon > 0
        BEGIN
            SET @SoNgayApDung = @SoNgayMuon;
        END

        -- Chuyển phiếu mượn sang 1 (ĐANG MƯỢN THỰC TẾ)
        -- Hạn trả tính từ thời điểm giao sách + số ngày mượn áp dụng
        DECLARE @NgayGiaoSach DATETIME = GETDATE();
        DECLARE @NgayHenTra DATETIME = DATEADD(DAY, @SoNgayApDung, @NgayGiaoSach);

        UPDATE PhieuMuon
        SET TrangThaiPhieu = 1,
            NgayMuon = @NgayGiaoSach,
            NgayHenTra = @NgayHenTra,
            SoNgayMuon = @SoNgayApDung
        WHERE MaPhieu = @MaPhieu;

        -- Cập nhật sách sang 1 (Đang cho mượn thực tế)
        UPDATE Sach
        SET TrangThai = 1
        WHERE MaSach = @MaSach;

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaSach AS MaSach,
            @MaDocGia AS MaDocGia,
            @NgayGiaoSach AS NgayMuon,
            @NgayHenTra AS NgayHenTra,
            @SoNgayApDung AS SoNgayMuon,
            1 AS TrangThaiPhieu,
            N'Xác nhận giao sách thành công! Đã chuyển sang trạng thái Đang mượn (hạn trả: ' + CONVERT(NVARCHAR(20), @NgayHenTra, 103) + N').' AS ThongBaoKetQua;

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

PRINT N'-> Cập nhật hoàn tất sp_KiemTraVaMuonSach và sp_XacNhanGiaoSach hỗ trợ tùy biến thời gian mượn!';
GO
