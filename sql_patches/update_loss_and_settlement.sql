USE ThuVienDB;
GO

-- 1. Bổ sung cột GiaTien vào bảng Sach (nếu chưa có)
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Sach' AND COLUMN_NAME = 'GiaTien')
BEGIN
    ALTER TABLE Sach ADD GiaTien INT NOT NULL CONSTRAINT DF_Sach_GiaTien DEFAULT 50000;
    PRINT N'-> Đã thêm cột GiaTien vào bảng Sach.';
END
GO

-- Cập nhật giá tiền niêm yết mẫu cho các cuốn sách hiện có
UPDATE Sach SET GiaTien = 85000 WHERE MaSach = 1;  -- Lập trình C++
UPDATE Sach SET GiaTien = 75000 WHERE MaSach = 2;  -- Cấu trúc dữ liệu
UPDATE Sach SET GiaTien = 120000 WHERE MaSach = 3; -- AI
UPDATE Sach SET GiaTien = 45000 WHERE MaSach = 4;  -- Lão Hạc
UPDATE Sach SET GiaTien = 55000 WHERE MaSach = 5;  -- Số đỏ
UPDATE Sach SET GiaTien = 110000 WHERE MaSach = 6; -- Lịch sử văn minh
UPDATE Sach SET GiaTien = 95000 WHERE MaSach = 7;  -- Vũ trụ trong vỏ hạt dẻ
UPDATE Sach SET GiaTien = 65000 WHERE MaSach = 8;  -- Giải tích một biến
UPDATE Sach SET GiaTien = 90000 WHERE MaSach = 9;  -- SQL Server
UPDATE Sach SET GiaTien = 80000 WHERE MaSach = 10; -- Đắc nhân tâm
GO

-- 2. Bổ sung cột TienDenBu vào bảng PhieuMuon (nếu chưa có)
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PhieuMuon' AND COLUMN_NAME = 'TienDenBu')
BEGIN
    ALTER TABLE PhieuMuon ADD TienDenBu INT NOT NULL CONSTRAINT DF_PhieuMuon_TienDenBu DEFAULT 0;
    PRINT N'-> Đã thêm cột TienDenBu vào bảng PhieuMuon.';
END
GO

-- 3. Bổ sung cột TrangThaiTaiKhoan vào bảng DocGia (nếu chưa có)
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'DocGia' AND COLUMN_NAME = 'TrangThaiTaiKhoan')
BEGIN
    ALTER TABLE DocGia ADD TrangThaiTaiKhoan INT NOT NULL CONSTRAINT DF_DocGia_TrangThaiTaiKhoan DEFAULT 1;
    PRINT N'-> Đã thêm cột TrangThaiTaiKhoan vào bảng DocGia.';
END
GO

-- 4. Stored Procedure: sp_BaoMatVaDenSach
-- Chốt tiền phạt quá hạn, tính tiền đền bù = Phạt + Giá sách, chuyển phiếu sang 2, sách sang -2, khóa tài khoản độc giả
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

        -- Kiểm tra sự tồn tại của phiếu mượn
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

        IF @TrangThaiPhieu = 1
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d đã được trả trước đó, không thể báo mất!', 16, 1, @MaPhieu);
            RETURN -2;
        END

        IF @TrangThaiPhieu = 2
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d đã được báo mất trước đó, đang chờ đền bù!', 16, 1, @MaPhieu);
            RETURN -3;
        END

        IF @TrangThaiPhieu = 3
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d đã đền bù hoàn tất!', 16, 1, @MaPhieu);
            RETURN -4;
        END

        BEGIN TRANSACTION;

        -- 1. Tính tiền phạt quá hạn tại thời điểm báo mất (5.000 VNĐ / ngày)
        DECLARE @SoNgayQuaHan INT = 0;
        DECLARE @TienPhatQuaHan INT = 0;
        DECLARE @NgayHienTai DATETIME = GETDATE();

        IF @NgayHienTai > @NgayHenTra
        BEGIN
            SET @SoNgayQuaHan = DATEDIFF(DAY, @NgayHenTra, @NgayHienTai);
            IF @SoNgayQuaHan < 0 SET @SoNgayQuaHan = 0;
            SET @TienPhatQuaHan = @SoNgayQuaHan * 5000;
        END

        -- 2. Tính tổng phí đền bù = Tiền phạt quá hạn + Giá niêm yết của sách
        DECLARE @TongTienDenBu INT = @TienPhatQuaHan + @GiaTienSach;

        -- 3. Cập nhật phiếu mượn sang trạng thái 2 (Báo mất / Chờ đền bù)
        UPDATE PhieuMuon
        SET TrangThaiPhieu = 2,
            TienDenBu = @TongTienDenBu,
            NgayTraThucTe = @NgayHienTai
        WHERE MaPhieu = @MaPhieu;

        -- 4. Cập nhật trạng thái sách sang -2 (Đã mất hẳn)
        UPDATE Sach
        SET TrangThai = -2
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

