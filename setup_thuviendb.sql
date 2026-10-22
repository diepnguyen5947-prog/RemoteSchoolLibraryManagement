-- =================================================================================
-- ĐỒ ÁN: HỆ THỐNG QUẢN LÝ MƯỢN/TRẢ SÁCH THƯ VIỆN TRƯỜNG HỌC
-- (REMOTE SCHOOL LIBRARY MANAGEMENT SYSTEM)
-- FILE TỔNG HỢP KHỞI TẠO CSDL MỚI: ThuVienDB (FRESH SETUP ONLY)
-- HỆ QUẢN TRỊ CSDL: MICROSOFT SQL SERVER
-- =================================================================================
-- LƯU Ý QUAN TRỌNG VỀ AN TOÀN DỮ LIỆU:
-- File này dùng khi CÀI ĐẶT MỚI TỪ ĐẦU (chứa lệnh DROP DATABASE cũ nếu đã tồn tại).
-- Nếu bạn đang có CSDL chạy thực tế và muốn nâng cấp an toàn giữ nguyên dữ liệu,
-- VUI LÒNG CHẠY FILE: sql_patches/migration_v2_safe_upgrade.sql
-- =================================================================================

USE master;
GO

-- 1. TẠO CƠ SỞ DỮ LIỆU ThuVienDB
IF DB_ID('ThuVienDB') IS NOT NULL
BEGIN
    ALTER DATABASE ThuVienDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE ThuVienDB;
    PRINT N'-> Đã dọn dẹp CSDL ThuVienDB cũ để khởi tạo mới.';
END
GO

CREATE DATABASE ThuVienDB COLLATE Vietnamese_CI_AS;
GO

PRINT N'-> Đã tạo thành công cơ sở dữ liệu ThuVienDB.';
GO

USE ThuVienDB;
GO

-- =================================================================================
-- 2. TẠO CÁC BẢNG DỮ LIỆU (TABLES & CONSTRAINTS)
-- =================================================================================

-- 2.1. Bảng DocGia (Tài khoản người dùng: Độc giả & Thủ thư)
-- VaiTro: 'READER' (Độc giả), 'LIBRARIAN' (Thủ thư quản trị)
-- TrangThaiTaiKhoan: 1 - Hoạt động bình thường, 0 - Bị khóa
CREATE TABLE DocGia (
    MaDocGia INT IDENTITY(1,1) PRIMARY KEY,
    HoTen NVARCHAR(100) NOT NULL,
    Email VARCHAR(100) NOT NULL UNIQUE,
    MatKhau VARCHAR(100) NOT NULL,
    SoDienThoai VARCHAR(15) NULL,
    VaiTro VARCHAR(20) NOT NULL CONSTRAINT DF_DocGia_VaiTro DEFAULT ('READER'),
    TrangThaiTaiKhoan INT NOT NULL CONSTRAINT DF_DocGia_TrangThai DEFAULT 1,
    LyDoKhoa NVARCHAR(255) NULL
);
PRINT N'-> Đã tạo bảng DocGia.';
GO

-- 2.2. Bảng Sach (Kho sách thư viện)
-- TrangThai: 
--   0: Sẵn sàng mượn
--   1: Đang được mượn thực tế
--   2: Đang được giữ chỗ (Chờ độc giả đến quầy lấy)
--  -1: Ngừng phục vụ / Đã mất
CREATE TABLE Sach (
    MaSach INT IDENTITY(1,1) PRIMARY KEY,
    TenSach NVARCHAR(200) NOT NULL,
    TacGia NVARCHAR(100) NOT NULL,
    NamXuatBan INT NOT NULL,
    TrangThai INT NOT NULL CONSTRAINT DF_Sach_TrangThai DEFAULT 0,
    HinhAnh NVARCHAR(MAX) NULL,
    GiaTien INT NOT NULL CONSTRAINT DF_Sach_GiaTien DEFAULT 50000
);
PRINT N'-> Đã tạo bảng Sach.';
GO

