-- =================================================================================
-- BẢN MIGRATION NÂNG CẤP AN TOÀN CHO HỆ THỐNG THƯ VIỆN (MIGRATION V2)
-- ĐỒ ÁN: REMOTE SCHOOL LIBRARY MANAGEMENT - HỌC PHẦN CMP180
-- =================================================================================
-- NGUYÊN TẮC AN TOÀN:
-- 1. TUYỆT ĐỐI KHÔNG DROP DATABASE, KHÔNG TRUNCATE DỮ LIỆU HIỆN CÓ.
-- 2. KIỂM TRA TIỀN ĐIỀU KIỆN DỮ LIỆU TRƯỚC KHI TẠO RÀNG BUỘC.
-- 3. BẢO VỆ BẤT BIẾN NGHIỆP VỤ BẰNG UNIQUE FILTERED INDEX:
--    Một bản sách (MaSach) không bao giờ có quá 1 phiếu mượn chưa kết thúc (0, 1, 4, 6).
-- 4. BỌC TOÀN BỘ CÁC THAO TÁC ĐỌC-GHI CỦA STORED PROCEDURES TRONG TRANSACTION.
-- =================================================================================

USE ThuVienDB;
GO

SET NOCOUNT ON;
PRINT N'====================================================================';
PRINT N'BẮT ĐẦU CHẠY MIGRATION V2 - NÂNG CẤP AN TOÀN CSDL THUVIENDB';
PRINT N'====================================================================';
GO

-- 1. KIỂM TRA TIỀN ĐIỀU KIỆN: XÁC MINH KHÔNG CÓ BẢN SÁCH NÀO BỊ TRÙNG PHIẾU ACTIVE
PRINT N'[BƯỚC 1] Kiểm tra tính toàn vẹn dữ liệu hiện có...';

IF EXISTS (
    SELECT MaSach, COUNT(*) AS SoLuongActive
    FROM PhieuMuon
    WHERE TrangThaiPhieu IN (0, 1, 4, 6)
    GROUP BY MaSach
    HAVING COUNT(*) > 1
)
BEGIN
    RAISERROR(N'CẢNH BÁO: Phát hiện dữ liệu cũ có bản sách bị trùng phiếu active! Cần xử lý trước khi tạo Index.', 16, 1);
    RETURN;
END
ELSE
BEGIN
    PRINT N'-> Tiền điều kiện ĐẠT: Không có bản sách nào đang bị mượn trùng.';
END
GO

-- 2. TẠO UNIQUE FILTERED INDEX ĐỂ BẢO VỆ BẤT BIẾN TẠI TẦNG CƠ SỞ DỮ LIỆU
PRINT N'[BƯỚC 2] Tạo Unique Filtered Index UQ_PhieuMuon_ActiveSach...';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_PhieuMuon_ActiveSach' AND object_id = OBJECT_ID('PhieuMuon'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UQ_PhieuMuon_ActiveSach
    ON PhieuMuon(MaSach)
    WHERE TrangThaiPhieu IN (0, 1, 4, 6);
    PRINT N'-> Đã tạo thành công Unique Filtered Index UQ_PhieuMuon_ActiveSach.';
END
ELSE
BEGIN
    PRINT N'-> Index UQ_PhieuMuon_ActiveSach đã tồn tại sẵn.';
END
GO

-- 3. CẬP NHẬT STORED PROCEDURE: sp_KiemTraVaMuonSach
-- Đảm bảo Transaction bao trọn toàn bộ quá trình đọc, khóa dòng và ghi
PRINT N'[BƯỚC 3] Cập nhật Stored Procedure sp_KiemTraVaMuonSach...';
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
        -- Bắt đầu Transaction sớm để bao trọn mọi thao tác đọc có khóa
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
PRINT N'-> Đã cập nhật Stored Procedure sp_KiemTraVaMuonSach.';
GO

-- 4. CẬP NHẬT STORED PROCEDURE: sp_XacNhanGiaoSach
-- Sửa lỗi: Di chuyển BEGIN TRANSACTION lên trước SELECT có khóa để bảo vệ nguyên tử
PRINT N'[BƯỚC 4] Cập nhật Stored Procedure sp_XacNhanGiaoSach...';
GO

