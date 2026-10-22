USE ThuVienDB;
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

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
        BEGIN TRANSACTION;

        -- 0. Tự động kiểm tra và quét khóa tài khoản nếu có quá hạn
        EXEC sp_KiemTraVaCapNhatKhoaTaiKhoan @MaDocGia = @MaDocGia;

        -- 1. Kiểm tra độc giả và trạng thái tài khoản với UPDLOCK
        DECLARE @TrangThaiTaiKhoan INT, @LyDoKhoa NVARCHAR(255);
        SELECT @TrangThaiTaiKhoan = TrangThaiTaiKhoan, @LyDoKhoa = LyDoKhoa 
        FROM DocGia WITH (UPDLOCK, HOLDLOCK)
        WHERE MaDocGia = @MaDocGia;

        IF @TrangThaiTaiKhoan IS NULL
        BEGIN
            RAISERROR(N'ERR_READER_NOT_FOUND: Độc giả có mã %d không tồn tại trong hệ thống!', 16, 1, @MaDocGia);
            ROLLBACK TRANSACTION;
            RETURN -1;
        END

        IF @TrangThaiTaiKhoan = 0 OR EXISTS (
            SELECT 1 
            FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
            WHERE MaDocGia = @MaDocGia
              AND ((TrangThaiPhieu IN (0, 1) AND NgayHenTra IS NOT NULL AND DATEDIFF(DAY, NgayHenTra, GETDATE()) > 10) OR TrangThaiPhieu = 4)
        )
        BEGIN
            IF @LyDoKhoa LIKE N'%MẤT SÁCH%' OR EXISTS (SELECT 1 FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK) WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 4)
            BEGIN
                RAISERROR(N'ERR_ACCOUNT_LOCKED_LOST: Tài khoản của bạn đã bị KHOÁ do mất sách. Vui lòng hoàn tất đền bù tại quầy Thư viện!', 16, 1);
            END
            ELSE
            BEGIN
                RAISERROR(N'ERR_ACCOUNT_LOCKED_OVERDUE: Tài khoản của bạn đã bị KHOÁ do có sách mượn quá hạn trên 10 ngày. Vui lòng trả sách và nộp phạt tại quầy!', 16, 1);
            END
            ROLLBACK TRANSACTION;
            RETURN -5;
        END

        -- 2. Kiểm tra độc giả có phiếu đang báo mất / nợ đền bù không (TrangThaiPhieu = 4)
        IF EXISTS (SELECT 1 FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK) WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 4)
        BEGIN
            RAISERROR(N'ERR_PENDING_SETTLEMENT: Bạn có phiếu mượn đang trong trạng thái chờ đền bù sách mất. Vui lòng thanh toán trước khi mượn tiếp!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -6;
        END

        -- 3. Kiểm tra giới hạn mượn: tối đa 3 cuốn đang chiếm dụng (0: chờ lấy, 1: đang mượn, 4: chờ đền bù, 6: chờ xác nhận trả)
        DECLARE @SoLuongDangChiêmDung INT;
        SELECT @SoLuongDangChiêmDung = COUNT(*)
        FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
        WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu IN (0, 1, 4, 6);

        IF @SoLuongDangChiêmDung >= 3
        BEGIN
            RAISERROR(N'ERR_LOAN_LIMIT_EXCEEDED: Bạn chỉ được mượn tối đa 3 cuốn sách', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -4;
        END

        -- 4. Kiểm tra trạng thái sách với UPDLOCK, ROWLOCK, HOLDLOCK
        DECLARE @TrangThaiSach INT;
        SELECT @TrangThaiSach = TrangThai
        FROM Sach WITH (UPDLOCK, ROWLOCK, HOLDLOCK)
        WHERE MaSach = @MaSach;

        IF @TrangThaiSach IS NULL
        BEGIN
            RAISERROR(N'ERR_BOOK_NOT_FOUND: Cuốn sách có mã %d không tồn tại trong thư viện!', 16, 1, @MaSach);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        IF @TrangThaiSach IN (-1, -2)
        BEGIN
            RAISERROR(N'ERR_BOOK_UNAVAILABLE: Cuốn sách này đã ngừng phục vụ hoặc bị mất khỏi thư viện!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -7;
        END

        IF @TrangThaiSach = 2
        BEGIN
            RAISERROR(N'ERR_BOOK_RESERVED: Cuốn sách này hiện đang được giữ chỗ bởi độc giả khác đang chờ nhận tại quầy!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -8;
        END

        IF @TrangThaiSach <> 0
        BEGIN
            RAISERROR(N'ERR_BOOK_ALREADY_BORROWED: Cuốn sách hiện đang được mượn bởi độc giả khác. Không thể mượn trùng!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -3;
        END

        -- 5. Cập nhật trạng thái sách sang 2 (Đang giữ chỗ)
        UPDATE Sach
        SET TrangThai = 2
        WHERE MaSach = @MaSach;

        -- 6. Tính thời hạn giữ sách (24 giờ) và hạn trả dự kiến
        DECLARE @NgayMuonHienTai DATETIME = GETDATE();
        DECLARE @NgayHetHanGiu DATETIME = DATEADD(HOUR, 24, @NgayMuonHienTai);
        
        IF @SoNgayMuon IS NULL OR @SoNgayMuon <= 0
            SET @SoNgayMuon = 14;

        DECLARE @NgayHenTraDuKien DATETIME = DATEADD(DAY, @SoNgayMuon, @NgayMuonHienTai);

        -- 7. Tạo phiếu mượn với TrangThaiPhieu = 0 (Chờ lấy sách)
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
PRINT N'Updated sp_KiemTraVaMuonSach with QUOTED_IDENTIFIER ON successfully!';