-- 5. Stored Procedure: sp_XacNhanThanhToanDenBu
-- Thủ thư xác nhận đã thu đủ tiền đền bù: chuyển phiếu sang 3, kiểm tra mở lại tài khoản độc giả
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

        IF @TrangThaiPhieu <> 2
        BEGIN
            RAISERROR(N'Lỗi: Phiếu mượn #%d không ở trạng thái Chờ đền bù (TrangThaiPhieu = %d)!', 16, 1, @MaPhieu, @TrangThaiPhieu);
            RETURN -2;
        END

        BEGIN TRANSACTION;

        -- 1. Chuyển trạng thái phiếu sang 3 (Đã đền bù hoàn tất)
        UPDATE PhieuMuon
        SET TrangThaiPhieu = 3
        WHERE MaPhieu = @MaPhieu;

        -- 2. Kiểm tra xem độc giả này có còn phiếu nào khác đang bị báo mất / nợ đền bù (TrangThaiPhieu = 2) không
        IF NOT EXISTS (SELECT 1 FROM PhieuMuon WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 2)
        BEGIN
            -- Nếu không còn phiếu nào nợ đền bù, tự động mở khóa tài khoản
            UPDATE DocGia
            SET TrangThaiTaiKhoan = 1
            WHERE MaDocGia = @MaDocGia;
        END

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaDocGia AS MaDocGia,
            @TienDenBu AS TienDaThu,
            N'Xác nhận thu tiền đền bù thành công! Tài khoản độc giả đã được kiểm tra và kích hoạt lại.' AS ThongBao;

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

-- 6. Cập nhật Stored Procedure sp_KiemTraVaMuonSach: Bổ sung kiểm tra TrangThaiTaiKhoan
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
        -- 1. Kiểm tra sự tồn tại của độc giả và trạng thái tài khoản
        DECLARE @TrangThaiTaiKhoan INT;
        SELECT @TrangThaiTaiKhoan = TrangThaiTaiKhoan FROM DocGia WHERE MaDocGia = @MaDocGia;

        IF @TrangThaiTaiKhoan IS NULL
        BEGIN
            RAISERROR(N'Lỗi: Độc giả có mã %d không tồn tại trong hệ thống!', 16, 1, @MaDocGia);
            RETURN -1;
        END

        -- Kiểm tra nếu tài khoản đang bị khóa do nợ phạt/mất sách
        IF @TrangThaiTaiKhoan = 0
        BEGIN
            RAISERROR(N'Lỗi: Tài khoản của bạn đang bị khóa do chưa hoàn tất đền bù sách mất/quá hạn! Vui lòng liên hệ Thủ thư để thanh toán.', 16, 1);
            RETURN -5;
        END

        -- Bắt đầu Transaction
        BEGIN TRANSACTION;

        -- 2. KIỂM TRA ĐỘC GIẢ CÓ ĐANG CÓ PHIẾU NỢ ĐỀN BÙ KHÔNG (TrangThaiPhieu = 2)
        IF EXISTS (SELECT 1 FROM PhieuMuon WITH (UPDLOCK, HOLDLOCK) WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 2)
        BEGIN
            RAISERROR(N'Lỗi: Bạn có phiếu mượn đang trong trạng thái chờ đền bù sách mất. Vui lòng thanh toán trước khi mượn tiếp!', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN -6;
        END

        -- 3. KIỂM TRA GIỚI HẠN MƯỢN SÁCH: Tối đa 3 cuốn sách chưa trả
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
        IF @TrangThaiSach = -2
        BEGIN
            RAISERROR(N'Lỗi: Cuốn sách mã %d đã bị mất hẳn khỏi thư viện, không thể mượn!', 16, 1, @MaSach);
            ROLLBACK TRANSACTION;
            RETURN -7;
        END

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
        INSERT INTO PhieuMuon (MaDocGia, MaSach, NgayMuon, NgayHenTra, NgayTraThucTe, TrangThaiPhieu, TienDenBu)
        VALUES (@MaDocGia, @MaSach, @NgayMuonHienTai, @NgayHenTraDuKien, NULL, 0, 0);

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

PRINT 'Cập nhật CSDL xử lý mất sách & đền bù THÀNH CÔNG!';