CREATE OR ALTER PROCEDURE sp_XacNhanGiaoSach
    @MaPhieu INT,
    @SoNgayMuon INT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

        DECLARE @MaSach INT;
        DECLARE @MaDocGia INT;
        DECLARE @TrangThaiPhieu INT;
        DECLARE @NgayHetHanGiu DATETIME;
        DECLARE @SoNgayMuonDaLuu INT;

        -- Đọc với UPDLOCK, ROWLOCK bên TRONG Transaction
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
            RAISERROR(N'ERR_LOAN_NOT_FOUND: Phiếu mượn #%d không tồn tại!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -1;
        END

        IF @TrangThaiPhieu <> 0
        BEGIN
            RAISERROR(N'ERR_INVALID_LOAN_STATE: Phiếu mượn #%d không ở trạng thái Chờ lấy sách (TrangThaiPhieu = %d)!', 16, 1, @MaPhieu, @TrangThaiPhieu);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        -- Kiểm tra nếu đã quá hạn 24 giờ giữ sách
        IF @NgayHetHanGiu IS NOT NULL AND GETDATE() > @NgayHetHanGiu
        BEGIN
            UPDATE PhieuMuon SET TrangThaiPhieu = 3 WHERE MaPhieu = @MaPhieu;
            UPDATE Sach SET TrangThai = 0 WHERE MaSach = @MaSach;
            COMMIT TRANSACTION;

            RAISERROR(N'ERR_HOLD_EXPIRED: Phiếu mượn đã quá hạn 24 giờ giữ sách và đã tự động chuyển sang Đã hủy!', 16, 1);
            RETURN -3;
        END

        -- Xác định số ngày mượn
        DECLARE @SoNgayApDung INT = @SoNgayMuonDaLuu;
        IF @SoNgayMuon IS NOT NULL AND @SoNgayMuon > 0
        BEGIN
            SET @SoNgayApDung = @SoNgayMuon;
        END

        -- Chuyển phiếu mượn sang 1 (ĐANG MƯỢN THỰC TẾ)
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
            N'Xác nhận giao sách thành công! Đã chuyển sang trạng thái Đang mượn.' AS ThongBaoKetQua;

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
PRINT N'-> Đã cập nhật Stored Procedure sp_XacNhanGiaoSach.';
GO

-- 5. CẬP NHẬT STORED PROCEDURE: sp_BaoMatVaDenSach
-- Sửa lỗi: Di chuyển BEGIN TRANSACTION lên trước SELECT có khóa
PRINT N'[BƯỚC 5] Cập nhật Stored Procedure sp_BaoMatVaDenSach...';
GO

CREATE OR ALTER PROCEDURE sp_BaoMatVaDenSach
    @MaPhieu INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

        DECLARE @MaDocGia INT;
        DECLARE @MaSach INT;
        DECLARE @NgayHenTra DATETIME;
        DECLARE @TrangThaiPhieu INT;
        DECLARE @GiaTienSach INT;

        -- Đọc với UPDLOCK, HOLDLOCK bên TRONG Transaction
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
            RAISERROR(N'ERR_LOAN_NOT_FOUND: Phiếu mượn #%d không tồn tại!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -1;
        END

        IF @TrangThaiPhieu = 2
        BEGIN
            RAISERROR(N'ERR_ALREADY_RETURNED: Phiếu mượn #%d đã được trả/hoàn tất trước đó, không thể báo mất!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        IF @TrangThaiPhieu = 3
        BEGIN
            RAISERROR(N'ERR_LOAN_CANCELLED: Phiếu mượn #%d đã bị hủy!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -3;
        END

        IF @TrangThaiPhieu = 4
        BEGIN
            RAISERROR(N'ERR_ALREADY_REPORTED_LOST: Phiếu mượn #%d đã được báo mất trước đó, đang chờ đền bù!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -4;
        END

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
            TienPhat = @TienPhatQuaHan,
            TienDenBu = @TongTienDenBu,
            NgayTraThucTe = @NgayHienTai,
            TinhTrangSachKhiTra = N'Báo mất sách'
        WHERE MaPhieu = @MaPhieu;

        -- 4. Cập nhật sách sang -1 (Ngừng phục vụ / Đã mất)
        UPDATE Sach
        SET TrangThai = -1
        WHERE MaSach = @MaSach;

        -- 5. Tự động khóa tài khoản độc giả với lý do chuẩn
        UPDATE DocGia
        SET TrangThaiTaiKhoan = 0,
            LyDoKhoa = N'TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH'
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
            N'Báo mất sách thành công! Tài khoản độc giả đã bị tạm khóa với lý do: TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH.' AS ThongBao;

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
PRINT N'-> Đã cập nhật Stored Procedure sp_BaoMatVaDenSach.';
GO

-- 6. CẬP NHẬT STORED PROCEDURE: sp_XacNhanThanhToanDenBu
-- Sửa lỗi: Di chuyển BEGIN TRANSACTION lên trước SELECT có khóa
PRINT N'[BƯỚC 6] Cập nhật Stored Procedure sp_XacNhanThanhToanDenBu...';
GO

CREATE OR ALTER PROCEDURE sp_XacNhanThanhToanDenBu
    @MaPhieu INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

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
            RAISERROR(N'ERR_LOAN_NOT_FOUND: Phiếu mượn #%d không tồn tại!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -1;
        END

        IF @TrangThaiPhieu <> 4
        BEGIN
            RAISERROR(N'ERR_NOT_IN_LOST_STATE: Phiếu mượn #%d không ở trạng thái Báo mất / Chờ đền bù (TrangThaiPhieu = %d)!', 16, 1, @MaPhieu, @TrangThaiPhieu);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        -- 1. Chuyển trạng thái phiếu sang 2 (Đã hoàn tất đền bù)
        UPDATE PhieuMuon
        SET TrangThaiPhieu = 2,
            TinhTrangSachKhiTra = N'Báo mất sách'
        WHERE MaPhieu = @MaPhieu;

        -- 2. Kiểm tra xem độc giả này có còn phiếu nào khác đang bị báo mất không
        IF NOT EXISTS (SELECT 1 FROM PhieuMuon WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 4)
        BEGIN
            -- Nếu không còn sách mất, kiểm tra xem có sách quá hạn > 10 ngày không
            IF EXISTS (
                SELECT 1
                FROM PhieuMuon pm
                WHERE pm.MaDocGia = @MaDocGia
                  AND pm.TrangThaiPhieu IN (0, 1)
                  AND pm.NgayHenTra IS NOT NULL
                  AND DATEDIFF(DAY, pm.NgayHenTra, GETDATE()) > 10
            )
            BEGIN
                UPDATE DocGia
                SET TrangThaiTaiKhoan = 0,
                    LyDoKhoa = N'TÀI KHOẢN ĐANG BỊ KHOÁ DO CÓ SÁCH QUÁ HẠN > 10 NGÀY'
                WHERE MaDocGia = @MaDocGia;
            END
            ELSE
            BEGIN
                UPDATE DocGia
                SET TrangThaiTaiKhoan = 1,
                    LyDoKhoa = NULL
                WHERE MaDocGia = @MaDocGia;
            END
        END

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaDocGia AS MaDocGia,
            @TienDenBu AS TienDaThu,
            2 AS TrangThaiPhieu,
            N'Xác nhận thu tiền đền bù thành công!' AS ThongBao;

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
PRINT N'-> Đã cập nhật Stored Procedure sp_XacNhanThanhToanDenBu.';
GO

-- 7. CẬP NHẬT STORED PROCEDURE: sp_TraSach
PRINT N'[BƯỚC 7] Cập nhật Stored Procedure sp_TraSach...';
GO

CREATE OR ALTER PROCEDURE sp_TraSach
    @MaPhieu INT,
    @TienPhuThu INT = 0,
    @TinhTrangSachKhiTra NVARCHAR(255) = N'Nguyên vẹn'
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM PhieuMuon WHERE MaPhieu = @MaPhieu)
        BEGIN
            RAISERROR(N'ERR_LOAN_NOT_FOUND: Phiếu mượn mã %d không tồn tại!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -1;
        END

        DECLARE @MaSach INT;
        DECLARE @MaDocGia INT;
        DECLARE @TrangThaiPhieu INT;
        DECLARE @NgayHenTra DATETIME;
        DECLARE @NgayMuon DATETIME;

        SELECT 
            @MaSach = MaSach,
            @MaDocGia = MaDocGia,
            @TrangThaiPhieu = TrangThaiPhieu,
            @NgayHenTra = NgayHenTra,
            @NgayMuon = NgayMuon
        FROM PhieuMuon WITH (UPDLOCK, ROWLOCK)
        WHERE MaPhieu = @MaPhieu;

        -- Kiểm tra nếu đã trả sách trước đó
        IF @TrangThaiPhieu = 2
        BEGIN
            RAISERROR(N'ERR_ALREADY_RETURNED: Phiếu mượn mã %d này đã được hoàn tất trả sách trước đó!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        -- Kiểm tra nếu đang ở trạng thái Chờ lấy sách (chưa được giao sách)
        IF @TrangThaiPhieu = 0
        BEGIN
            RAISERROR(N'ERR_HOLD_NOT_PICKED_UP: Phiếu mượn mã %d đang ở trạng thái Chờ lấy sách, chưa được giao sách tại quầy!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -3;
        END

        IF @TrangThaiPhieu NOT IN (1, 6)
        BEGIN
            RAISERROR(N'ERR_INVALID_LOAN_STATE: Phiếu mượn mã %d không ở trạng thái đang mượn!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -4;
        END

        DECLARE @NgayTraHienTai DATETIME = GETDATE();
        DECLARE @SoNgayQuaHan INT = 0;
        DECLARE @TienPhatQuaHan INT = 0;

        -- Tính số ngày quá hạn và tiền phạt quá hạn (5.000 VNĐ / ngày)
        IF @NgayHenTra IS NOT NULL AND @NgayTraHienTai > @NgayHenTra
        BEGIN
            SET @SoNgayQuaHan = DATEDIFF(DAY, @NgayHenTra, @NgayTraHienTai);
            IF @SoNgayQuaHan < 0 SET @SoNgayQuaHan = 0;
            SET @TienPhatQuaHan = @SoNgayQuaHan * 5000;
        END

        -- Tổng tiền phạt = Phạt quá hạn + Tiền phụ thu (hỏng hóc nếu có)
        DECLARE @TongTienPhat INT = @TienPhatQuaHan + ISNULL(@TienPhuThu, 0);

        -- Cập nhật Phiếu mượn sang 2 (Đã trả)
        UPDATE PhieuMuon
        SET TrangThaiPhieu = 2,
            NgayTraThucTe = @NgayTraHienTai,
            TienPhat = @TongTienPhat,
            TinhTrangSachKhiTra = ISNULL(@TinhTrangSachKhiTra, N'Nguyên vẹn')
        WHERE MaPhieu = @MaPhieu;

        -- Phục hồi trạng thái sách về 0 (Sẵn sàng mượn)
        UPDATE Sach
        SET TrangThai = 0
        WHERE MaSach = @MaSach;

        COMMIT TRANSACTION;

        SELECT 
            @MaPhieu AS MaPhieu,
            @MaSach AS MaSach,
            @MaDocGia AS MaDocGia,
            @NgayTraHienTai AS NgayTraThucTe,
            @SoNgayQuaHan AS SoNgayQuaHan,
            @TongTienPhat AS TienPhat,
            2 AS TrangThaiPhieu,
            N'Hoàn tất trả sách và quyết toán thành công! Sách đã được phục hồi về Sẵn sàng mượn.' AS ThongBaoKetQua;

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
PRINT N'-> Đã cập nhật Stored Procedure sp_TraSach.';
GO

PRINT N'====================================================================';
PRINT N'MIGRATION V2 HOÀN TẤT THÀNH CÔNG VỚI DỮ LIỆU ĐƯỢC BẢO TOÀN 100%!';
PRINT N'====================================================================';
GO