-- 2.3. Bảng PhieuMuon (Phiếu mượn / trả / giữ chỗ / báo mất sách)
-- TrangThaiPhieu:
--   0: Chờ lấy sách (Giữ chỗ 24h từ xa)
--   1: Đang mượn thực tế
--   2: Đã trả sách (hoặc đã hoàn tất đền bù)
--   3: Đã hủy (do quá hạn giữ chỗ 24h hoặc độc giả hủy)
--   4: Báo mất / Chờ đền bù tại quầy
--   6: Chờ thủ thư xác nhận trả
CREATE TABLE PhieuMuon (
    MaPhieu INT IDENTITY(1,1) PRIMARY KEY,
    MaDocGia INT NOT NULL CONSTRAINT FK_PhieuMuon_DocGia FOREIGN KEY REFERENCES DocGia(MaDocGia),
    MaSach INT NOT NULL CONSTRAINT FK_PhieuMuon_Sach FOREIGN KEY REFERENCES Sach(MaSach),
    NgayMuon DATETIME NOT NULL CONSTRAINT DF_PhieuMuon_NgayMuon DEFAULT GETDATE(),
    NgayHenTra DATETIME NULL,
    NgayTraThucTe DATETIME NULL,
    TrangThaiPhieu INT NOT NULL CONSTRAINT DF_PhieuMuon_TrangThai DEFAULT 0,
    NgayHetHanGiu DATETIME NULL,
    SoNgayMuon INT NULL CONSTRAINT DF_PhieuMuon_SoNgayMuon DEFAULT 14,
    TienPhat INT NULL CONSTRAINT DF_PhieuMuon_TienPhat DEFAULT 0,
    TienDenBu INT NOT NULL CONSTRAINT DF_PhieuMuon_TienDenBu DEFAULT 0,
    TinhTrangSachKhiTra NVARCHAR(255) NULL
);
PRINT N'-> Đã tạo bảng PhieuMuon.';
GO

-- 2.3.1. Unique Filtered Index bảo vệ bất biến: Một bản sách không có quá 1 phiếu mượn active (0, 1, 4, 6)
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE UNIQUE NONCLUSTERED INDEX UQ_PhieuMuon_ActiveSach
ON PhieuMuon(MaSach)
WHERE TrangThaiPhieu IN (0, 1, 4, 6);
PRINT N'-> Đã tạo Unique Filtered Index UQ_PhieuMuon_ActiveSach.';
GO

-- 2.4. Bảng PhanHoi (Phản hồi & Báo cáo sự cố từ độc giả)
CREATE TABLE PhanHoi (
    MaPhanHoi INT IDENTITY(1,1) PRIMARY KEY,
    MaDocGia INT NULL CONSTRAINT FK_PhanHoi_DocGia FOREIGN KEY REFERENCES DocGia(MaDocGia),
    MaSach INT NULL CONSTRAINT FK_PhanHoi_Sach FOREIGN KEY REFERENCES Sach(MaSach),
    TieuDe NVARCHAR(200) NULL CONSTRAINT DF_PhanHoi_TieuDe DEFAULT (N'Báo cáo tình trạng sách'),
    NoiDung NVARCHAR(1000) NOT NULL,
    NgayGui DATETIME NULL CONSTRAINT DF_PhanHoi_NgayGui DEFAULT GETDATE(),
    TrangThai INT NULL CONSTRAINT DF_PhanHoi_TrangThai DEFAULT 0,
    GhiChuThuThu NVARCHAR(500) NULL
);
PRINT N'-> Đã tạo bảng PhanHoi.';
GO

-- 2.5. Bảng YeuThich (Danh sách sách yêu thích của độc giả)
CREATE TABLE YeuThich (
    MaYeuThich INT IDENTITY(1,1) PRIMARY KEY,
    MaDocGia INT NOT NULL CONSTRAINT FK_YeuThich_DocGia FOREIGN KEY REFERENCES DocGia(MaDocGia),
    MaSach INT NOT NULL CONSTRAINT FK_YeuThich_Sach FOREIGN KEY REFERENCES Sach(MaSach),
    NgayThem DATETIME NULL CONSTRAINT DF_YeuThich_NgayThem DEFAULT GETDATE()
);
PRINT N'-> Đã tạo bảng YeuThich.';
GO

-- =================================================================================
-- 3. STORED PROCEDURES (NGHIỆP VỤ HỆ THỐNG)
-- =================================================================================

