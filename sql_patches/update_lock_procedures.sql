USE ThuVienDB;
GO

-- 1. Cập nhật Stored Procedure: sp_BaoMatVaDenSach
-- Khi báo mất sách: Tự động khóa tài khoản với lý do chuẩn: 'TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH'
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
            TienPhat = @TienPhatQuaHan,
            TienDenBu = @TongTienDenBu,
            NgayTraThucTe = @NgayHienTai
        WHERE MaPhieu = @MaPhieu;

        -- 4. Cập nhật sách sang -1 (Ngừng phục vụ / Đã mất)
        UPDATE Sach
        SET TrangThai = -1
        WHERE MaSach = @MaSach;

        -- 5. Tự động khóa tài khoản độc giả (TrangThaiTaiKhoan = 0) với lý do chính xác
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

-- 2. Cập nhật Stored Procedure: sp_KiemTraVaCapNhatKhoaTaiKhoan
-- Phân biệt rõ ràng giữa MẤT SÁCH và QUÁ HẠN > 10 NGÀY
CREATE OR ALTER PROCEDURE sp_KiemTraVaCapNhatKhoaTaiKhoan
    @MaDocGia INT = NULL,
    @SoDocGiaBiKhoa INT = 0 OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    -- Bước 1: Ưu tiên khóa tài khoản đối với độc giả có sách bị báo mất (TrangThaiPhieu = 4)
    UPDATE dg
    SET dg.TrangThaiTaiKhoan = 0,
        dg.LyDoKhoa = N'TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH'
    FROM DocGia dg
    WHERE (@MaDocGia IS NULL OR dg.MaDocGia = @MaDocGia)
      AND EXISTS (
          SELECT 1
          FROM PhieuMuon pm
          WHERE pm.MaDocGia = dg.MaDocGia
            AND pm.TrangThaiPhieu = 4
      );

    -- Bước 2: Khóa tài khoản đối với độc giả có sách mượn quá hạn > 10 ngày (chỉ khi không có sách bị báo mất)
    UPDATE dg
    SET dg.TrangThaiTaiKhoan = 0,
        dg.LyDoKhoa = N'TÀI KHOẢN ĐANG BỊ KHOÁ DO CÓ SÁCH QUÁ HẠN > 10 NGÀY'
    FROM DocGia dg
    WHERE (@MaDocGia IS NULL OR dg.MaDocGia = @MaDocGia)
      AND NOT EXISTS (
          SELECT 1
          FROM PhieuMuon pm4
          WHERE pm4.MaDocGia = dg.MaDocGia
            AND pm4.TrangThaiPhieu = 4
      )
      AND EXISTS (
          SELECT 1
          FROM PhieuMuon pm
          WHERE pm.MaDocGia = dg.MaDocGia
            AND pm.TrangThaiPhieu IN (0, 1)
            AND pm.NgayHenTra IS NOT NULL
            AND DATEDIFF(DAY, pm.NgayHenTra, GETDATE()) > 10
      );

    SET @SoDocGiaBiKhoa = @@ROWCOUNT;

    RETURN @SoDocGiaBiKhoa;
END;
GO

-- 3. Cập nhật Stored Procedure: sp_XacNhanThanhToanDenBu
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

-- 4. Cập nhật Stored Procedure: sp_MoKhoaTaiKhoan
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

    -- Kiểm tra nếu độc giả VẪN CÒN phiếu báo mất sách chưa thanh toán đền bù
    IF EXISTS (
        SELECT 1
        FROM PhieuMuon pm
        WHERE pm.MaDocGia = @MaDocGia
          AND pm.TrangThaiPhieu = 4
    )
    BEGIN
        RAISERROR(N'Không thể mở khóa: Độc giả vẫn còn phiếu mượn báo mất sách chưa hoàn tất đền bù tại quầy!', 16, 1);
        RETURN -3;
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

-- 5. Cập nhật Stored Procedure: sp_KiemTraVaMuonSach
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
        -- 0. Tự động kiểm tra và quét khóa tài khoản
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
              AND ((TrangThaiPhieu IN (0, 1) AND NgayHenTra IS NOT NULL AND DATEDIFF(DAY, NgayHenTra, GETDATE()) > 10) OR TrangThaiPhieu = 4)
        )
        BEGIN
            IF @LyDoKhoa LIKE N'%MẤT SÁCH%' OR EXISTS (SELECT 1 FROM PhieuMuon WHERE MaDocGia = @MaDocGia AND TrangThaiPhieu = 4)
            BEGIN
                RAISERROR(N'Tài khoản của bạn đã bị KHOÁ do mất sách. Vui lòng liên hệ quầy Thư viện để hoàn tất nộp phạt/đền bù và làm thủ tục mở khóa!', 16, 1);
            END
            ELSE
            BEGIN
                RAISERROR(N'Tài khoản của bạn đã bị KHOÁ do có sách mượn quá hạn trên 10 ngày. Vui lòng mang sách đến quầy Thư viện để trả sách, thanh toán phí phạt và làm thủ tục mở khóa!', 16, 1);
            END
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

-- 6. Đồng bộ ngay lập tức dữ liệu độc giả hiện có trong CSDL
EXEC sp_KiemTraVaCapNhatKhoaTaiKhoan;
GO
