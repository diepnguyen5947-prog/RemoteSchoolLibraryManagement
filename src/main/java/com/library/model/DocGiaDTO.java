package com.library.model;

import java.io.Serializable;

/**
 * Lớp DocGiaDTO (Data Transfer Object) đại diện cho thực thể Độc giả & Thủ thư.
 * Tuân thủ chuẩn Serialization (Bài 6) phục vụ truyền tải dữ liệu qua Java RMI (Bài 9).
 */
public class DocGiaDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int maDocGia;
    private String hoTen;
    private String email;
    private String matKhau;
    private String soDienThoai;
    private String vaiTro; // "READER" hoặc "LIBRARIAN"
    private int trangThaiTaiKhoan = 1; // 1: Hoạt động, 0: Đã bị khóa do nợ phạt/mất sách/quá hạn > 10 ngày
    private String lyDoKhoa; // Lý do tài khoản bị khóa

    public DocGiaDTO() {
        this.vaiTro = "READER";
        this.trangThaiTaiKhoan = 1;
    }

    public DocGiaDTO(int maDocGia, String hoTen, String email, String matKhau, String soDienThoai) {
        this.maDocGia = maDocGia;
        this.hoTen = hoTen;
        this.email = email;
        this.matKhau = matKhau;
        this.soDienThoai = soDienThoai;
        this.vaiTro = "READER";
        this.trangThaiTaiKhoan = 1;
    }

    public DocGiaDTO(int maDocGia, String hoTen, String email, String matKhau, String soDienThoai, String vaiTro) {
        this.maDocGia = maDocGia;
        this.hoTen = hoTen;
        this.email = email;
        this.matKhau = matKhau;
        this.soDienThoai = soDienThoai;
        this.vaiTro = (vaiTro != null && !vaiTro.trim().isEmpty()) ? vaiTro : "READER";
        this.trangThaiTaiKhoan = 1;
    }

    public DocGiaDTO(int maDocGia, String hoTen, String email, String matKhau, String soDienThoai, String vaiTro, int trangThaiTaiKhoan) {
        this.maDocGia = maDocGia;
        this.hoTen = hoTen;
        this.email = email;
        this.matKhau = matKhau;
        this.soDienThoai = soDienThoai;
        this.vaiTro = (vaiTro != null && !vaiTro.trim().isEmpty()) ? vaiTro : "READER";
        this.trangThaiTaiKhoan = trangThaiTaiKhoan;
    }

    public DocGiaDTO(int maDocGia, String hoTen, String email, String matKhau, String soDienThoai, String vaiTro, int trangThaiTaiKhoan, String lyDoKhoa) {
        this.maDocGia = maDocGia;
        this.hoTen = hoTen;
        this.email = email;
        this.matKhau = matKhau;
        this.soDienThoai = soDienThoai;
        this.vaiTro = (vaiTro != null && !vaiTro.trim().isEmpty()) ? vaiTro : "READER";
        this.trangThaiTaiKhoan = trangThaiTaiKhoan;
        this.lyDoKhoa = lyDoKhoa;
    }

    public DocGiaDTO(String hoTen, String email, String matKhau, String soDienThoai, String vaiTro) {
        this.hoTen = hoTen;
        this.email = email;
        this.matKhau = matKhau;
        this.soDienThoai = soDienThoai;
        this.vaiTro = (vaiTro != null && !vaiTro.trim().isEmpty()) ? vaiTro : "READER";
        this.trangThaiTaiKhoan = 1;
    }

    public int getMaDocGia() {
        return maDocGia;
    }

    public void setMaDocGia(int maDocGia) {
        this.maDocGia = maDocGia;
    }

    public String getHoTen() {
        return hoTen;
    }

    public void setHoTen(String hoTen) {
        this.hoTen = hoTen;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMatKhau() {
        return matKhau;
    }

    public void setMatKhau(String matKhau) {
        this.matKhau = matKhau;
    }

    public String getSoDienThoai() {
        return soDienThoai;
    }

    public void setSoDienThoai(String soDienThoai) {
        this.soDienThoai = soDienThoai;
    }

    public String getVaiTro() {
        return (vaiTro != null) ? vaiTro : "READER";
    }

    public void setVaiTro(String vaiTro) {
        this.vaiTro = vaiTro;
    }

    public boolean isLibrarian() {
        return "LIBRARIAN".equalsIgnoreCase(this.vaiTro);
    }

    public int getTrangThaiTaiKhoan() {
        return trangThaiTaiKhoan;
    }

    public void setTrangThaiTaiKhoan(int trangThaiTaiKhoan) {
        this.trangThaiTaiKhoan = trangThaiTaiKhoan;
    }

    public String getLyDoKhoa() {
        return lyDoKhoa != null ? lyDoKhoa : "";
    }

    public void setLyDoKhoa(String lyDoKhoa) {
        this.lyDoKhoa = lyDoKhoa;
    }

    public boolean isLocked() {
        return trangThaiTaiKhoan == 0;
    }

    /**
     * Tạo bản sao an toàn không chứa mật khẩu để truyền ra Client hoặc phản hồi API.
     * Đảm bảo mật khẩu thuần và chuỗi băm không bao giờ bị lộ qua mạng (RMI / JSON).
     */
    public DocGiaDTO getSafeCopy() {
        DocGiaDTO copy = new DocGiaDTO(this.maDocGia, this.hoTen, this.email, null, this.soDienThoai, this.vaiTro, this.trangThaiTaiKhoan, this.lyDoKhoa);
        return copy;
    }

    @Override
    public String toString() {
        return String.format("DocGiaDTO [Mã=%d | Họ tên='%s' | Email='%s' | SĐT='%s' | Vai trò=%s | Trạng thái TK=%s | Lý do='%s']",
                maDocGia, hoTen, email, soDienThoai, getVaiTro(), isLocked() ? "Bị khóa" : "Hoạt động", getLyDoKhoa());
    }
}