-- 3.1. Stored Procedure: sp_KiemTraVaCapNhatKhoaTaiKhoan
-- Tự động quét và khóa tài khoản vi phạm:
--   - Ưu tiên 1: Độc giả có sách báo mất (TrangThaiPhieu = 4) -> 'TÀI KHOẢN ĐANG BỊ KHOÁ DO MẤT SÁCH'
--   - Ưu tiên 2: Độc giả có sách mượn quá hạn > 10 ngày -> 'TÀI KHOẢN ĐANG BỊ KHOÁ DO CÓ SÁCH QUÁ HẠN > 10 NGÀY'
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
PRINT N'-> Đã tạo Stored Procedure sp_KiemTraVaCapNhatKhoaTaiKhoan.';
GO

-- 3.2. Stored Procedure: sp_MoKhoaTaiKhoan
-- Mở khóa tài khoản cho độc giả sau khi đã xử lý xong vi phạm
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
PRINT N'-> Đã tạo Stored Procedure sp_MoKhoaTaiKhoan.';
GO

-- 3.3. Stored Procedure: sp_KiemTraVaMuonSach
-- Đăng ký giữ sách từ xa (Remote Hold).
-- Sử dụng Transaction kết hợp UPDLOCK + ROWLOCK + HOLDLOCK để chống tranh chấp đồng thời (Concurrency Control).
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
PRINT N'-> Đã tạo Stored Procedure sp_KiemTraVaMuonSach.';
GO

-- 3.4. Stored Procedure: sp_XacNhanGiaoSach
-- Thủ thư giao sách trực tiếp tại quầy cho độc giả đã giữ chỗ
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

            RAISERROR(N'ERR_HOLD_EXPIRED: Phiếu mượn đã quá hạn 24 giờ giữ sách và đã tự động chuyển sang Đã hủy!', 16, 1);
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
PRINT N'-> Đã tạo Stored Procedure sp_XacNhanGiaoSach.';
GO

-- 3.5. Stored Procedure: sp_XacNhanGaoSach (Tương thích tên gọi alias)
CREATE OR ALTER PROCEDURE sp_XacNhanGaoSach
    @MaPhieu INT,
    @SoNgayMuon INT = NULL
AS
BEGIN
    EXEC sp_XacNhanGiaoSach @MaPhieu = @MaPhieu, @SoNgayMuon = @SoNgayMuon;
END;
GO
PRINT N'-> Đã tạo Stored Procedure sp_XacNhanGaoSach (alias).';
GO

-- 3.6. Stored Procedure: sp_HuyPhieuChoQuaHan
-- Tự động quét và hủy các phiếu mượn giữ chỗ quá hạn 24 giờ không đến nhận tại quầy
CREATE OR ALTER PROCEDURE sp_HuyPhieuChoQuaHan
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

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
            UPDATE PhieuMuon
            SET TrangThaiPhieu = 3
            WHERE MaPhieu IN (SELECT MaPhieu FROM @DanhSachHuy);

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
PRINT N'-> Đã tạo Stored Procedure sp_HuyPhieuChoQuaHan.';
GO

