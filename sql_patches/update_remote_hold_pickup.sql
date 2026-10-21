-- ====================================================================
-- CẬP NHẬT & CHUẨN HÓA LUỒNG MƯỢN SÁCH TỪ XA (REMOTE HOLD & PICKUP WORKFLOW)
-- Cơ sở dữ liệu: ThuVienDB (SQL Server)
-- ====================================================================
USE ThuVienDB;
GO

-- 1. Bổ sung cột NgayHetHanGiu vào bảng PhieuMuon
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'PhieuMuon') AND name = 'NgayHetHanGiu')
BEGIN
    ALTER TABLE PhieuMuon ADD NgayHetHanGiu DATETIME NULL;
    PRINT N'-> Đã bổ sung cột NgayHetHanGiu vào bảng PhieuMuon thành công.';
END
ELSE
BEGIN
    PRINT N'-> Cột NgayHetHanGiu đã tồn tại trong bảng PhieuMuon.';
END
GO

-- 2. Di chuyển dữ liệu cũ sang quy định mã TrangThaiPhieu chuẩn mới:
-- 0: Chờ lấy sách
-- 1: Đang mượn thực tế
-- 2: Đã trả sách
-- 3: Đã hủy (do quá hạn 24h)
-- 4: Báo mất / Chờ đền bù
-- Nếu trước đó 1 là Đã trả -> cập nhật sang 2
UPDATE PhieuMuon SET TrangThaiPhieu = 2 WHERE TrangThaiPhieu = 1;
-- Nếu trước đó 2 là Báo mất -> cập nhật sang 4
UPDATE PhieuMuon SET TrangThaiPhieu = 4 WHERE TrangThaiPhieu = 2;
-- Nếu trước đó 3 là Đã đền bù hoàn tất -> cập nhật sang 2 (hoặc giữ đã giải quyết)
UPDATE PhieuMuon SET TrangThaiPhieu = 2 WHERE TrangThaiPhieu = 3;
-- Nếu trước đó 0 là Đang mượn thực tế -> cập nhật sang 1
UPDATE PhieuMuon SET TrangThaiPhieu = 1 WHERE TrangThaiPhieu = 0;
GO

-- 3. Stored Procedure: sp_KiemTraVaMuonSach (Đăng ký giữ sách từ xa)
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
        -- 1. Kiểm tra độc giả và trạng thái tài khoản
        DECLARE @TrangThaiTaiKhoan INT;
        SELECT @TrangThaiTaiKhoan = TrangThaiTaiKhoan FROM DocGia WHERE MaDocGia = @MaDocGia;

        IF @TrangThaiTaiKhoan IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Độc giả có mã %d không tồn tại trong hệ thống!', 16, 1, @MaDocGia);
            RETURN -1;
        END

        IF @TrangThaiTaiKhoan = 0
        BEGIN
            RAISERROR(N'Tài khoản của bạn đang bị khóa do chưa hoàn tất đền bù sách mất/quá hạn! Vui lòng liên hệ Thủ thư để thanh toán.', 16, 1);
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
        -- 0: Sẵn sàng mượn
        -- 1: Đang cho mượn thực tế
        -- 2: Đang được giữ chỗ (Chờ độc giả đến lấy)
        -- -1 / -2: Ngừng phục vụ / Đã mất
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

        -- 7. Tạo phiếu mượn với TrangThaiPhieu = 0 (Chờ lấy sách), NgayHenTra tạm để NULL hoặc tính sau khi nhận
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

