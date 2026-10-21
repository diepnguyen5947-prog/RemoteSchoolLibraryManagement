package com.library.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Lớp PhieuMuonDTO đại diện cho thực thể Phiếu mượn/trả sách.
 * Bổ sung tính toán ngày quá hạn và tiền phạt tự động (5.000 VNĐ / ngày quá hạn).
 */
public class PhieuMuonDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final long DON_GIA_PHAT_MOI_NGAY = 5000L; // 5.000 VNĐ / ngày quá hạn

    private int maPhieu;
    private int maDocGia;
    private int maSach;
    private Date ngayMuon;
    private Date ngayHenTra;
    private Date ngayTraThucTe;
    private Date ngayHetHanGiu; // Hạn chót đến quầy nhận sách (24 giờ sau khi đăng ký online)
    private int trangThaiPhieu; // 0: Chờ lấy sách, 1: Đang mượn, 2: Đã trả sách, 3: Đã hủy (Quá hạn 24h), 4: Báo mất / Chờ đền bù

    private String tenDocGia;
    private String tenSach;
    private int soNgayQuaHan;
    private long tienPhat;
    private int tienDenBu; // Tổng tiền bồi thường (Tiền phạt quá hạn + Giá sách)
    private int giaTienSach = 50000; // Giá niêm yết của cuốn sách
    private int soNgayMuon = 14; // Thời hạn số ngày mượn do độc giả chọn
    private String tinhTrangSachKhiTra = "Bình thường (Nguyên vẹn)"; // Tình trạng sách khi thủ thư nhận bàn giao từ độc giả

    public PhieuMuonDTO() {
    }

    public PhieuMuonDTO(int maPhieu, int maDocGia, int maSach, Date ngayMuon, Date ngayHenTra,
                        Date ngayTraThucTe, int trangThaiPhieu) {
        this.maPhieu = maPhieu;
        this.maDocGia = maDocGia;
        this.maSach = maSach;
        this.ngayMuon = ngayMuon;
        this.ngayHenTra = ngayHenTra;
        this.ngayTraThucTe = ngayTraThucTe;
        this.trangThaiPhieu = trangThaiPhieu;
        tinhToanQuaHan();
    }

    public PhieuMuonDTO(int maPhieu, int maDocGia, String tenDocGia, int maSach, String tenSach,
                        Date ngayMuon, Date ngayHenTra, Date ngayTraThucTe, int trangThaiPhieu) {
        this.maPhieu = maPhieu;
        this.maDocGia = maDocGia;
        this.tenDocGia = tenDocGia;
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.ngayMuon = ngayMuon;
        this.ngayHenTra = ngayHenTra;
        this.ngayTraThucTe = ngayTraThucTe;
        this.trangThaiPhieu = trangThaiPhieu;
        tinhToanQuaHan();
    }

    public PhieuMuonDTO(int maPhieu, int maDocGia, String tenDocGia, int maSach, String tenSach,
                        Date ngayMuon, Date ngayHenTra, Date ngayTraThucTe, int trangThaiPhieu, int tienDenBu, int giaTienSach) {
        this.maPhieu = maPhieu;
        this.maDocGia = maDocGia;
        this.tenDocGia = tenDocGia;
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.ngayMuon = ngayMuon;
        this.ngayHenTra = ngayHenTra;
        this.ngayTraThucTe = ngayTraThucTe;
        this.trangThaiPhieu = trangThaiPhieu;
        this.tienDenBu = tienDenBu;
        this.giaTienSach = giaTienSach > 0 ? giaTienSach : 50000;
        tinhToanQuaHan();
    }

    public PhieuMuonDTO(int maPhieu, int maDocGia, String tenDocGia, int maSach, String tenSach,
                        Date ngayMuon, Date ngayHenTra, Date ngayTraThucTe, int trangThaiPhieu, int tienDenBu, int giaTienSach, Date ngayHetHanGiu) {
        this.maPhieu = maPhieu;
        this.maDocGia = maDocGia;
        this.tenDocGia = tenDocGia;
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.ngayMuon = ngayMuon;
        this.ngayHenTra = ngayHenTra;
        this.ngayTraThucTe = ngayTraThucTe;
        this.trangThaiPhieu = trangThaiPhieu;
        this.tienDenBu = tienDenBu;
        this.giaTienSach = giaTienSach > 0 ? giaTienSach : 50000;
        this.ngayHetHanGiu = ngayHetHanGiu;
        tinhToanQuaHan();
    }

    public PhieuMuonDTO(int maPhieu, int maDocGia, String tenDocGia, int maSach, String tenSach,
                        Date ngayMuon, Date ngayHenTra, Date ngayTraThucTe, int trangThaiPhieu, int tienDenBu, int giaTienSach, Date ngayHetHanGiu, long tienPhat) {
        this.maPhieu = maPhieu;
        this.maDocGia = maDocGia;
        this.tenDocGia = tenDocGia;
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.ngayMuon = ngayMuon;
        this.ngayHenTra = ngayHenTra;
        this.ngayTraThucTe = ngayTraThucTe;
        this.trangThaiPhieu = trangThaiPhieu;
        this.tienDenBu = tienDenBu;
        this.giaTienSach = giaTienSach > 0 ? giaTienSach : 50000;
        this.ngayHetHanGiu = ngayHetHanGiu;
        this.tienPhat = tienPhat;
        tinhToanQuaHan();
        if (tienPhat > 0 && this.tienPhat == 0) {
            this.tienPhat = tienPhat;
        }
    }

    /**
     * Tự động tính toán số ngày quá hạn và số tiền phạt tương ứng.
     * Khi ở trạng thái Chờ lấy sách (0) hoặc Đã hủy (3), không tính quá hạn mượn.
     * Khi đã báo mất (4), tiền phạt theo ngày đã dừng chốt tại thời điểm báo mất.
     */
    public void tinhToanQuaHan() {
        this.soNgayQuaHan = 0;
        if (this.tienPhat == 0L) {
            this.tienPhat = 0L;
        }

        // Nếu đang Chờ lấy sách (0) hoặc Đã hủy (3), không áp dụng phạt quá hạn trả sách
        if (trangThaiPhieu == 0 || trangThaiPhieu == 3) return;

        if (ngayHenTra == null) return;

        // Nếu đã trả (2) hoặc đã báo mất/đền bù (4), so sánh tại mốc ngày trả/báo mất thực tế
        long mocSoSanh = ((trangThaiPhieu == 2 || trangThaiPhieu == 4 || trangThaiPhieu == 5) && ngayTraThucTe != null) 
                         ? ngayTraThucTe.getTime() 
                         : System.currentTimeMillis();

        if (mocSoSanh > ngayHenTra.getTime()) {
            long diffMillis = mocSoSanh - ngayHenTra.getTime();
            long days = diffMillis / (1000L * 60 * 60 * 24);
            if (days > 0) {
                this.soNgayQuaHan = (int) days;
                if (this.tienPhat == 0L || trangThaiPhieu == 1) {
                    this.tienPhat = this.soNgayQuaHan * DON_GIA_PHAT_MOI_NGAY;
                }
            }
        }
    }

    /**
     * Thời hạn số ngày mượn được áp dụng cho phiếu mượn
     */
    public int getSoNgayMuon() {
        if (soNgayMuon > 0) return soNgayMuon;
        if (ngayHenTra != null && ngayMuon != null) {
            long diff = ngayHenTra.getTime() - ngayMuon.getTime();
            int days = (int) Math.round(diff / (1000.0 * 60 * 60 * 24));
            if (days > 0) return days;
        }
        return 14;
    }

    public void setSoNgayMuon(int soNgayMuon) {
        this.soNgayMuon = soNgayMuon;
    }

    /**
     * Tính số ngày đã mượn thực tế từ thời điểm mượn đến hiện tại hoặc đến ngày trả
     */
    public int getSoNgayDaMuon() {
        if (ngayMuon == null) return 0;
        long mocKetThuc = (ngayTraThucTe != null) ? ngayTraThucTe.getTime() : System.currentTimeMillis();
        long diffMillis = mocKetThuc - ngayMuon.getTime();
        if (diffMillis <= 0) return 1;
        long days = diffMillis / (1000L * 60 * 60 * 24);
        return Math.max(1, (int) days);
    }

    public int getMaPhieu() {
        return maPhieu;
    }

    public void setMaPhieu(int maPhieu) {
        this.maPhieu = maPhieu;
    }

    public int getMaDocGia() {
        return maDocGia;
    }

    public void setMaDocGia(int maDocGia) {
        this.maDocGia = maDocGia;
    }

    public int getMaSach() {
        return maSach;
    }

    public void setMaSach(int maSach) {
        this.maSach = maSach;
    }

    public Date getNgayMuon() {
        return ngayMuon;
    }

    public void setNgayMuon(Date ngayMuon) {
        this.ngayMuon = ngayMuon;
        tinhToanQuaHan();
    }

    public Date getNgayHenTra() {
        return ngayHenTra;
    }

    public void setNgayHenTra(Date ngayHenTra) {
        this.ngayHenTra = ngayHenTra;
        tinhToanQuaHan();
    }

    public Date getNgayTraThucTe() {
        return ngayTraThucTe;
    }

    public void setNgayTraThucTe(Date ngayTraThucTe) {
        this.ngayTraThucTe = ngayTraThucTe;
        tinhToanQuaHan();
    }

    public int getTrangThaiPhieu() {
        return trangThaiPhieu;
    }

    public void setTrangThaiPhieu(int trangThaiPhieu) {
        this.trangThaiPhieu = trangThaiPhieu;
        tinhToanQuaHan();
    }

    public String getTenDocGia() {
        return tenDocGia;
    }

    public void setTenDocGia(String tenDocGia) {
        this.tenDocGia = tenDocGia;
    }

    public String getTenSach() {
        return tenSach;
    }

    public void setTenSach(String tenSach) {
        this.tenSach = tenSach;
    }

    public int getSoNgayQuaHan() {
        return soNgayQuaHan;
    }

    public void setSoNgayQuaHan(int soNgayQuaHan) {
        this.soNgayQuaHan = soNgayQuaHan;
    }

    public long getTienPhat() {
        return tienPhat;
    }

    public void setTienPhat(long tienPhat) {
        this.tienPhat = tienPhat;
    }

    public boolean isQuaHan() {
        return soNgayQuaHan > 0;
    }

    public int getTienDenBu() {
        return tienDenBu;
    }

    public void setTienDenBu(int tienDenBu) {
        this.tienDenBu = tienDenBu;
    }

    public int getGiaTienSach() {
        return giaTienSach > 0 ? giaTienSach : 50000;
    }

    public void setGiaTienSach(int giaTienSach) {
        this.giaTienSach = giaTienSach > 0 ? giaTienSach : 50000;
    }

    public Date getNgayHetHanGiu() {
        return ngayHetHanGiu;
    }

    public void setNgayHetHanGiu(Date ngayHetHanGiu) {
        this.ngayHetHanGiu = ngayHetHanGiu;
    }

    public boolean isChoLaySach() {
        return trangThaiPhieu == 0;
    }

    public boolean isDangMuon() {
        return trangThaiPhieu == 1;
    }

    public boolean isDaTra() {
        return trangThaiPhieu == 2;
    }

    public boolean isDaHuy() {
        return trangThaiPhieu == 3;
    }

    public boolean isBaoMat() {
        return trangThaiPhieu == 4;
    }

    public boolean isQuaHanGiu() {
        return trangThaiPhieu == 0 && ngayHetHanGiu != null && System.currentTimeMillis() > ngayHetHanGiu.getTime();
    }

    public boolean isChoDuyetTra() {
        return trangThaiPhieu == 6;
    }

    public String getTrangThaiPhieuText() {
        if (trangThaiPhieu == 0) return "Chờ lấy sách";
        if (trangThaiPhieu == 1) return "Đang mượn";
        if (trangThaiPhieu == 2) return "Đã trả sách";
        if (trangThaiPhieu == 3) return "Đã hủy (Quá hạn 24h)";
        if (trangThaiPhieu == 4) return "Báo mất / Chờ đền bù";
        if (trangThaiPhieu == 5) return "Đã đền bù hoàn tất";
        if (trangThaiPhieu == 6) return "Chờ duyệt trả";
        return "Khác";
    }

    public String getTinhTrangSachKhiTra() {
        return tinhTrangSachKhiTra != null ? tinhTrangSachKhiTra : "Bình thường (Nguyên vẹn)";
    }

    public void setTinhTrangSachKhiTra(String tinhTrangSachKhiTra) {
        this.tinhTrangSachKhiTra = tinhTrangSachKhiTra;
    }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        String strNgayMuon = (ngayMuon != null) ? sdf.format(ngayMuon) : "N/A";
        String strNgayHenTra = (ngayHenTra != null) ? sdf.format(ngayHenTra) : "N/A";
        String strNgayTra = (ngayTraThucTe != null) ? sdf.format(ngayTraThucTe) : "Chưa trả";

        return String.format("PhieuMuonDTO [Mã phiếu=%d | Độc giả=%s (#%d) | Sách=%s (#%d) | Hạn=%s | Trả TT=%s | TT=%s | Quá hạn=%d ngày | Phạt=%d VNĐ | Đền bù=%d VNĐ]",
                maPhieu,
                (tenDocGia != null ? tenDocGia : ""), maDocGia,
                (tenSach != null ? tenSach : ""), maSach,
                strNgayHenTra, strNgayTra, getTrangThaiPhieuText(), soNgayQuaHan, tienPhat, tienDenBu);
    }
}
