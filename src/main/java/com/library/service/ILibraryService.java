package com.library.service;

import com.library.model.DocGiaDTO;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote Interface ILibraryService (Bài 9 - Java RMI).
 * Định nghĩa tất cả các phương thức từ xa cho cả Độc giả (Reader) và Thủ thư (Librarian).
 */
public interface ILibraryService extends Remote {

    // --- CÁC PHƯƠNG THỨC TRA CỨU SÁCH ---
    List<SachDTO> getAllBooks() throws RemoteException;
    List<SachDTO> searchBooks(String keyword) throws RemoteException;
    SachDTO getBookById(int maSach) throws RemoteException;

    // --- CÁC PHƯƠNG THỨC MƯỢN / TRẢ SÁCH (CÓ KHÓA ĐỒNG THỜI UPDLOCK) ---
    int muonSach(int maDocGia, int maSach, int soNgayMuon) throws RemoteException;
    boolean xacNhanGiaoSach(int maPhieu) throws RemoteException;
    boolean xacNhanGiaoSach(int maPhieu, int soNgayMuon) throws RemoteException;
    int huyPhieuChoQuaHan() throws RemoteException;
    boolean yeuCauTraSach(int maPhieu) throws RemoteException;
    boolean traSach(int maPhieu) throws RemoteException;
    boolean traSach(int maPhieu, int tienPhuThu) throws RemoteException;
    boolean traSach(int maPhieu, int tienPhuThu, String tinhTrangSach) throws RemoteException;
    boolean baoMatSach(int maPhieu) throws RemoteException;
    boolean xacNhanDenBu(int maPhieu) throws RemoteException;
    List<PhieuMuonDTO> getAllPhieuMuon() throws RemoteException;
    List<PhieuMuonDTO> getPhieuMuonByDocGia(int maDocGia) throws RemoteException;

    // --- CÁC PHƯƠNG THỨC NGƯỜI DÙNG & XÁC THỰC ---
    DocGiaDTO login(String email, String matKhau) throws RemoteException;
    List<DocGiaDTO> getAllDocGia() throws RemoteException;
    int themDocGia(DocGiaDTO docGia) throws RemoteException;
    boolean moKhoaTaiKhoan(int maDocGia) throws RemoteException;
    DocGiaDTO kiemTraTrangThaiDocGia(int maDocGia) throws RemoteException;

    // --- CÁC PHƯƠNG THỨC QUẢN LÝ DÀNH CHO THỦ THƯ (LIBRARIAN CRUD) ---
    int themSach(SachDTO sach) throws RemoteException;
    boolean capNhatSach(SachDTO sach) throws RemoteException;
    boolean boSungSachMoi(int maSach, int giaTien) throws RemoteException;
    boolean capNhatTrangThaiSach(int maSach, int trangThai) throws RemoteException;
    boolean xoaSach(int maSach) throws RemoteException;
    Map<String, Object> layThongKe() throws RemoteException;

    // --- CÁC PHƯƠNG THỨC PHẢN HỒI & BÁO CÁO TÌNH TRẠNG SÁCH ---
    boolean guiPhanHoi(int maDocGia, int maSach, String tieuDe, String noiDung) throws RemoteException;
    List<PhanHoiDTO> getAllPhanHoi() throws RemoteException;
    int demPhanHoiChuaXuLy() throws RemoteException;
    boolean xuLyPhanHoi(int maPhanHoi, String ghiChu) throws RemoteException;

    // --- CÁC PHƯƠNG THỨC MỤC SÁCH YÊU THÍCH CỦA ĐỘC GIẢ ---
    boolean toggleYeuThich(int maDocGia, int maSach) throws RemoteException;
    boolean isYeuThich(int maDocGia, int maSach) throws RemoteException;
    List<SachDTO> getDanhSachYeuThich(int maDocGia) throws RemoteException;
}
