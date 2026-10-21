package com.library.web.controller;

import com.library.model.DocGiaDTO;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.service.ILibraryService;
import com.library.web.config.RMIClientManager;
import com.library.web.config.SessionManager;
import com.library.web.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Controller xử lý tất cả các REST API endpoints cho Độc giả và Thủ thư.
 */
public class BookApiController implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS Headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Auth-Token");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

        String method = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();
        String query = requestURI.getRawQuery();

        try {
            ILibraryService service = RMIClientManager.getInstance().getService();

            if ("GET".equals(method)) {
                handleGetRequests(exchange, service, path, query);
            } else if ("POST".equals(method)) {
                String requestBody = readRequestBody(exchange);
                Map<String, String> params = JsonUtil.parseFormData(requestBody);
                handlePostRequests(exchange, service, path, params);
            } else {
                sendJsonResponse(exchange, 405, JsonUtil.errorResponse("Phương thức HTTP không được hỗ trợ!"));
            }

        } catch (RemoteException ex) {
            System.err.println("[BookApiController] Lỗi RMI: " + ex.getMessage());
            sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Lỗi kết nối RMI Server: " + ex.getMessage()));
        } catch (Exception ex) {
            System.err.println("[BookApiController] Lỗi hệ thống: " + ex.getMessage());
            sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Lỗi máy chủ nội bộ: " + ex.getMessage()));
        }
    }

    private void handleGetRequests(HttpExchange exchange, ILibraryService service, String path, String query) throws IOException {
        if ("/api/sach".equals(path)) {
            List<SachDTO> list = service.getAllBooks();
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách sách thành công", JsonUtil.sachListToJson(list)));
        } else if ("/api/sach/chi-tiet".equals(path) || "/api/sach/detail".equals(path)) {
            String strMaSach = extractQueryParam(query, "maSach");
            if (strMaSach == null || strMaSach.isEmpty()) {
                strMaSach = extractQueryParam(query, "id");
            }
            if (strMaSach != null && !strMaSach.isEmpty()) {
                try {
                    int maSach = Integer.parseInt(strMaSach);
                    SachDTO found = service.getBookById(maSach);
                    if (found != null) {
                        String jsonBook = JsonUtil.toJson(found);
                        // Nếu sách đang được mượn hoặc giữ chỗ, đính kèm ngày hẹn trả của phiếu đang mượn gần nhất
                        try {
                            if (found.getTrangThai() == 1 || found.getTrangThai() == 2) {
                                List<PhieuMuonDTO> loans = service.getAllPhieuMuon();
                                if (loans != null) {
                                    for (PhieuMuonDTO pm : loans) {
                                        if (pm.getMaSach() == maSach && (pm.getTrangThaiPhieu() == 1 || pm.getTrangThaiPhieu() == 0)) {
                                            if (pm.getNgayHenTra() != null) {
                                                String strNgayHenTra = JsonUtil.DATE_FORMAT.format(pm.getNgayHenTra());
                                                jsonBook = jsonBook.substring(0, jsonBook.length() - 1) +
                                                        ",\"ngayHenTra\":\"" + JsonUtil.escape(strNgayHenTra) + "\"}";
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            System.err.println("[BookApiController] Lỗi lấy thông tin phiếu mượn cho sách #" + maSach + ": " + ex.getMessage());
                        }
                        sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy thông tin sách thành công", jsonBook));
                    } else {
                        sendJsonResponse(exchange, 404, JsonUtil.errorResponse("Không tìm thấy cuốn sách với mã #" + maSach));
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã sách không hợp lệ!"));
                }
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Thiếu tham số maSach hoặc id!"));
            }
        } else if ("/api/sach/tim-kiem".equals(path)) {
            String keyword = extractQueryParam(query, "keyword");
            List<SachDTO> list = service.searchBooks(keyword);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Tìm kiếm sách thành công", JsonUtil.sachListToJson(list)));
        } else if ("/api/phieu-muon".equals(path)) {
            String strMaDocGia = extractQueryParam(query, "maDocGia");
            List<PhieuMuonDTO> list;
            if (strMaDocGia != null && !strMaDocGia.isEmpty()) {
                int maDocGia = Integer.parseInt(strMaDocGia);
                list = service.getPhieuMuonByDocGia(maDocGia);
            } else {
                list = service.getAllPhieuMuon();
            }
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách phiếu mượn thành công", JsonUtil.phieuMuonListToJson(list)));
        } else if ("/api/doc-gia".equals(path)) {
            List<DocGiaDTO> list = service.getAllDocGia();
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách người dùng thành công", JsonUtil.docGiaListToJson(list)));
        } else if ("/api/admin/thong-ke".equals(path)) {
            Map<String, Object> stats = service.layThongKe();
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy thống kê thành công", JsonUtil.mapToJson(stats)));
        } else if ("/api/auth/me".equals(path)) {
            String token = extractToken(exchange);
            DocGiaDTO user = SessionManager.getUser(token);
            if (user != null) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đang đăng nhập", JsonUtil.toJson(user)));
            } else {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Chưa đăng nhập hoặc phiên đã hết hạn!"));
            }
        } else if ("/api/admin/phan-hoi".equals(path)) {
            List<PhanHoiDTO> list = service.getAllPhanHoi();
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách phản hồi thành công", JsonUtil.phanHoiListToJson(list)));
        } else if ("/api/admin/phan-hoi/dem-chua-xu-ly".equals(path)) {
            int count = service.demPhanHoiChuaXuLy();
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đếm phản hồi chưa xử lý", "{\"chuaXuLy\":" + count + "}"));
        } else if ("/api/reader/yeu-thich".equals(path)) {
            String strMaDocGia = extractQueryParam(query, "maDocGia");
            if (strMaDocGia != null && !strMaDocGia.isEmpty()) {
                int maDocGia = Integer.parseInt(strMaDocGia);
                List<SachDTO> list = service.getDanhSachYeuThich(maDocGia);
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách yêu thích thành công", JsonUtil.sachListToJson(list)));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Thiếu mã độc giả!"));
            }
        } else if ("/api/reader/yeu-thich/check".equals(path)) {
            String strMaDocGia = extractQueryParam(query, "maDocGia");
            String strMaSach = extractQueryParam(query, "maSach");
            if (strMaDocGia != null && !strMaDocGia.isEmpty() && strMaSach != null && !strMaSach.isEmpty()) {
                int maDocGia = Integer.parseInt(strMaDocGia);
                int maSach = Integer.parseInt(strMaSach);
                boolean favorited = service.isYeuThich(maDocGia, maSach);
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Kiểm tra yêu thích", "{\"isFavorite\":" + favorited + "}"));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Thiếu thông tin độc giả hoặc sách!"));
            }
        } else if ("/api/reader/status".equals(path)) {
            String strMaDocGia = extractQueryParam(query, "maDocGia");
            if (strMaDocGia != null && !strMaDocGia.isEmpty()) {
                try {
                    int maDocGia = Integer.parseInt(strMaDocGia);
                    DocGiaDTO dg = service.kiemTraTrangThaiDocGia(maDocGia);
                    if (dg != null) {
                        sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy trạng thái tài khoản thành công", JsonUtil.toJson(dg)));
                    } else {
                        sendJsonResponse(exchange, 404, JsonUtil.errorResponse("Không tìm thấy độc giả #" + maDocGia));
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã độc giả không hợp lệ!"));
                }
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Thiếu tham số maDocGia!"));
            }
        } else {
            sendJsonResponse(exchange, 404, JsonUtil.errorResponse("Endpoint không tồn tại: " + path));
        }
    }

    private void handlePostRequests(HttpExchange exchange, ILibraryService service, String path, Map<String, String> params) throws IOException {
        // --- 1. XÁC THỰC ĐĂNG NHẬP ---
        if ("/api/auth/login".equals(path)) {
            String email = params.getOrDefault("email", "").trim();
            String matKhau = params.getOrDefault("matKhau", "").trim();

            if (email.isEmpty() || matKhau.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Email và Mật khẩu không được để trống!"));
                return;
            }

            DocGiaDTO user = service.login(email, matKhau);
            if (user != null) {
                String token = SessionManager.createSession(user);
                String data = String.format("{\"token\":\"%s\",\"user\":%s}", token, JsonUtil.toJson(user));
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đăng nhập thành công!", data));
            } else {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Email hoặc mật khẩu không chính xác!"));
            }
        }
        else if ("/api/auth/logout".equals(path)) {
            String token = extractToken(exchange);
            SessionManager.removeSession(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đăng xuất thành công!", null));
        }
        else if ("/api/auth/register".equals(path) || "/api/auth/dang-ky".equals(path) || "/api/auth/tao-tai-khoan".equals(path)) {
            String hoTen = params.getOrDefault("hoTen", "").trim();
            String email = params.getOrDefault("email", "").trim();
            String matKhau = params.getOrDefault("matKhau", "").trim();
            String nhapLaiMatKhau = params.getOrDefault("nhapLaiMatKhau", "").trim();
            String soDienThoai = params.getOrDefault("soDienThoai", "").trim();

            if (hoTen.isEmpty() || email.isEmpty() || matKhau.isEmpty() || nhapLaiMatKhau.isEmpty() || soDienThoai.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Tất cả các thông tin (Họ tên, Email, Mật khẩu, Nhập lại mật khẩu, Số điện thoại) không được để trống!"));
                return;
            }

            if (!matKhau.equals(nhapLaiMatKhau)) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mật khẩu nhập lại không khớp!"));
                return;
            }

            if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Địa chỉ Email không đúng định dạng hợp lệ!"));
                return;
            }

            try {
                DocGiaDTO docGia = new DocGiaDTO();
                docGia.setHoTen(hoTen);
                docGia.setEmail(email);
                docGia.setMatKhau(matKhau);
                docGia.setSoDienThoai(soDienThoai);
                docGia.setVaiTro("READER");
                docGia.setTrangThaiTaiKhoan(1);

                int id = service.themDocGia(docGia);
                if (id > 0) {
                    String data = String.format("{\"maDocGia\":%d,\"email\":\"%s\",\"hoTen\":\"%s\"}", id, JsonUtil.escape(email), JsonUtil.escape(hoTen));
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Tạo tài khoản độc giả thành công! Bạn có thể đăng nhập ngay bây giờ.", data));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể tạo tài khoản. Vui lòng kiểm tra lại thông tin!"));
                }
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                if (message.contains("UQ__DocGia") || message.contains("duplicate") || message.contains("UNIQUE") || message.contains("trùng")) {
                    message = "Email này đã được đăng ký trong hệ thống. Vui lòng sử dụng địa chỉ email khác!";
                }
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(message));
            }
        }

        // --- 2. NGHIỆP VỤ MƯỢN / TRẢ SÁCH ---
        else if ("/api/muon-sach".equals(path)) {
            try {
                int maDocGia = Integer.parseInt(params.getOrDefault("maDocGia", "0"));
                int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
                int soNgayMuon = Integer.parseInt(params.getOrDefault("soNgayMuon", "14"));

                if (maDocGia <= 0 || maSach <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Dữ liệu không hợp lệ: maDocGia và maSach phải > 0!"));
                    return;
                }

                int maPhieu = service.muonSach(maDocGia, maSach, soNgayMuon);
                if (maPhieu > 0) {
                    long expMillis = System.currentTimeMillis() + 24L * 3600 * 1000;
                    String strExp = JsonUtil.DATE_FORMAT.format(new java.util.Date(expMillis));
                    String data = String.format("{\"maPhieu\":%d,\"maDocGia\":%d,\"maSach\":%d,\"ngayHetHanGiu\":\"%s\"}", maPhieu, maDocGia, maSach, strExp);
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đăng ký giữ sách thành công! Vui lòng đến thư viện nhận sách trước " + strExp, data));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Sách đang được mượn bởi người khác hoặc không sẵn có!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng tham số maDocGia hoặc maSach không đúng kiểu số!"));
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                if (message.isEmpty()) {
                    message = "Sách đang được mượn bởi người khác hoặc không sẵn có!";
                }
                sendJsonResponse(exchange, 409, JsonUtil.errorResponse(message));
            }
        }
        else if ("/api/reader/yeu-cau-tra-sach".equals(path) || "/api/yeu-cau-tra-sach".equals(path)) {
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }

                boolean ok = service.yeuCauTraSach(maPhieu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đã gửi yêu cầu trả sách tới Thủ thư! Vui lòng mang sách tới quầy để Thủ thư kiểm tra và xác nhận hoàn tất.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể gửi yêu cầu trả sách. Phiếu mượn không tồn tại, đã trả hoặc đang chờ duyệt!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(message));
            }
        }
        else if ("/api/tra-sach".equals(path) || "/api/admin/tra-sach".equals(path) || "/api/admin/xac-nhan-tra-sach".equals(path)) {
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }

                int tienPhuThu = 0;
                try {
                    tienPhuThu = Integer.parseInt(params.getOrDefault("tienPhuThu", "0"));
                } catch (Exception ignored) {}

                String tinhTrangSach = params.get("tinhTrangSach");
                if (tinhTrangSach == null || tinhTrangSach.trim().isEmpty()) {
                    tinhTrangSach = params.get("tinhTrang");
                }
                if (tinhTrangSach == null || tinhTrangSach.trim().isEmpty()) {
                    tinhTrangSach = tienPhuThu > 0 ? "Hỏng hóc / Hư hại" : "Nguyên vẹn";
                }

                boolean ok = service.traSach(maPhieu, tienPhuThu, tinhTrangSach);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thủ thư đã xác nhận nhận sách và quyết toán thành công! Trạng thái sách đã được phục hồi về Sẵn sàng trong CSDL.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể hoàn tất trả sách. Phiếu mượn không tồn tại hoặc đã trả trước đó!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(message));
            }
        }
        else if ("/api/reader/bao-mat-sach".equals(path) || "/api/bao-mat-sach".equals(path)) {
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }

                boolean ok = service.baoMatSach(maPhieu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Báo mất sách thành công! Hệ thống đã chốt phí đền bù và tạm khóa tài khoản cho đến khi bạn hoàn tất nộp tiền đền bù tại thư viện.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể báo mất sách. Phiếu mượn không tồn tại hoặc đã được xử lý!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(message));
            }
        }
        else if ("/api/admin/xac-nhan-den-bu".equals(path) || "/api/xac-nhan-den-bu".equals(path)) {
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }

                boolean ok = service.xacNhanDenBu(maPhieu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thủ thư đã xác nhận thu tiền đền bù thành công! Phiếu đã được chốt và tài khoản độc giả đã được mở khóa tự động.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể xác nhận đền bù. Phiếu mượn không tồn tại hoặc không ở trạng thái Chờ đền bù!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(message));
            }
        }
        else if ("/api/admin/xac-nhan-giao-sach".equals(path) || "/api/xac-nhan-giao-sach".equals(path) || "/api/admin/xac-nhan-gao-sach".equals(path)) {
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }

                int soNgayMuon = Integer.parseInt(params.getOrDefault("soNgayMuon", "0"));
                boolean ok = service.xacNhanGiaoSach(maPhieu, soNgayMuon);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thủ thư đã xác nhận giao sách thành công! Phiếu đã chuyển sang trạng thái Đang mượn.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể giao sách. Phiếu mượn không tồn tại hoặc không ở trạng thái Chờ lấy sách!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(message));
            }
        }
        else if ("/api/admin/huy-phieu-qua-han".equals(path) || "/api/huy-phieu-qua-han".equals(path)) {
            try {
                int count = service.huyPhieuChoQuaHan();
                String msg = count > 0 
                        ? "Đã quét và hủy thành công " + count + " phiếu giữ chỗ quá hạn 24 giờ. Trạng thái sách đã được phục hồi về Sẵn sàng mượn!"
                        : "Không có phiếu giữ chỗ nào bị quá hạn 24 giờ cần hủy.";
                sendJsonResponse(exchange, 200, JsonUtil.successResponse(msg, "{\"soLuongHuy\":" + count + "}"));
            } catch (RemoteException e) {
                String message = cleanErrorMessage(e.getMessage());
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(message));
            }
        }

        // --- 3. QUẢN TRỊ THỦ THƯ (UPLOAD ẢNH BÌA & CRUD SÁCH & ĐỘC GIẢ) ---
        else if ("/api/admin/upload-cover".equals(path)) {
            String base64Data = params.getOrDefault("base64Data", params.getOrDefault("imageData", "")).trim();
            String fileName = params.getOrDefault("fileName", "cover.jpg").trim();

            if (base64Data.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Dữ liệu ảnh tải lên không được để trống!"));
                return;
            }

            try {
                // Tách phần tiền tố base64 nếu có: "data:image/png;base64,..."
                if (base64Data.contains("base64,")) {
                    base64Data = base64Data.substring(base64Data.indexOf("base64,") + 7);
                }

                // Làm sạch base64 nếu có khoảng trắng hoặc ký tự xuống dòng
                base64Data = base64Data.replace(" ", "+").replaceAll("[\\r\\n]", "");

                byte[] imageBytes = java.util.Base64.getMimeDecoder().decode(base64Data);

                // Xác định định dạng file
                String ext = ".jpg";
                if (fileName.lastIndexOf('.') > 0) {
                    ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
                }
                if (!ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".png") && !ext.equals(".webp")) {
                    ext = ".jpg";
                }

                String newFileName = "cover_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + ext;

                // Thư mục lưu ảnh tĩnh
                java.io.File uploadDir = new java.io.File("src/main/resources/static/uploads/covers");
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }
                java.io.File targetFile = new java.io.File(uploadDir, newFileName);
                java.nio.file.Files.write(targetFile.toPath(), imageBytes);

                // Đồng bộ sang target nếu có
                java.io.File targetBuildDir = new java.io.File("target/classes/static/uploads/covers");
                if (targetBuildDir.exists() || new java.io.File("target/classes/static").exists()) {
                    targetBuildDir.mkdirs();
                    java.nio.file.Files.write(new java.io.File(targetBuildDir, newFileName).toPath(), imageBytes);
                }

                String publicUrl = "/uploads/covers/" + newFileName;
                String data = String.format("{\"url\":\"%s\",\"fileName\":\"%s\"}", publicUrl, newFileName);
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Tải ảnh bìa lên thành công!", data));
            } catch (Exception e) {
                System.err.println("[upload-cover] Lỗi lưu ảnh: " + e.getMessage());
                sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Lỗi xử lý tệp ảnh: " + e.getMessage()));
            }
        }
        else if ("/api/admin/sach".equals(path)) {
            String tenSach = params.getOrDefault("tenSach", "").trim();
            String tacGia = params.getOrDefault("tacGia", "").trim();
            int namXuatBan = Integer.parseInt(params.getOrDefault("namXuatBan", "2024"));
            String hinhAnh = params.getOrDefault("hinhAnh", "").trim();
            int giaTien = 50000;
            try {
                giaTien = Integer.parseInt(params.getOrDefault("giaTien", "50000"));
            } catch (NumberFormatException ignored) {}

            if (tenSach.isEmpty() || tacGia.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Tên sách và Tác giả không được để trống!"));
                return;
            }

            SachDTO s = new SachDTO(tenSach, tacGia, namXuatBan, 0, hinhAnh, giaTien);
            int newId = service.themSach(s);
            if (newId > 0) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thêm sách mới thành công! Mã sách: #" + newId, JsonUtil.toJson(s)));
            } else {
                sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Không thể thêm sách vào CSDL!"));
            }
        }
        else if ("/api/admin/sach/update".equals(path)) {
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
            String tenSach = params.getOrDefault("tenSach", "").trim();
            String tacGia = params.getOrDefault("tacGia", "").trim();
            int namXuatBan = Integer.parseInt(params.getOrDefault("namXuatBan", "2024"));
            String hinhAnh = params.getOrDefault("hinhAnh", "").trim();
            int giaTien = 50000;
            try {
                giaTien = Integer.parseInt(params.getOrDefault("giaTien", "50000"));
            } catch (NumberFormatException ignored) {}

            if (maSach <= 0 || tenSach.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Thông tin cập nhật sách không hợp lệ!"));
                return;
            }

            SachDTO s = new SachDTO(maSach, tenSach, tacGia, namXuatBan, 0, hinhAnh, giaTien);
            boolean ok = service.capNhatSach(s);
            if (ok) {
                // Nếu thủ thư tích chọn khôi phục sách đã báo mất / bổ sung sách mới
                String strKhoiPhuc = params.getOrDefault("khoiPhuc", "false").trim();
                if ("true".equalsIgnoreCase(strKhoiPhuc) || "1".equals(strKhoiPhuc)) {
                    service.boSungSachMoi(maSach, giaTien);
                }
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Cập nhật sách thành công!", null));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không tìm thấy cuốn sách để cập nhật!"));
            }
        }
        else if ("/api/admin/sach/bo-sung".equals(path) || "/api/admin/sach/khoi-phuc".equals(path)) {
            try {
                int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
                if (maSach <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã sách không hợp lệ!"));
                    return;
                }

                int giaTien = 50000;
                try {
                    giaTien = Integer.parseInt(params.getOrDefault("giaTien", "50000"));
                } catch (NumberFormatException ignored) {}

                boolean ok = service.boSungSachMoi(maSach, giaTien);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Bổ sung sách mới thành công! Sách #" + maSach + " đã được chuyển về trạng thái 'Sẵn sàng' và hiển thị trên cổng Độc giả để mượn lại.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không tìm thấy cuốn sách để bổ sung!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng dữ liệu không hợp lệ!"));
            } catch (RemoteException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(cleanErrorMessage(e.getMessage())));
            }
        }
        else if ("/api/admin/sach/delete".equals(path)) {
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
            if (maSach <= 0) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã sách không hợp lệ!"));
                return;
            }

            try {
                boolean ok = service.xoaSach(maSach);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Xóa cuốn sách #" + maSach + " thành công!", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể xóa sách!"));
                }
            } catch (RemoteException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(e.getMessage()));
            }
        }
        else if ("/api/admin/readers/create".equals(path)) {
            String hoTen = params.getOrDefault("hoTen", "").trim();
            String email = params.getOrDefault("email", "").trim();
            String matKhau = params.getOrDefault("matKhau", "Pass@123").trim();
            String sdt = params.getOrDefault("soDienThoai", "").trim();
            String vaiTro = params.getOrDefault("vaiTro", "READER").trim();

            if (hoTen.isEmpty() || email.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Họ tên và Email không được để trống!"));
                return;
            }

            DocGiaDTO dg = new DocGiaDTO(hoTen, email, matKhau, sdt, vaiTro);
            try {
                int newId = service.themDocGia(dg);
                if (newId > 0) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Cấp tài khoản mới thành công! Mã độc giả: #" + newId, JsonUtil.toJson(dg)));
                } else {
                    sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Không thể tạo tài khoản!"));
                }
            } catch (RemoteException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Email đã tồn tại hoặc lỗi CSDL: " + e.getMessage()));
            }
        }
        else if ("/api/admin/doc-gia/mo-khoa".equals(path) || "/api/admin/readers/unlock".equals(path)) {
            try {
                int maDocGia = Integer.parseInt(params.getOrDefault("maDocGia", "0"));
                if (maDocGia <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã độc giả không hợp lệ!"));
                    return;
                }

                boolean ok = service.moKhoaTaiKhoan(maDocGia);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Mở khóa tài khoản cho độc giả #" + maDocGia + " thành công!", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể mở khóa tài khoản!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maDocGia không đúng kiểu số!"));
            } catch (RemoteException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse(cleanErrorMessage(e.getMessage())));
            }
        }
        else if ("/api/phan-hoi".equals(path) || "/api/reader/phan-hoi".equals(path)) {
            int maDocGia = Integer.parseInt(params.getOrDefault("maDocGia", "0"));
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
            String tieuDe = params.getOrDefault("tieuDe", "Báo cáo tình trạng sách").trim();
            String noiDung = params.getOrDefault("noiDung", "").trim();

            if (noiDung.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Nội dung phản hồi không được để trống!"));
                return;
            }

            try {
                boolean ok = service.guiPhanHoi(maDocGia, maSach, tieuDe, noiDung);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Gửi phản hồi thành công! Thủ thư đã nhận được thông báo của bạn.", null));
                } else {
                    sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Không thể lưu phản hồi vào hệ thống!"));
                }
            } catch (RemoteException e) {
                sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Lỗi RMI: " + e.getMessage()));
            }
        }
        else if ("/api/admin/phan-hoi/xu-ly".equals(path)) {
            int maPhanHoi = Integer.parseInt(params.getOrDefault("maPhanHoi", "0"));
            String ghiChu = params.getOrDefault("ghiChu", "Đã tiếp nhận và xử lý").trim();

            if (maPhanHoi <= 0) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phản hồi không hợp lệ!"));
                return;
            }

            try {
                boolean ok = service.xuLyPhanHoi(maPhanHoi, ghiChu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Cập nhật trạng thái phản hồi thành công!", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không tìm thấy phản hồi hoặc cập nhật thất bại!"));
                }
            } catch (RemoteException e) {
                sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Lỗi RMI: " + e.getMessage()));
            }
        }
        else if ("/api/reader/yeu-thich/toggle".equals(path)) {
            int maDocGia = Integer.parseInt(params.getOrDefault("maDocGia", "0"));
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));

            if (maDocGia <= 0 || maSach <= 0) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã độc giả hoặc mã sách không hợp lệ!"));
                return;
            }

            try {
                boolean isAdded = service.toggleYeuThich(maDocGia, maSach);
                String msg = isAdded ? "Đã thêm cuốn sách vào mục Yêu thích!" : "Đã bỏ cuốn sách khỏi mục Yêu thích!";
                sendJsonResponse(exchange, 200, JsonUtil.successResponse(msg, "{\"isFavorite\":" + isAdded + "}"));
            } catch (RemoteException e) {
                sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Lỗi RMI: " + e.getMessage()));
            }
        }
        else {
            sendJsonResponse(exchange, 404, JsonUtil.errorResponse("Endpoint POST không tồn tại: " + path));
        }
    }

    private String extractToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String custom = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        return null;
    }

    private String extractQueryParam(String query, String name) {
        if (query == null) return "";
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && name.equalsIgnoreCase(pair[0])) {
                return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (java.io.InputStream is = exchange.getRequestBody();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String cleanErrorMessage(String full) {
        if (full == null || full.trim().isEmpty()) return "Thao tác không thành công!";

        String lower = full.toLowerCase();

        if (lower.contains("đền bù") || lower.contains("den bu") || lower.contains("trạng thái chờ đền bù") 
                || lower.contains("tráº¡ng thĂ¡i") || lower.contains("chá»  Ä‘á» n bĂ¹") 
                || lower.contains("báo mất") || lower.contains("bĂ¡o máº¥t")) {
            return "Bạn đang có phiếu mượn báo mất sách hoặc nợ tiền bồi hoàn chưa giải quyết. Vui lòng thanh toán tại quầy Thư viện trước khi mượn tiếp!";
        }
        if (lower.contains("bị khóa") || lower.contains("bá»‹ khĂ³a") || lower.contains("tạm khóa") || lower.contains("khóa tài khoản")) {
            return "Tài khoản của bạn hiện đang bị tạm khóa. Vui lòng liên hệ Thủ thư tại quầy để được mở khóa!";
        }
        if (lower.contains("3 cuốn") || lower.contains("3 cuá»‘n") || lower.contains("tối đa") || lower.contains("tá»‘i Ä‘a") || lower.contains("giới hạn")) {
            return "Độc giả đã đạt giới hạn mượn tối đa 3 cuốn sách chưa trả. Vui lòng hoàn trả bớt sách trước khi mượn tiếp!";
        }
        if (lower.contains("giữ chỗ") || lower.contains("giá»¯ chá»—") || lower.contains("chờ đến nhận")) {
            return "Cuốn sách này hiện đang được giữ chỗ bởi một bạn đọc khác đang chờ đến nhận tại quầy!";
        }
        if (lower.contains("đang được mượn") || lower.contains("mượn trùng") || lower.contains("mÆ°á»£n trĂ¹ng") || lower.contains("không thể mượn trùng")) {
            return "Cuốn sách này hiện đang được mượn bởi độc giả khác. Không thể mượn trùng!";
        }
        if (lower.contains("ngừng phục vụ") || lower.contains("mất hẳn") || lower.contains("bị mất")) {
            return "Cuốn sách này hiện đã ngừng phục vụ hoặc bị mất khỏi thư viện!";
        }
        if (lower.contains("không tồn tại") || lower.contains("khĂ´ng tá»“n táº¡i")) {
            return "Mã sách hoặc độc giả không tồn tại trong hệ thống!";
        }
        if (lower.contains("đã được trả") || lower.contains("đã trả") || lower.contains("Ä‘Ă£ Ä‘Æ°á»£c tráº£")) {
            return "Phiếu mượn này đã được hoàn tất trả trước đó!";
        }

        // Nếu chuỗi chứa "Lỗi: ..." hoặc "Lá»—i: ..."
        if (full.contains("Lỗi:")) {
            String extracted = full.substring(full.lastIndexOf("Lỗi:") + 4).trim();
            if (extracted.contains(";")) extracted = extracted.substring(0, extracted.indexOf(";")).trim();
            if (extracted.contains("\n")) extracted = extracted.substring(0, extracted.indexOf("\n")).trim();
            return extracted;
        }
        if (full.contains("Lá»—i:")) {
            String extracted = full.substring(full.lastIndexOf("Lá»—i:") + 6).trim();
            if (extracted.contains(";")) extracted = extracted.substring(0, extracted.indexOf(";")).trim();
            if (extracted.contains("\n")) extracted = extracted.substring(0, extracted.indexOf("\n")).trim();
            return extracted;
        }

        // Làm sạch các wrapper Java/SQL nếu còn lại
        String clean = full;
        if (clean.contains("nested exception is:")) {
            clean = clean.substring(clean.lastIndexOf("nested exception is:") + 20).trim();
        }
        if (clean.contains("RemoteException:")) {
            clean = clean.substring(clean.lastIndexOf("RemoteException:") + 16).trim();
        }
        if (clean.contains("SQLServerException:")) {
            clean = clean.substring(clean.lastIndexOf("SQLServerException:") + 19).trim();
        }
        if (clean.contains("Lỗi mượn sách:")) {
            clean = clean.substring(clean.lastIndexOf("Lỗi mượn sách:") + 14).trim();
        }

        return clean.trim();
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
