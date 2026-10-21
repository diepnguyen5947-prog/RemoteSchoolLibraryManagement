package com.library.web.util;

import com.library.model.DocGiaDTO;
import com.library.model.PhanHoiDTO;
import com.library.model.PhieuMuonDTO;
import com.library.model.SachDTO;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiện ích chuyển đổi dữ liệu sang chuỗi định dạng JSON chuẩn.
 */
public class JsonUtil {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    public static String toJson(SachDTO s) {
        if (s == null) return "null";
        String raw = s.getHinhAnh() != null ? s.getHinhAnh().trim() : "";
        List<String> listImages = new ArrayList<>();
        if (!raw.isEmpty()) {
            String[] parts = raw.split("\\|\\|\\|");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    listImages.add(trimmed);
                }
            }
        }
        String mainCover = !listImages.isEmpty() ? listImages.get(0) : "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&q=80";
        if (listImages.isEmpty()) {
            listImages.add(mainCover);
        }

        StringBuilder imgArrayJson = new StringBuilder("[");
        for (int i = 0; i < listImages.size(); i++) {
            imgArrayJson.append("\"").append(escape(listImages.get(i))).append("\"");
            if (i < listImages.size() - 1) imgArrayJson.append(",");
        }
        imgArrayJson.append("]");

        return String.format(
                "{\"maSach\":%d,\"tenSach\":\"%s\",\"tacGia\":\"%s\",\"namXuatBan\":%d,\"trangThai\":%d,\"moTaTrangThai\":\"%s\",\"giaTien\":%d,\"hinhAnh\":\"%s\",\"danhSachHinhAnh\":%s,\"rawHinhAnh\":\"%s\"}",
                s.getMaSach(),
                escape(s.getTenSach()),
                escape(s.getTacGia()),
                s.getNamXuatBan(),
                s.getTrangThai(),
                escape(s.getTrangThaiText()),
                s.getGiaTien(),
                escape(mainCover),
                imgArrayJson.toString(),
                escape(raw)
        );
    }

    public static String sachListToJson(List<SachDTO> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String toJson(DocGiaDTO d) {
        if (d == null) return "null";
        return String.format(
                "{\"maDocGia\":%d,\"hoTen\":\"%s\",\"email\":\"%s\",\"soDienThoai\":\"%s\",\"vaiTro\":\"%s\",\"trangThaiTaiKhoan\":%d,\"isLocked\":%b,\"lyDoKhoa\":\"%s\"}",
                d.getMaDocGia(),
                escape(d.getHoTen()),
                escape(d.getEmail()),
                escape(d.getSoDienThoai()),
                escape(d.getVaiTro()),
                d.getTrangThaiTaiKhoan(),
                d.isLocked(),
                escape(d.getLyDoKhoa())
        );
    }

    public static String docGiaListToJson(List<DocGiaDTO> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String toJson(PhieuMuonDTO p) {
        if (p == null) return "null";
        String strNgayMuon = p.getNgayMuon() != null ? DATE_FORMAT.format(p.getNgayMuon()) : "";
        String strNgayHenTra = p.getNgayHenTra() != null ? DATE_FORMAT.format(p.getNgayHenTra()) : "";
        String strNgayTra = p.getNgayTraThucTe() != null ? DATE_FORMAT.format(p.getNgayTraThucTe()) : "";
        String strNgayHetHanGiu = p.getNgayHetHanGiu() != null ? DATE_FORMAT.format(p.getNgayHetHanGiu()) : "";

        return String.format(
                "{\"maPhieu\":%d,\"maDocGia\":%d,\"tenDocGia\":\"%s\",\"maSach\":%d,\"tenSach\":\"%s\"," +
                "\"ngayMuon\":\"%s\",\"ngayHenTra\":\"%s\",\"ngayTraThucTe\":\"%s\",\"ngayHetHanGiu\":\"%s\",\"isQuaHanGiu\":%b," +
                "\"trangThaiPhieu\":%d,\"moTaTrangThaiPhieu\":\"%s\"," +
                "\"soNgayMuon\":%d,\"soNgayQuaHan\":%d,\"tienPhat\":%d,\"isQuaHan\":%b,\"tienDenBu\":%d,\"giaTienSach\":%d," +
                "\"tinhTrangSachKhiTra\":\"%s\"}",
                p.getMaPhieu(),
                p.getMaDocGia(),
                escape(p.getTenDocGia()),
                p.getMaSach(),
                escape(p.getTenSach()),
                escape(strNgayMuon),
                escape(strNgayHenTra),
                escape(strNgayTra),
                escape(strNgayHetHanGiu),
                p.isQuaHanGiu(),
                p.getTrangThaiPhieu(),
                escape(p.getTrangThaiPhieuText()),
                p.getSoNgayMuon(),
                p.getSoNgayQuaHan(),
                p.getTienPhat(),
                p.isQuaHan(),
                p.getTienDenBu(),
                p.getGiaTienSach(),
                escape(p.getTinhTrangSachKhiTra())
        );
    }

    public static String phieuMuonListToJson(List<PhieuMuonDTO> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String toJson(PhanHoiDTO p) {
        if (p == null) return "null";
        String strNgayGui = p.getNgayGui() != null ? DATE_FORMAT.format(p.getNgayGui()) : "";
        return String.format(
                "{\"maPhanHoi\":%d,\"maDocGia\":%d,\"tenDocGia\":\"%s\",\"emailDocGia\":\"%s\"," +
                "\"maSach\":%d,\"tenSach\":\"%s\",\"tieuDe\":\"%s\",\"noiDung\":\"%s\"," +
                "\"ngayGui\":\"%s\",\"trangThai\":%d,\"ghiChuThuThu\":\"%s\"}",
                p.getMaPhanHoi(),
                p.getMaDocGia(),
                escape(p.getTenDocGia()),
                escape(p.getEmailDocGia()),
                p.getMaSach(),
                escape(p.getTenSach()),
                escape(p.getTieuDe()),
                escape(p.getNoiDung()),
                escape(strNgayGui),
                p.getTrangThai(),
                escape(p.getGhiChuThuThu())
        );
    }

    public static String phanHoiListToJson(List<PhanHoiDTO> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String mapToJson(Map<String, Object> map) {
        if (map == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(escape(val.toString())).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public static String successResponse(String message, String dataJson) {
        return String.format("{\"success\":true,\"message\":\"%s\",\"data\":%s}",
                escape(message), (dataJson != null ? dataJson : "null"));
    }

    public static String errorResponse(String message) {
        return String.format("{\"success\":false,\"message\":\"%s\",\"data\":null}", escape(message));
    }

    /**
     * Phân tích query string hoặc form body x-www-form-urlencoded hoặc JSON
     */
    public static Map<String, String> parseFormData(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return map;

        String trimmed = body.trim();
        // Nếu là định dạng JSON (Xử lý quét ký tự tuyến tính O(N), không dùng Regex tránh StackOverflow khi payload lớn)
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            int len = trimmed.length();
            int i = 1;
            while (i < len - 1) {
                // Bỏ qua khoảng trắng hoặc dấu phẩy
                while (i < len - 1 && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ',')) {
                    i++;
                }
                if (i >= len - 1) break;

                // Tìm bắt đầu key: '"'
                if (trimmed.charAt(i) != '"') {
                    i++;
                    continue;
                }
                i++; // Bỏ qua dấu '"' mở đầu key

                // Đọc key
                StringBuilder keySb = new StringBuilder();
                while (i < len - 1) {
                    char c = trimmed.charAt(i);
                    if (c == '\\' && i + 1 < len - 1) {
                        keySb.append(trimmed.charAt(i + 1));
                        i += 2;
                    } else if (c == '"') {
                        i++; // Bỏ qua dấu '"' kết thúc key
                        break;
                    } else {
                        keySb.append(c);
                        i++;
                    }
                }
                String key = keySb.toString();

                // Tìm dấu ':' ngăn cách key và value
                while (i < len - 1 && trimmed.charAt(i) != ':') {
                    i++;
                }
                if (i >= len - 1) break;
                i++; // Bỏ qua ':'

                // Bỏ qua khoảng trắng trước value
                while (i < len - 1 && Character.isWhitespace(trimmed.charAt(i))) {
                    i++;
                }
                if (i >= len - 1) break;

                // Đọc value
                StringBuilder valSb = new StringBuilder();
                if (trimmed.charAt(i) == '"') {
                    i++; // Bỏ qua '"' mở đầu value
                    while (i < len - 1) {
                        char c = trimmed.charAt(i);
                        if (c == '\\' && i + 1 < len - 1) {
                            char next = trimmed.charAt(i + 1);
                            if (next == '\"') valSb.append('\"');
                            else if (next == '\\') valSb.append('\\');
                            else if (next == 'n') valSb.append('\n');
                            else if (next == 'r') valSb.append('\r');
                            else if (next == 't') valSb.append('\t');
                            else { valSb.append('\\').append(next); }
                            i += 2;
                        } else if (c == '"') {
                            i++; // Bỏ qua '"' kết thúc value
                            break;
                        } else {
                            valSb.append(c);
                            i++;
                        }
                    }
                } else {
                    // Value là number hoặc boolean hoặc null (không có ngoặc kép)
                    while (i < len - 1 && trimmed.charAt(i) != ',' && trimmed.charAt(i) != '}') {
                        valSb.append(trimmed.charAt(i));
                        i++;
                    }
                }
                map.put(key, valSb.toString().trim());
            }
            return map;
        }

        // Định dạng form urlencoded: key1=val1&key2=val2...
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String k = java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String v = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    map.put(k, v);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }
}
