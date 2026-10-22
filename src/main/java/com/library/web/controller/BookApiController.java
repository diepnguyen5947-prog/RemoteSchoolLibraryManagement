package com.library.web.controller;

import com.library.model.DocGiaDTO;
import com.library.model.LibraryConstants;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;
import com.library.model.UserSessionDTO;
import com.library.service.ILibraryService;
import com.library.service.LibraryException;
import com.library.web.config.RMIClientManager;
import com.library.web.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý tất cả các REST API endpoints (Web Gateway).
 * Tuân thủ mô hình 3 tầng:
 * Browser -> HTTP/JSON -> BookApiController -> RMI Client Stub -> RMI Server -> DAO -> SQL Server.
 * Web Gateway không đi tắt xuống DAO/CSDL và không che giấu lỗi hạ tầng.
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
                handlePostRequests(exchange, service, path, query, params);
            } else {
                sendJsonResponse(exchange, 405, JsonUtil.errorResponse("Phương thức HTTP không được hỗ trợ!"));
            }

        } catch (LibraryException ex) {
            int status = mapLibraryException(ex);
            sendJsonResponse(exchange, status, JsonUtil.errorResponse(ex.getMessage()));
        } catch (RemoteException ex) {
            System.err.println("[BookApiController] Lỗi truyền thông RMI Server: " + ex.getMessage());
            sendJsonResponse(exchange, 503, JsonUtil.errorResponse("Dịch vụ Thư viện từ xa (RMI Server) đang ngoại tuyến hoặc không phản hồi: " + ex.getMessage()));
        } catch (Exception ex) {
            System.err.println("[BookApiController] Lỗi hệ thống: " + ex.getMessage());
            sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Lỗi máy chủ nội bộ: " + ex.getMessage()));
        }
    }

    private void handleGetRequests(HttpExchange exchange, ILibraryService service, String path, String query)
            throws IOException, LibraryException {

        String token = extractToken(exchange, null, query);

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
                        sendJsonResponse(exchange, 200, JsonUtil.successResponse("Tìm thấy sách", jsonBook));
                    } else {
                        sendJsonResponse(exchange, 404, JsonUtil.errorResponse("Không tìm thấy cuốn sách!"));
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã sách không hợp lệ!"));
                }
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Thiếu tham số maSach!"));
            }
        } else if ("/api/sach/tim-kiem".equals(path)) {
            String keyword = extractQueryParam(query, "q");
            if (keyword == null || keyword.isEmpty()) {
                keyword = extractQueryParam(query, "keyword");
            }
            List<SachDTO> list = service.searchBooks(keyword != null ? keyword : "");
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Tìm kiếm sách thành công", JsonUtil.sachListToJson(list)));
        } else if ("/api/phieu-muon".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập để xem danh sách phiếu mượn!"));
                return;
            }
            DocGiaDTO user = service.getProfile(token);
            List<PhieuMuonDTO> list;
            if (user.isLibrarian()) {
                list = service.getAllPhieuMuon(token);
            } else {
                list = service.getPhieuMuonByDocGia(token);
            }
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách phiếu mượn thành công", JsonUtil.phieuMuonListToJson(list)));
        } else if ("/api/doc-gia".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập với quyền Thủ thư!"));
                return;
            }
            List<DocGiaDTO> list = service.getAllDocGia(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách độc giả thành công", JsonUtil.docGiaListToJson(list)));
        } else if ("/api/admin/thong-ke".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập với quyền Thủ thư!"));
                return;
            }
            Map<String, Object> stats = service.layThongKe(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy thống kê thành công", JsonUtil.mapToJson(stats)));
        } else if ("/api/auth/me".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Chưa đăng nhập hoặc phiên đã hết hạn!"));
                return;
            }
            DocGiaDTO user = service.getProfile(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đang đăng nhập", JsonUtil.toJson(user)));
        } else if ("/api/admin/phan-hoi".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            List<PhanHoiDTO> list = service.getAllPhanHoi(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách phản hồi thành công", JsonUtil.phanHoiListToJson(list)));
        } else if ("/api/admin/phan-hoi/dem-chua-xu-ly".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            int count = service.demPhanHoiChuaXuLy(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đếm phản hồi chưa xử lý", "{\"chuaXuLy\":" + count + "}"));
        } else if ("/api/reader/yeu-thich".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập!"));
                return;
            }
            List<SachDTO> list = service.getDanhSachYeuThich(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Lấy danh sách yêu thích thành công", JsonUtil.sachListToJson(list)));
        } else if ("/api/reader/yeu-thich/check".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Chưa đăng nhập", "{\"isFavorite\":false}"));
                return;
            }
            String strMaSach = extractQueryParam(query, "maSach");
            int maSach = 0;
            try {
                maSach = Integer.parseInt(strMaSach);
            } catch (Exception ignored) {}
            boolean isFav = service.isYeuThich(token, maSach);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Kiểm tra yêu thích", "{\"isFavorite\":" + isFav + "}"));
        } else if ("/api/reader/status".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập!"));
                return;
            }
            DocGiaDTO user = service.getProfile(token);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Kiểm tra trạng thái độc giả", JsonUtil.toJson(user)));
        } else {
            sendJsonResponse(exchange, 404, JsonUtil.errorResponse("Endpoint GET không tồn tại: " + path));
        }
    }

    private void handlePostRequests(HttpExchange exchange, ILibraryService service, String path, String query, Map<String, String> params)
            throws IOException, LibraryException {

        String token = extractToken(exchange, params, query);

        // 1. ĐĂNG NHẬP / ĐĂNG XUẤT / ĐĂNG KÝ
        if ("/api/auth/login".equals(path)) {
            String email = params.getOrDefault("email", "").trim();
            String matKhau = params.getOrDefault("matKhau", "").trim();

            if (email.isEmpty() || matKhau.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Email và Mật khẩu không được để trống!"));
                return;
            }

            UserSessionDTO session = service.login(email, matKhau);
            exchange.getResponseHeaders().add("Set-Cookie", "auth_token=" + session.getToken() + "; Path=/; HttpOnly; SameSite=Lax");
            String data = String.format("{\"token\":\"%s\",\"user\":%s}", session.getToken(), JsonUtil.toJson(session.getUser()));
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đăng nhập thành công!", data));
        }
        else if ("/api/auth/logout".equals(path)) {
            if (token != null && !token.isEmpty()) {
                service.logout(token);
            }
            exchange.getResponseHeaders().add("Set-Cookie", "auth_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
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

            DocGiaDTO newDocGia = new DocGiaDTO(hoTen, email, matKhau, soDienThoai, LibraryConstants.ROLE_READER);
            int id = service.dangKyDocGia(newDocGia);
            newDocGia.setMaDocGia(id);
            newDocGia.setMatKhau(null);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đăng ký tài khoản độc giả thành công! Bạn có thể đăng nhập ngay.", JsonUtil.toJson(newDocGia)));
        }

        // 2. MƯỢN / TRẢ SÁCH (ĐỘC GIẢ)
        else if ("/api/muon-sach".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập để thực hiện mượn sách!"));
                return;
            }

            try {
                int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
                int soNgayMuon = Integer.parseInt(params.getOrDefault("soNgayMuon", "14"));

                if (maSach <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã sách không hợp lệ!"));
                    return;
                }

                int maPhieu = service.muonSach(token, maSach, soNgayMuon);
                DocGiaDTO caller = service.getProfile(token);
                long expMillis = System.currentTimeMillis() + 24L * 3600 * 1000;
                String strExp = JsonUtil.DATE_FORMAT.format(new Date(expMillis));
                String data = String.format("{\"maPhieu\":%d,\"maDocGia\":%d,\"maSach\":%d,\"ngayHetHanGiu\":\"%s\"}",
                        maPhieu, caller.getMaDocGia(), maSach, strExp);

                sendJsonResponse(exchange, 200, JsonUtil.successResponse(
                        "Đăng ký giữ sách thành công! Vui lòng đến thư viện nhận sách trước " + strExp, data));
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng tham số maSach không đúng kiểu số!"));
            }
        }
        else if ("/api/reader/yeu-cau-tra-sach".equals(path) || "/api/yeu-cau-tra-sach".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập để gửi yêu cầu trả sách!"));
                return;
            }
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }
                boolean ok = service.yeuCauTraSach(token, maPhieu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đã gửi yêu cầu trả sách tới Thủ thư! Vui lòng mang sách tới quầy để Thủ thư kiểm tra và xác nhận hoàn tất.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể gửi yêu cầu trả sách. Phiếu mượn không ở trạng thái hợp lệ!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            }
        }
        else if ("/api/reader/bao-mat-sach".equals(path) || "/api/bao-mat-sach".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập để báo mất sách!"));
                return;
            }
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }
                boolean ok = service.baoMatSach(token, maPhieu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đã ghi nhận báo mất sách! Hệ thống đã tính tiền đền bù theo đơn giá sách niêm yết. Vui lòng đến quầy thư viện nộp phạt để mở lại tài khoản.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể báo mất sách. Phiếu mượn không tồn tại hoặc đã trả trước đó!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            }
        }
        else if ("/api/phieu-muon/ngung-muon".equals(path) || "/api/phieu-muon/huy-giu-cho".equals(path) || "/api/ngung-muon".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập để ngưng mượn sách!"));
                return;
            }
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }
                boolean ok = service.huyPhieuGiuCho(token, maPhieu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Đã ngưng mượn thành công! Cuốn sách đã được chuyển về trạng thái Sẵn sàng.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể ngưng mượn. Yêu cầu giữ sách không ở trạng thái Chờ lấy sách!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            }
        }

        // 3. QUẢN TRỊ THỦ THƯ (LIBRARIAN APIS)
        else if ("/api/tra-sach".equals(path) || "/api/admin/tra-sach".equals(path) || "/api/admin/xac-nhan-tra-sach".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập với quyền Thủ thư!"));
                return;
            }
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }
                int tienPhuThu = 0;
                try {
                    tienPhuThu = Integer.parseInt(params.getOrDefault("tienPhuThu", "0"));
                } catch (NumberFormatException ignored) {}
                String tinhTrangSach = params.getOrDefault("tinhTrangSach", "Bình thường (Nguyên vẹn)");

                boolean ok = service.traSach(token, maPhieu, tienPhuThu, tinhTrangSach);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thủ thư đã xác nhận trả sách thành công! Sách đã được trả về trạng thái Sẵn sàng mượn.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể trả sách. Phiếu mượn không tồn tại hoặc đã trả trước đó!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng tham số không đúng kiểu số!"));
            }
        }
        else if ("/api/admin/xac-nhan-giao-sach".equals(path) || "/api/xac-nhan-giao-sach".equals(path) || "/api/admin/xac-nhan-gao-sach".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }
                int soNgayMuon = Integer.parseInt(params.getOrDefault("soNgayMuon", "0"));
                boolean ok = service.xacNhanGiaoSach(token, maPhieu, soNgayMuon);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thủ thư đã xác nhận giao sách thành công! Phiếu đã chuyển sang trạng thái Đang mượn.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể giao sách. Phiếu mượn không ở trạng thái Chờ lấy sách!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            }
        }
        else if ("/api/admin/xac-nhan-den-bu".equals(path) || "/api/xac-nhan-den-bu".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            try {
                int maPhieu = Integer.parseInt(params.getOrDefault("maPhieu", "0"));
                if (maPhieu <= 0) {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Mã phiếu không hợp lệ!"));
                    return;
                }
                boolean ok = service.xacNhanDenBu(token, maPhieu);
                if (ok) {
                    sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thủ thư đã xác nhận thu tiền đền bù thành công! Tài khoản độc giả đã được tự động mở khóa.", null));
                } else {
                    sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể xác nhận đền bù!"));
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Định dạng maPhieu không đúng kiểu số!"));
            }
        }
        else if ("/api/admin/huy-phieu-qua-han".equals(path) || "/api/huy-phieu-qua-han".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            int count = service.huyPhieuChoQuaHan(token);
            String msg = count > 0 
                    ? "Đã quét và hủy thành công " + count + " phiếu giữ chỗ quá hạn 24 giờ. Trạng thái sách đã được phục hồi về Sẵn sàng mượn!"
                    : "Không có phiếu giữ chỗ nào bị quá hạn 24 giờ cần hủy.";
            sendJsonResponse(exchange, 200, JsonUtil.successResponse(msg, "{\"soLuongHuy\":" + count + "}"));
        }
        else if ("/api/admin/upload-cover".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            DocGiaDTO caller = service.getProfile(token);
            if (!caller.isLibrarian()) {
                sendJsonResponse(exchange, 403, JsonUtil.errorResponse("Từ chối truy cập: Thao tác chỉ dành cho Thủ thư!"));
                return;
            }

            String base64Data = params.getOrDefault("base64Data", params.getOrDefault("imageData", "")).trim();
            String fileName = params.getOrDefault("fileName", "cover.jpg").trim();

            if (base64Data.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Dữ liệu ảnh tải lên không được để trống!"));
                return;
            }

            try {
                if (base64Data.contains("base64,")) {
                    base64Data = base64Data.substring(base64Data.indexOf("base64,") + 7);
                }
                base64Data = base64Data.replace(" ", "+").replaceAll("[\\r\\n]", "");
                byte[] imageBytes = Base64.getMimeDecoder().decode(base64Data);

                String ext = ".jpg";
                if (fileName.lastIndexOf('.') > 0) {
                    ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
                }
                if (!ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".png") && !ext.equals(".webp")) {
                    ext = ".jpg";
                }

                String newFileName = "cover_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

                File uploadDir = new File("src/main/resources/static/uploads/covers");
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }
                File targetFile = new File(uploadDir, newFileName);
                Files.write(targetFile.toPath(), imageBytes);

                File targetBuildDir = new File("target/classes/static/uploads/covers");
                if (targetBuildDir.exists() || new File("target/classes/static").exists()) {
                    targetBuildDir.mkdirs();
                    Files.write(new File(targetBuildDir, newFileName).toPath(), imageBytes);
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
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
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
            int newId = service.themSach(token, s);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Thêm sách mới thành công! Mã sách: #" + newId, JsonUtil.toJson(s)));
        }
        else if ("/api/admin/sach/update".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
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
            boolean ok = service.capNhatSach(token, s);
            if (ok) {
                String strKhoiPhuc = params.getOrDefault("khoiPhuc", "false").trim();
                if ("true".equalsIgnoreCase(strKhoiPhuc) || "1".equals(strKhoiPhuc)) {
                    service.boSungSachMoi(token, maSach, giaTien);
                }
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Cập nhật sách thành công!", null));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không tìm thấy cuốn sách để cập nhật!"));
            }
        }
        else if ("/api/admin/sach/bo-sung".equals(path) || "/api/admin/sach/khoi-phuc".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
            int giaTien = 50000;
            try {
                giaTien = Integer.parseInt(params.getOrDefault("giaTien", "50000"));
            } catch (NumberFormatException ignored) {}

            boolean ok = service.boSungSachMoi(token, maSach, giaTien);
            if (ok) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Bổ sung sách mới thành công! Sách #" + maSach + " đã được chuyển về trạng thái 'Sẵn sàng'.", null));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không tìm thấy cuốn sách để bổ sung!"));
            }
        }
        else if ("/api/admin/sach/delete".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
            boolean ok = service.xoaSach(token, maSach);
            if (ok) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Xóa cuốn sách #" + maSach + " thành công!", null));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể xóa sách!"));
            }
        }
        else if ("/api/admin/readers/create".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
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
            int newId = service.themDocGia(token, dg);
            dg.setMaDocGia(newId);
            dg.setMatKhau(null);
            sendJsonResponse(exchange, 200, JsonUtil.successResponse("Cấp tài khoản mới thành công! Mã độc giả: #" + newId, JsonUtil.toJson(dg)));
        }
        else if ("/api/admin/doc-gia/mo-khoa".equals(path) || "/api/admin/readers/unlock".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            int maDocGia = Integer.parseInt(params.getOrDefault("maDocGia", "0"));
            boolean ok = service.moKhoaTaiKhoan(token, maDocGia);
            if (ok) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Mở khóa tài khoản cho độc giả #" + maDocGia + " thành công!", null));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không thể mở khóa tài khoản!"));
            }
        }
        else if ("/api/phan-hoi".equals(path) || "/api/reader/phan-hoi".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập để gửi phản hồi!"));
                return;
            }
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
            String tieuDe = params.getOrDefault("tieuDe", "Báo cáo tình trạng sách").trim();
            String noiDung = params.getOrDefault("noiDung", "").trim();

            if (noiDung.isEmpty()) {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Nội dung phản hồi không được để trống!"));
                return;
            }

            boolean ok = service.guiPhanHoi(token, maSach, tieuDe, noiDung);
            if (ok) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Gửi phản hồi thành công! Thủ thư đã nhận được thông báo của bạn.", null));
            } else {
                sendJsonResponse(exchange, 500, JsonUtil.errorResponse("Không thể lưu phản hồi vào hệ thống!"));
            }
        }
        else if ("/api/admin/phan-hoi/xu-ly".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu quyền Thủ thư!"));
                return;
            }
            int maPhanHoi = Integer.parseInt(params.getOrDefault("maPhanHoi", "0"));
            String ghiChu = params.getOrDefault("ghiChu", "Đã tiếp nhận và xử lý").trim();

            boolean ok = service.xuLyPhanHoi(token, maPhanHoi, ghiChu);
            if (ok) {
                sendJsonResponse(exchange, 200, JsonUtil.successResponse("Cập nhật trạng thái phản hồi thành công!", null));
            } else {
                sendJsonResponse(exchange, 400, JsonUtil.errorResponse("Không tìm thấy phản hồi hoặc cập nhật thất bại!"));
            }
        }
        else if ("/api/reader/yeu-thich/toggle".equals(path)) {
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 401, JsonUtil.errorResponse("Yêu cầu đăng nhập!"));
                return;
            }
            int maSach = Integer.parseInt(params.getOrDefault("maSach", "0"));
            boolean isAdded = service.toggleYeuThich(token, maSach);
            String msg = isAdded ? "Đã thêm cuốn sách vào mục Yêu thích!" : "Đã bỏ cuốn sách khỏi mục Yêu thích!";
            sendJsonResponse(exchange, 200, JsonUtil.successResponse(msg, "{\"isFavorite\":" + isAdded + "}"));
        }
        else {
            sendJsonResponse(exchange, 404, JsonUtil.errorResponse("Endpoint POST không tồn tại: " + path));
        }
    }

    private int mapLibraryException(LibraryException ex) {
        String code = ex.getErrorCode();
        if (LibraryConstants.ERR_UNAUTHORIZED.equals(code)) {
            return 401;
        }
        if (LibraryConstants.ERR_FORBIDDEN.equals(code) || 
            LibraryConstants.ERR_ACCOUNT_LOCKED_OVERDUE.equals(code) || 
            LibraryConstants.ERR_ACCOUNT_LOCKED_LOST.equals(code)) {
            return 403;
        }
        if (LibraryConstants.ERR_NOT_FOUND.equals(code) || 
            LibraryConstants.ERR_BOOK_NOT_FOUND.equals(code) || 
            LibraryConstants.ERR_READER_NOT_FOUND.equals(code) || 
            LibraryConstants.ERR_LOAN_NOT_FOUND.equals(code)) {
            return 404;
        }
        if (LibraryConstants.ERR_BOOK_NOT_AVAILABLE.equals(code) || 
            LibraryConstants.ERR_BOOK_RESERVED.equals(code) || 
            LibraryConstants.ERR_BOOK_ALREADY_BORROWED.equals(code)) {
            return 409;
        }
        if (LibraryConstants.ERR_BAD_REQUEST.equals(code) || 
            LibraryConstants.ERR_LOAN_LIMIT_EXCEEDED.equals(code) || 
            LibraryConstants.ERR_INVALID_STATE.equals(code)) {
            return 400;
        }
        return 500;
    }

    private String extractToken(HttpExchange exchange, Map<String, String> params, String query) {
        // 1. Authorization: Bearer <token>
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }

        // 2. Custom header X-Auth-Token
        String custom = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }

        // 3. Cookie auth_token
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && "auth_token".equalsIgnoreCase(pair[0].trim())) {
                    return pair[1].trim();
                }
            }
        }

        // 4. Form/Body parameter "token"
        if (params != null && params.containsKey("token")) {
            String t = params.get("token");
            if (t != null && !t.trim().isEmpty()) return t.trim();
        }

        // 5. Query parameter "token"
        if (query != null && !query.isEmpty()) {
            String t = extractQueryParam(query, "token");
            if (t != null && !t.trim().isEmpty()) return t.trim();
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
