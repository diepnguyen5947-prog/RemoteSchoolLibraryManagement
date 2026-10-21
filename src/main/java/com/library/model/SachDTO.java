package com.library.model;

import java.io.Serializable;

/**
 * Lớp SachDTO (Data Transfer Object) đại diện cho thực thể Sách trong thư viện.
 * Đã bổ sung trường hinhAnh để lưu trữ ảnh bìa sách tải lên hoặc URL ảnh.
 */
public class SachDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int maSach;
    private String tenSach;
    private String tacGia;
    private int namXuatBan;
    private int trangThai; // 0: Sẵn sàng mượn, 1: Đang mượn, -2: Đã mất hẳn
    private String hinhAnh; // Đường dẫn ảnh bìa hoặc URL ảnh
    private int giaTien = 50000; // Giá niêm yết của cuốn sách (VNĐ)

    public SachDTO() {
    }

    public SachDTO(int maSach, String tenSach, String tacGia, int namXuatBan, int trangThai) {
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.namXuatBan = namXuatBan;
        this.trangThai = trangThai;
        this.hinhAnh = "";
        this.giaTien = 50000;
    }

    public SachDTO(int maSach, String tenSach, String tacGia, int namXuatBan, int trangThai, String hinhAnh) {
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.namXuatBan = namXuatBan;
        this.trangThai = trangThai;
        this.hinhAnh = hinhAnh;
        this.giaTien = 50000;
    }

    public SachDTO(int maSach, String tenSach, String tacGia, int namXuatBan, int trangThai, String hinhAnh, int giaTien) {
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.namXuatBan = namXuatBan;
        this.trangThai = trangThai;
        this.hinhAnh = hinhAnh;
        this.giaTien = giaTien > 0 ? giaTien : 50000;
    }

    public SachDTO(String tenSach, String tacGia, int namXuatBan, int trangThai, String hinhAnh) {
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.namXuatBan = namXuatBan;
        this.trangThai = trangThai;
        this.hinhAnh = hinhAnh;
        this.giaTien = 50000;
    }

    public SachDTO(String tenSach, String tacGia, int namXuatBan, int trangThai, String hinhAnh, int giaTien) {
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.namXuatBan = namXuatBan;
        this.trangThai = trangThai;
        this.hinhAnh = hinhAnh;
        this.giaTien = giaTien > 0 ? giaTien : 50000;
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

    public String getTacGia() {
        return tacGia;
    }

    public void setTacGia(String tacGia) {
        this.tacGia = tacGia;
    }

    public int getNamXuatBan() {
        return namXuatBan;
    }

    public void setNamXuatBan(int namXuatBan) {
        this.namXuatBan = namXuatBan;
    }

    public int getTrangThai() {
        return trangThai;
    }

    public void setTrangThai(int trangThai) {
        this.trangThai = trangThai;
    }

    public String getHinhAnh() {
        if (hinhAnh == null || hinhAnh.trim().isEmpty()) {
            return "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&q=80"; // Default cover
        }
        return hinhAnh;
    }

    public void setHinhAnh(String hinhAnh) {
        this.hinhAnh = hinhAnh;
    }

    public int getGiaTien() {
        return giaTien > 0 ? giaTien : 50000;
    }

    public void setGiaTien(int giaTien) {
        this.giaTien = giaTien > 0 ? giaTien : 50000;
    }

    public String getTrangThaiText() {
        if (trangThai == -1 || trangThai == -2) return "Ngừng phục vụ / Đã mất";
        if (trangThai == 1) return "Đang cho mượn";
        if (trangThai == 2) return "Đang giữ chỗ (Chờ lấy)";
        return "Sẵn sàng mượn";
    }

    public boolean isSanSang() {
        return trangThai == 0;
    }

    public boolean isDangMuon() {
        return trangThai == 1;
    }

    public boolean isDangGiuCho() {
        return trangThai == 2;
    }

    public boolean isNgungPhucVu() {
        return trangThai < 0;
    }

    @Override
    public String toString() {
        return String.format("SachDTO [Mã=%d | Tên='%s' | Tác giả='%s' | Năm XB=%d | Giá=%d VNĐ | Trạng thái=%s | Ảnh='%s']",
                maSach, tenSach, tacGia, namXuatBan, getGiaTien(), getTrangThaiText(), getHinhAnh());
    }
}