-- 4. Stored Procedure: sp_XacNhanGiaoSach (Thủ thư giao sách tại quầy)
CREATE OR ALTER PROCEDURE sp_XacNhanGiaoSach
    @MaPhieu INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        DECLARE @MaSach INT;
        DECLARE @MaDocGia INT;
        DECLARE @TrangThaiPhieu INT;
        DECLARE @NgayHetHanGiu DATETIME;

        SELECT 
            @MaSach = MaSach,
            @MaDocGia = MaDocGia,
            @TrangThaiPhieu = TrangThaiPhieu,
            @NgayHetHanGiu = NgayHetHanGiu
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
            -- Quá hạn -> Hủy phiếu và trả sách về Sẵn sàng
            UPDATE PhieuMuon SET TrangThaiPhieu = 3 WHERE MaPhieu = @MaPhieu;
            UPDATE Sach SET TrangThai = 0 WHERE MaSach = @MaSach;
            COMMIT TRANSACTION;

            RAISERROR(N'Lỗi: Phiếu mượn đã quá hạn 24 giờ giữ sách và đã tự động chuyển sang Đã hủy!', 16, 1);
            RETURN -3;
        END

        -- Chuyển phiếu mượn sang 1 (ĐANG MƯỢN THỰC TẾ)
        -- Bắt đầu tính hạn 14 ngày kể từ thời điểm giao sách
        DECLARE @NgayGiaoSach DATETIME = GETDATE();
        DECLARE @NgayHenTra DATETIME = DATEADD(DAY, 14, @NgayGiaoSach);

        UPDATE PhieuMuon
        SET TrangThaiPhieu = 1,
            NgayMuon = @NgayGiaoSach,
            NgayHenTra = @NgayHenTra
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
            1 AS TrangThaiPhieu,
            N'Xác nhận giao sách thành công! Đã chuyển sang trạng thái Đang mượn (hạn trả 14 ngày).' AS ThongBaoKetQua;

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

-- 4.1 Tạo alias sp_XacNhanGaoSach để tương thích tên gọi trong yêu cầu
CREATE OR ALTER PROCEDURE sp_XacNhanGaoSach
    @MaPhieu INT
AS
BEGIN
    EXEC sp_XacNhanGiaoSach @MaPhieu;
END;
GO

-- 5. Stored Procedure: sp_HuyPhieuChoQuaHan (Quét và dọn dẹp các phiếu quá hạn 24h không đến lấy)
CREATE OR ALTER PROCEDURE sp_HuyPhieuChoQuaHan
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

        -- Tạo bảng tạm lưu danh sách phiếu quá hạn cần hủy
        DECLARE @DanhSachHuy TABLE (MaPhieu INT, MaSach INT);

        INSERT INTO @DanhSachHuy (MaPhieu, MaSach)
        SELECT MaPhieu, MaSach
        FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
        WHERE TrangThaiPhieu = 0 
          AND NgayHetHanGiu IS NOT NULL 
          AND NgayHetHanGiu < GETDATE();

        DECLARE @SoLuongHuy INT;
        SELECT @SoLuongHuy = COUNT(*) FROM @DanhSachHuy;

        IF @SoLuongHuy > 0
        BEGIN
            -- 1. Chuyển phiếu mượn sang 3 (ĐÃ HỦY)
            UPDATE PhieuMuon
            SET TrangThaiPhieu = 3
            WHERE MaPhieu IN (SELECT MaPhieu FROM @DanhSachHuy);

            -- 2. Phục hồi trạng thái sách về 0 (Sẵn sàng mượn)
            UPDATE Sach
            SET TrangThai = 0
            WHERE MaSach IN (SELECT MaSach FROM @DanhSachHuy);
        END

        COMMIT TRANSACTION;

        SELECT 
            @SoLuongHuy AS SoLuongDaHuy,
            N'Đã quét và hủy thành công ' + CAST(@SoLuongHuy AS NVARCHAR(10)) + N' phiếu quá hạn giữ sách 24h.' AS ThongBaoKetQua;

        RETURN @SoLuongHuy;
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

