-- update_khoa_tai_khoan_quahan.sql
-- Tự động khóa tài khoản khi có sách mượn quá hạn trên 10 ngày (DATEDIFF > 10)
USE ThuVienDB;
GO

-- 1. Bổ sung cột LyDoKhoa vào bảng DocGia nếu chưa có
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('DocGia') AND name = 'LyDoKhoa')
BEGIN
    ALTER TABLE DocGia ADD LyDoKhoa NVARCHAR(255) NULL;
    PRINT N'-> Đã bổ sung cột LyDoKhoa vào bảng DocGia';
END
ELSE
BEGIN
    PRINT N'-> Cột LyDoKhoa đã tồn tại trong bảng DocGia';
END
GO

-- 2. Stored Procedure: sp_KiemTraVaCapNhatKhoaTaiKhoan
-- Quét các phiếu mượn (TrangThaiPhieu = 0 hoặc 1) có DATEDIFF(DAY, NgayHenTra, GETDATE()) > 10
-- Tự động cập nhật TrangThaiTaiKhoan = 0 và ghi nhận lý do
CREATE OR ALTER PROCEDURE sp_KiemTraVaCapNhatKhoaTaiKhoan
    @MaDocGia INT = NULL,
    @SoDocGiaBiKhoa INT = 0 OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    -- Cập nhật TrangThaiTaiKhoan = 0 cho các độc giả có sách quá hạn > 10 ngày
    UPDATE dg
    SET dg.TrangThaiTaiKhoan = 0,
        dg.LyDoKhoa = N'Có sách mượn quá hạn trên 10 ngày (cần trả sách và xử lý vi phạm tại quầy)'
    FROM DocGia dg
    WHERE (@MaDocGia IS NULL OR dg.MaDocGia = @MaDocGia)
      AND dg.TrangThaiTaiKhoan = 1
      AND EXISTS (
          SELECT 1
          FROM PhieuMuon pm
          WHERE pm.MaDocGia = dg.MaDocGia
            AND pm.TrangThaiPhieu IN (0, 1)
            AND pm.NgayHenTra IS NOT NULL
            AND DATEDIFF(DAY, pm.NgayHenTra, GETDATE()) > 10
      );

    SET @SoDocGiaBiKhoa = @@ROWCOUNT;

    -- Đồng thời cập nhật lại lý do nếu tài khoản đã bị khóa do quá hạn > 10 ngày
    UPDATE dg
    SET dg.LyDoKhoa = N'Có sách mượn quá hạn trên 10 ngày (cần trả sách và xử lý vi phạm tại quầy)'
    FROM DocGia dg
    WHERE (@MaDocGia IS NULL OR dg.MaDocGia = @MaDocGia)
      AND dg.TrangThaiTaiKhoan = 0
      AND (dg.LyDoKhoa IS NULL OR dg.LyDoKhoa = '')
      AND EXISTS (
          SELECT 1
          FROM PhieuMuon pm
          WHERE pm.MaDocGia = dg.MaDocGia
            AND pm.TrangThaiPhieu IN (0, 1)
            AND pm.NgayHenTra IS NOT NULL
            AND DATEDIFF(DAY, pm.NgayHenTra, GETDATE()) > 10
      );

    RETURN @SoDocGiaBiKhoa;
END;
GO

-- 3. Stored Procedure: sp_MoKhoaTaiKhoan
-- Dành riêng cho Thủ thư: mở khóa tài khoản sau khi độc giả hoàn tất trả sách và nộp phạt
CREATE OR ALTER PROCEDURE sp_MoKhoaTaiKhoan
    @MaDocGia INT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM DocGia WHERE MaDocGia = @MaDocGia)
    BEGIN
        RAISERROR(N'Lỗi: Độc giả có mã %d không tồn tại trong hệ thống!', 16, 1, @MaDocGia);
        RETURN -1;
    END

    -- Kiểm tra nếu độc giả VẪN CÒN sách mượn quá hạn > 10 ngày chưa trả
    IF EXISTS (
        SELECT 1
        FROM PhieuMuon pm
        WHERE pm.MaDocGia = @MaDocGia
          AND pm.TrangThaiPhieu IN (0, 1)
          AND pm.NgayHenTra IS NOT NULL
          AND DATEDIFF(DAY, pm.NgayHenTra, GETDATE()) > 10
    )
    BEGIN
        RAISERROR(N'Không thể mở khóa: Độc giả vẫn còn sách mượn quá hạn trên 10 ngày chưa hoàn tất trả tại quầy!', 16, 1);
        RETURN -2;
    END

    -- Cập nhật mở khóa
    UPDATE DocGia
    SET TrangThaiTaiKhoan = 1,
        LyDoKhoa = NULL
    WHERE MaDocGia = @MaDocGia;

    SELECT MaDocGia, HoTen, Email, TrangThaiTaiKhoan, N'Mở khóa tài khoản thành công' AS ThongBao
    FROM DocGia
    WHERE MaDocGia = @MaDocGia;

    RETURN 0;
END;
GO

-- 4. Cập nhật Stored Procedure: sp_KiemTraVaMuonSach
-- Kiểm tra nghiêm ngặt: nếu TrangThaiTaiKhoan = 0 hoặc có sách quá hạn > 10 ngày -> TỪ CHỐI MƯỢN
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

        -- Kiểm tra nếu tài khoản đang bị khóa hoặc còn sách mượn quá hạn > 10 ngày
        IF @TrangThaiTaiKhoan = 0 OR EXISTS (
            SELECT 1 
            FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
            WHERE MaDocGia = @MaDocGia
              AND TrangThaiPhieu IN (0, 1)
              AND NgayHenTra IS NOT NULL
              AND DATEDIFF(DAY, NgayHenTra, GETDATE()) > 10
        )
        BEGIN
            -- Đảm bảo đồng bộ cập nhật khóa nếu chưa được cập nhật
            IF @TrangThaiTaiKhoan <> 0
            BEGIN
                UPDATE DocGia 
                SET TrangThaiTaiKhoan = 0, 
                    LyDoKhoa = N'Có sách mượn quá hạn trên 10 ngày' 
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

        -- 6. Tính thời hạn giữ sách (mặc định = GETDATE() + 24 giờ)
        DECLARE @NgayMuonHienTai DATETIME = GETDATE();
        DECLARE @NgayHetHanGiu DATETIME = DATEADD(HOUR, 24, @NgayMuonHienTai);

        -- 7. Tạo phiếu mượn với TrangThaiPhieu = 0 (Chờ lấy sách)
        INSERT INTO PhieuMuon (MaDocGia, MaSach, NgayMuon, NgayHenTra, NgayTraThucTe, TrangThaiPhieu, TienDenBu, NgayHetHanGiu)
        VALUES (@MaDocGia, @MaSach, @NgayMuonHienTai, NULL, NULL, 0, 0, @NgayHetHanGiu);

        SET @MaPhieu = SCOPE_IDENTITY();

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaDocGia AS MaDocGia,
            @MaSach AS MaSach,
            @NgayMuonHienTai AS NgayDangKy,
            @NgayHetHanGiu AS NgayHetHanGiu,
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

PRINT N'-> Cập nhật hoàn tất sp_KiemTraVaCapNhatKhoaTaiKhoan, sp_MoKhoaTaiKhoan và sp_KiemTraVaMuonSach!';
GO
