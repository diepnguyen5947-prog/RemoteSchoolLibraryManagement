package com.library.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Lớp PhanHoiDTO đại diện cho các phản hồi, báo cáo tình trạng sách từ độc giả gửi tới thủ thư.
 */
public class PhanHoiDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int maPhanHoi;
    private int maDocGia;
    private String tenDocGia;
    private String emailDocGia;
    private int maSach;
    private String tenSach;
    private String tieuDe;
    private String noiDung;
    private Date ngayGui;
    private int trangThai; // 0: Chờ xử lý / Mới, 1: Đã xử lý
    private String ghiChuThuThu;

    public PhanHoiDTO() {
    }

    public PhanHoiDTO(int maPhanHoi, int maDocGia, String tenDocGia, String emailDocGia, 
                      int maSach, String tenSach, String tieuDe, String noiDung, 
                      Date ngayGui, int trangThai, String ghiChuThuThu) {
        this.maPhanHoi = maPhanHoi;
        this.maDocGia = maDocGia;
        this.tenDocGia = tenDocGia;
        this.emailDocGia = emailDocGia;
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.tieuDe = tieuDe;
        this.noiDung = noiDung;
        this.ngayGui = ngayGui;
        this.trangThai = trangThai;
        this.ghiChuThuThu = ghiChuThuThu;
    }

    public int getMaPhanHoi() {
        return maPhanHoi;
    }

    public void setMaPhanHoi(int maPhanHoi) {
        this.maPhanHoi = maPhanHoi;
    }

    public int getMaDocGia() {
        return maDocGia;
    }

    public void setMaDocGia(int maDocGia) {
        this.maDocGia = maDocGia;
    }

    public String getTenDocGia() {
        return tenDocGia;
    }

    public void setTenDocGia(String tenDocGia) {
        this.tenDocGia = tenDocGia;
    }

    public String getEmailDocGia() {
        return emailDocGia;
    }

    public void setEmailDocGia(String emailDocGia) {
        this.emailDocGia = emailDocGia;
    }

    public int getMaSach() {
        return maSach;
    }

    public void setMaSach(int maSach) {
        this.maSach = maSach;
    }

    public String getTenSach() {
        return tenSach;
    }

    public void setTenSach(String tenSach) {
        this.tenSach = tenSach;
    }

    public String getTieuDe() {
        return tieuDe;
    }

    public void setTieuDe(String tieuDe) {
        this.tieuDe = tieuDe;
    }

    public String getNoiDung() {
        return noiDung;
    }

    public void setNoiDung(String noiDung) {
        this.noiDung = noiDung;
    }

    public Date getNgayGui() {
        return ngayGui;
    }

    public void setNgayGui(Date ngayGui) {
        this.ngayGui = ngayGui;
    }

    public int getTrangThai() {
        return trangThai;
    }

    public void setTrangThai(int trangThai) {
        this.trangThai = trangThai;
    }

    public String getGhiChuThuThu() {
        return ghiChuThuThu;
    }

    public void setGhiChuThuThu(String ghiChuThuThu) {
        this.ghiChuThuThu = ghiChuThuThu;
    }

    @Override
    public String toString() {
        return "PhanHoiDTO{" +
                "maPhanHoi=" + maPhanHoi +
                ", tenDocGia='" + tenDocGia + '\'' +
                ", tenSach='" + tenSach + '\'' +
                ", noiDung='" + noiDung + '\'' +
                ", trangThai=" + trangThai +
                '}';
    }
}