-- 6. Stored Procedure: sp_TraSach (Cập nhật chuẩn TrangThaiPhieu = 2)
CREATE OR ALTER PROCEDURE sp_TraSach
    @MaPhieu INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM PhieuMuon WHERE MaPhieu = @MaPhieu)
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn mã %d không tồn tại!', 16, 1, @MaPhieu);
            RETURN -1;
        END

        BEGIN TRANSACTION;

        DECLARE @MaSach INT;
        DECLARE @TrangThaiPhieu INT;

        SELECT 
            @MaSach = MaSach,
            @TrangThaiPhieu = TrangThaiPhieu
        FROM PhieuMuon WITH (UPDLOCK, ROWLOCK)
        WHERE MaPhieu = @MaPhieu;

        IF @TrangThaiPhieu = 2
        BEGIN
            RAISERROR(N'Thông báo: Phiếu mượn mã %d này đã được hoàn tất trả sách trước đó!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        IF @TrangThaiPhieu NOT IN (1, 4) -- 1: Đang mượn, 4: Chờ trả (nếu có)
        BEGIN
            RAISERROR(N'Thông báo: Phiếu mượn mã %d không ở trạng thái đang mượn!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -3;
        END

        DECLARE @NgayTraHienTai DATETIME = GETDATE();
        -- Cập nhật phiếu mượn: TrangThaiPhieu = 2 (ĐÃ TRẢ SÁCH)
        UPDATE PhieuMuon
        SET 
            NgayTraThucTe = @NgayTraHienTai,
            TrangThaiPhieu = 2
        WHERE MaPhieu = @MaPhieu;

        -- Cập nhật sách: chuyển trạng thái về 0 (Sẵn sàng mượn)
        UPDATE Sach
        SET TrangThai = 0
        WHERE MaSach = @MaSach;

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaSach AS MaSach,
            @NgayTraHienTai AS NgayTraThucTe,
            2 AS TrangThaiPhieu,
            N'Trả sách thành công! Trạng thái sách đã được đưa về Sẵn sàng mượn.' AS ThongBaoKetQua;

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

-- 7. Stored Procedure: sp_BaoMatVaDenSach (Cập nhật chuẩn TrangThaiPhieu = 4, Sach.TrangThai = -1)
CREATE OR ALTER PROCEDURE sp_BaoMatVaDenSach
    @MaPhieu INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        DECLARE @MaDocGia INT;
        DECLARE @MaSach INT;
        DECLARE @NgayHenTra DATETIME;
        DECLARE @TrangThaiPhieu INT;
        DECLARE @GiaTienSach INT;

        SELECT 
            @MaDocGia = pm.MaDocGia,
            @MaSach = pm.MaSach,
            @NgayHenTra = pm.NgayHenTra,
            @TrangThaiPhieu = pm.TrangThaiPhieu,
            @GiaTienSach = ISNULL(s.GiaTien, 50000)
        FROM PhieuMuon pm WITH (UPDLOCK, HOLDLOCK)
        JOIN Sach s ON pm.MaSach = s.MaSach
        WHERE pm.MaPhieu = @MaPhieu;

        IF @MaDocGia IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d không tồn tại!', 16, 1, @MaPhieu);
            RETURN -1;
        END

        IF @TrangThaiPhieu = 2
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d đã được trả/hoàn tất trước đó, không thể báo mất!', 16, 1, @MaPhieu);
            RETURN -2;
        END

        IF @TrangThaiPhieu = 3
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d đã bị hủy!', 16, 1, @MaPhieu);
            RETURN -3;
        END

        IF @TrangThaiPhieu = 4
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d đã được báo mất trước đó, đang chờ đền bù!', 16, 1, @MaPhieu);
            RETURN -4;
        END

        BEGIN TRANSACTION;

        -- 1. Tính tiền phạt quá hạn nếu có (5.000 VNĐ / ngày)
        DECLARE @SoNgayQuaHan INT = 0;
        DECLARE @TienPhatQuaHan INT = 0;
        DECLARE @NgayHienTai DATETIME = GETDATE();

        IF @NgayHenTra IS NOT NULL AND @NgayHienTai > @NgayHenTra
        BEGIN
            SET @SoNgayQuaHan = DATEDIFF(DAY, @NgayHenTra, @NgayHienTai);
            IF @SoNgayQuaHan < 0 SET @SoNgayQuaHan = 0;
            SET @TienPhatQuaHan = @SoNgayQuaHan * 5000;
        END

        -- 2. Tính tổng tiền đền bù = Phạt quá hạn + Giá niêm yết sách
        DECLARE @TongTienDenBu INT = @TienPhatQuaHan + @GiaTienSach;

        -- 3. Cập nhật phiếu mượn sang 4 (Báo mất / Chờ đền bù)
        UPDATE PhieuMuon
        SET TrangThaiPhieu = 4,
            TienDenBu = @TongTienDenBu,
            NgayTraThucTe = @NgayHienTai
        WHERE MaPhieu = @MaPhieu;

        -- 4. Cập nhật sách sang -1 (Ngừng phục vụ / Đã mất)
        UPDATE Sach
        SET TrangThai = -1
        WHERE MaSach = @MaSach;

        -- 5. Tự động khóa tài khoản độc giả (TrangThaiTaiKhoan = 0)
        UPDATE DocGia
        SET TrangThaiTaiKhoan = 0
        WHERE MaDocGia = @MaDocGia;

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaDocGia AS MaDocGia,
            @MaSach AS MaSach,
            @GiaTienSach AS GiaTienSach,
            @SoNgayQuaHan AS SoNgayQuaHan,
            @TienPhatQuaHan AS TienPhatQuaHan,
            @TongTienDenBu AS TienDenBu,
            4 AS TrangThaiPhieu,
            N'Báo mất sách thành công! Tài khoản độc giả đã bị tạm khóa đến khi hoàn tất nộp phạt đền bù.' AS ThongBao;

        RETURN 0;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;
        DECLARE @ErrMsg NVARCHAR(4000) = ERROR_MESSAGE();
        RAISERROR(@ErrMsg, 16, 1);
        RETURN -99;
    END CATCH
END;
GO

-- 8. Stored Procedure: sp_XacNhanThanhToanDenBu (Cập nhật chuẩn TrangThaiPhieu = 2, mở khóa tài khoản)
CREATE OR ALTER PROCEDURE sp_XacNhanThanhToanDenBu
    @MaPhieu INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        DECLARE @MaDocGia INT;
        DECLARE @TrangThaiPhieu INT;
        DECLARE @TienDenBu INT;

        SELECT 
            @MaDocGia = MaDocGia,
            @TrangThaiPhieu = TrangThaiPhieu,
            @TienDenBu = TienDenBu
        FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK)
        WHERE MaPhieu = @MaPhieu;

        IF @MaDocGia IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d không tồn tại!', 16, 1, @MaPhieu);
            RETURN -1;
        END

        IF @TrangThaiPhieu <> 4
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d không ở trạng thái Báo mất / Chờ đền bù (TrangThaiPhieu = %d)!', 16, 1, @MaPhieu, @TrangThaiPhieu);
            RETURN -2;
        END

        BEGIN TRANSACTION;

        -- 1. Chuyển trạng thái phiếu sang 2 (Đã trả / Đã hoàn tất đền bù)
        UPDATE PhieuMuon
        SET TrangThaiPhieu = 2
        WHERE MaPhieu = @MaPhieu;

        -- 2. Kiểm tra xem độc giả này có còn phiếu nào khác đang bị báo mất (TrangThaiPhieu = 4) không
        IF NOT EXISTS (SELECT 1 FROM PhieuMuon WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 4)
        BEGIN
            UPDATE DocGia
            SET TrangThaiTaiKhoan = 1
            WHERE MaDocGia = @MaDocGia;
        END

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaDocGia AS MaDocGia,
            @TienDenBu AS TienDaThu,
            2 AS TrangThaiPhieu,
            N'Xác nhận thu tiền đền bù thành công! Tài khoản độc giả đã được mở khóa tự động.' AS ThongBao;

        RETURN 0;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;
        DECLARE @ErrMsg NVARCHAR(4000) = ERROR_MESSAGE();
        RAISERROR(@ErrMsg, 16, 1);
        RETURN -99;
    END CATCH
END;
GO

PRINT N'=== TOÀN BỘ CSDL VÀ CÁC STORED PROCEDURES ĐÃ ĐƯỢC CẬP NHẬT CHUẨN XÁC ===';