-- 3.7. Stored Procedure: sp_TraSach
-- Hoàn tất trả sách, tính phạt quá hạn (5.000 VNĐ / ngày), phụ thu hư hại (nếu có),
-- và phục hồi trạng thái sách về 0 (Sẵn sàng mượn).
CREATE OR ALTER PROCEDURE sp_TraSach
    @MaPhieu INT,
    @TienPhuThu INT = 0,
    @TinhTrangSachKhiTra NVARCHAR(255) = N'Nguyên vẹn'
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
            RAISERROR(N'Thông báo: Phiếu mượn mã %d này đã được hoàn tất trả sách trước đó!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -2;
        END

        -- Kiểm tra nếu đang ở trạng thái Chờ lấy sách
        IF @TrangThaiPhieu = 0
        BEGIN
            RAISERROR(N'Thông báo: Phiếu mượn mã %d đang ở trạng thái Chờ lấy sách, chưa được giao sách tại quầy!', 16, 1, @MaPhieu);
            ROLLBACK TRANSACTION;
            RETURN -3;
        END

        IF @TrangThaiPhieu NOT IN (1, 6)
        BEGIN
            RAISERROR(N'Thông báo: Phiếu mượn mã %d không ở trạng thái đang mượn!', 16, 1, @MaPhieu);
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
PRINT N'-> Đã tạo Stored Procedure sp_TraSach.';
GO

-- 3.8. Stored Procedure: sp_BaoMatVaDenSach
-- Báo mất sách: Dừng tính phạt theo ngày, quyết toán tổng tiền đền bù = Phạt + Giá sách,
-- chuyển phiếu sang 4 (Chờ đền bù), sách sang -1 (Đã mất), tự động khóa tài khoản độc giả.
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
            NgayTraThucTe = @NgayHienTai,
            TinhTrangSachKhiTra = N'Báo mất sách'
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
PRINT N'-> Đã tạo Stored Procedure sp_BaoMatVaDenSach.';
GO

-- 3.9. Stored Procedure: sp_XacNhanThanhToanDenBu
-- Thủ thư xác nhận thu tiền đền bù sách mất tại quầy, hoàn tất phiếu và tự động mở khóa nếu không còn vi phạm
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
PRINT N'-> Đã tạo Stored Procedure sp_XacNhanThanhToanDenBu.';
GO

-- =================================================================================
-- 4. TẠO DỮ LIỆU MẪU ĐẦY ĐỦ (COMPREHENSIVE SEED DATA)
-- =================================================================================

-- 4.1. Tài khoản người dùng (DocGia)
-- Mật khẩu mặc định: 123456 (thuận tiện cho bạn bè và giảng viên test)
INSERT INTO DocGia (HoTen, Email, MatKhau, SoDienThoai, VaiTro, TrangThaiTaiKhoan, LyDoKhoa)
VALUES 
-- Tài khoản Thủ thư quản trị
(N'Thủ Thư Quản Trị', 'admin@school.edu.vn', '123456', '0988888888', 'LIBRARIAN', 1, NULL),

-- Các tài khoản Độc giả học sinh / sinh viên
(N'Nguyễn Văn An', 'nguyenvanan@school.edu.vn', '123456', '0901234567', 'READER', 1, NULL),
(N'Trần Thị Bình', 'tranthibinh@school.edu.vn', '123456', '0912345678', 'READER', 1, NULL),
(N'Lê Hoàng Cường', 'lehoangcuong@school.edu.vn', '123456', '0923456789', 'READER', 1, NULL),
(N'Phạm Thị Dung', 'phamthidung@school.edu.vn', '123456', '0934567890', 'READER', 1, NULL),
(N'Hoàng Minh Em', 'hoangminhem@school.edu.vn', '123456', '0945678901', 'READER', 1, NULL);

PRINT N'-> Đã nạp 1 tài khoản Thủ thư và 5 tài khoản Độc giả mẫu.';
GO

-- 4.2. Kho sách thư viện (Sach)
INSERT INTO Sach (TenSach, TacGia, NamXuatBan, TrangThai, GiaTien)
VALUES
(N'Lập trình C++ từ cơ bản đến nâng cao', N'GS. Phạm Văn Ất', 2020, 0, 85000),
(N'Cấu trúc dữ liệu và giải thuật', N'Đỗ Trung Kiên', 2021, 0, 75000),
(N'Nhập môn Trí tuệ Nhân tạo', N'Từ Minh Phương', 2022, 0, 120000),
(N'Lão Hạc và tuyển tập truyện ngắn Nam Cao', N'Nam Cao', 2018, 0, 45000),
(N'Số đỏ', N'Vũ Trọng Phụng', 2019, 0, 55000),
(N'Lịch sử văn minh thế giới', N'Will Durant', 2020, 0, 110000),
(N'Vũ trụ trong vỏ hạt dẻ', N'Stephen Hawking', 2021, 0, 95000),
(N'Giải tích một biến và ứng dụng', N'Trần Đức Long', 2019, 0, 65000),
(N'Cơ sở dữ liệu nâng cao', N'Nguyễn Gia Tuấn Anh', 2018, 0, 90000),
(N'Đắc nhân tâm', N'Dale Carnegie', 2021, 1, 80000),              -- Đang mượn (Phiếu 3)
(N'Ngôi nhà muôn cánh cửa', N'Tan Twan Eng', 2025, 0, 180000),
(N'Kẻ Trộm Sách', N'Markus Zusak', 2024, 2, 175000);              -- Đang giữ chỗ chờ nhận (Phiếu 4)

PRINT N'-> Đã nạp 12 cuốn sách mẫu phong phú thể loại và giá tiền.';
GO

-- 4.3. Phiếu mượn mẫu (PhieuMuon)
-- Giả lập đầy đủ các tình huống: Đã trả nguyên vẹn, Đã trả có phụ thu hỏng hóc, Đang mượn, Đang giữ chỗ
INSERT INTO PhieuMuon (MaDocGia, MaSach, NgayMuon, NgayHenTra, NgayTraThucTe, TrangThaiPhieu, NgayHetHanGiu, SoNgayMuon, TienPhat, TienDenBu, TinhTrangSachKhiTra)
VALUES
-- Phiếu 1: Độc giả 2 (Nguyễn Văn An) đã mượn và đã trả hoàn tất, sách Nguyên vẹn, không phạt
(2, 1, DATEADD(DAY, -14, GETDATE()), DATEADD(DAY, 0, GETDATE()), DATEADD(DAY, -1, GETDATE()), 2, NULL, 14, 0, 0, N'Nguyên vẹn'),

-- Phiếu 2: Độc giả 3 (Trần Thị Bình) đã trả sách, có phụ thu bồi hoàn hư hại 20.000 VNĐ
(3, 2, DATEADD(DAY, -10, GETDATE()), DATEADD(DAY, 4, GETDATE()), DATEADD(DAY, -2, GETDATE()), 2, NULL, 14, 20000, 0, N'Hỏng hóc / Hư hại'),

-- Phiếu 3: Độc giả 4 (Lê Hoàng Cường) đang mượn sách số 10 (Đắc nhân tâm)
(4, 10, DATEADD(DAY, -3, GETDATE()), DATEADD(DAY, 11, GETDATE()), NULL, 1, NULL, 14, 0, 0, NULL),

-- Phiếu 4: Độc giả 5 (Phạm Thị Dung) đăng ký giữ chỗ trực tuyến sách số 12 (Kẻ Trộm Sách), thời hạn giữ 24h
(5, 12, GETDATE(), DATEADD(DAY, 14, GETDATE()), NULL, 0, DATEADD(HOUR, 24, GETDATE()), 14, 0, 0, NULL);

PRINT N'-> Đã nạp 4 phiếu mượn mẫu minh họa đầy đủ các trạng thái và lịch sử.';
GO

-- 4.4. Phản hồi mẫu (PhanHoi)
INSERT INTO PhanHoi (MaDocGia, MaSach, TieuDe, NoiDung, NgayGui, TrangThai, GhiChuThuThu)
VALUES 
(2, 1, N'Đề xuất bổ sung thêm sách', N'Kính gửi Thư viện, em hy vọng thư viện bổ sung thêm các đầu sách chuyên sâu về Spring Boot và Microservices ạ.', DATEADD(DAY, -5, GETDATE()), 1, N'Đã ghi nhận để đề xuất ban giám hiệu mua thêm.'),
(3, 2, N'Báo cáo trang sách bị rách nhẹ', N'Trang 45 của cuốn Cấu trúc dữ liệu có vết rách nhỏ, em xin báo cáo trước khi trả ạ.', DATEADD(DAY, -2, GETDATE()), 1, N'Đã kiểm tra và xử lý phụ thu bảo quản.');

PRINT N'-> Đã nạp dữ liệu phản hồi mẫu.';
GO

-- 4.5. Sách yêu thích mẫu (YeuThich)
INSERT INTO YeuThich (MaDocGia, MaSach, NgayThem)
VALUES
(2, 3, DATEADD(DAY, -3, GETDATE())),
(2, 7, DATEADD(DAY, -2, GETDATE())),
(3, 10, DATEADD(DAY, -1, GETDATE()));

PRINT N'-> Đã nạp dữ liệu sách yêu thích mẫu.';
GO

-- 5. KÍCH HOẠT QUÉT ĐỒNG BỘ TRẠNG THÁI KHÓA BAN ĐẦU
EXEC sp_KiemTraVaCapNhatKhoaTaiKhoan;
GO

PRINT N'=================================================================================';
PRINT N'ĐÃ TẠO TOÀN DIỆN VÀ HOÀN TẤT THIẾT LẬP CƠ SỞ DỮ LIỆU ThuVienDB THÀNH CÔNG!';
PRINT N'TÀI KHOẢN ĐĂNG NHẬP SẴN CÓ:';
PRINT N'  - Thủ Thư: admin@school.edu.vn | Mật khẩu: 123456';
PRINT N'  - Độc Giả: nguyenvanan@school.edu.vn | Mật khẩu: 123456';
PRINT N'  - Độc Giả: tranthibinh@school.edu.vn | Mật khẩu: 123456';
PRINT N'=================================================================================';
GO
