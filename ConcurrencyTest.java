import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Lớp kiểm thử đồng thời tầng Web Gateway (HTTP Concurrency Test).
 * Mô phỏng 2 độc giả gửi yêu cầu mượn cùng một cuốn sách qua Web API tại cùng một thời điểm.
 * Bổ trợ cho kiểm thử tầng RMI trực tiếp (RmiConcurrencyTest).
 */
public class ConcurrencyTest {

    public static void main(String[] args) throws Exception {
        System.out.println("====================================================================");
        System.out.println("  KIỂM THỬ ĐỒNG THỜI TẦNG WEB GATEWAY (HTTP CONCURRENCY TEST)");
        System.out.println("====================================================================");

        int maSach = 5; // Cuốn sách Số đỏ
        String baseUrl = "http://localhost:8080";

        HttpClient client = HttpClient.newHttpClient();

        // 1. Đăng nhập 2 tài khoản độc giả hợp lệ để lấy Token (Tránh hardcode ID 1 là Thủ thư)
        System.out.println("[Bước 1] Đăng nhập 2 tài khoản Độc giả (READER):");
        String token1 = loginAndGetToken(client, baseUrl, "nguyenvanan@school.edu.vn", "123456");
        String token2 = loginAndGetToken(client, baseUrl, "tranthibinh@school.edu.vn", "123456");

        if (token1 == null || token2 == null) {
            System.err.println("[BLOCKED] Không thể đăng nhập tài khoản độc giả qua Web Server (cổng 8080)!");
            System.err.println("Vui lòng chắc chắn rằng run_server.bat và run_web_client.bat đang chạy.");
            return;
        }

        System.out.println("  -> Độc giả 1 (An):    Token = " + token1.substring(0, 8) + "...");
        System.out.println("  -> Độc giả 2 (Bình):  Token = " + token2.substring(0, 8) + "...");

        // 2. Chuẩn bị 2 request mượn sách song song
        System.out.println("\n[Bước 2] Gửi 2 yêu cầu mượn song song cho cuốn sách mã " + maSach + "...");
        String borrowUrl = baseUrl + "/api/muon-sach";

        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(borrowUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + token1)
                .POST(HttpRequest.BodyPublishers.ofString("maSach=" + maSach + "&soNgayMuon=14"))
                .build();

        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(borrowUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + token2)
                .POST(HttpRequest.BodyPublishers.ofString("maSach=" + maSach + "&soNgayMuon=7"))
                .build();

        long start = System.currentTimeMillis();
        CompletableFuture<HttpResponse<String>> future1 = client.sendAsync(req1, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> future2 = client.sendAsync(req2, HttpResponse.BodyHandlers.ofString());

        CompletableFuture.allOf(future1, future2).join();
        long duration = System.currentTimeMillis() - start;

        HttpResponse<String> resp1 = future1.get();
        HttpResponse<String> resp2 = future2.get();

        System.out.println("\n>>> KẾT QUẢ PHẢN HỒI TỪ YÊU CẦU 1 (Độc giả 1):");
        System.out.println("    Status Code: " + resp1.statusCode());
        System.out.println("    Nội dung:    " + resp1.body());

        System.out.println("\n>>> KẾT QUẢ PHẢN HỒI TỪ YÊU CẦU 2 (Độc giả 2):");
        System.out.println("    Status Code: " + resp2.statusCode());
        System.out.println("    Nội dung:    " + resp2.body());

        System.out.println("\n--------------------------------------------------------------------");
        System.out.println("Thời gian thực thi 2 luồng đồng thời: " + duration + " ms");

        boolean oneSuccessOneReject = (resp1.statusCode() == 200 && resp2.statusCode() == 409) ||
                                     (resp2.statusCode() == 200 && resp1.statusCode() == 409);

        if (oneSuccessOneReject) {
            System.out.println(">>> ĐÁNH GIÁ VÒNG TEST: ĐẠT!");
            System.out.println("    [OK] Đúng 1 yêu cầu mượn thành công (HTTP 200 - Đã cấp mã phiếu giữ chỗ).");
            System.out.println("    [OK] Đúng 1 yêu cầu bị từ chối (HTTP 409 - Sách đang được mượn/giữ chỗ bởi người khác).");
            System.out.println("    [OK] Cơ chế phân quyền và bảo vệ bất biến của SQL Server và RMI Server đã hoạt động đúng.");
        } else {
            System.out.println(">>> ĐÁNH GIÁ: Không thỏa mãn điều kiện 1 thành công (200) và 1 bị từ chối (409).");
        }
        System.out.println("====================================================================");
    }

    private static String loginAndGetToken(HttpClient client, String baseUrl, String email, String password) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("email=" + email + "&matKhau=" + password))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                int idx = body.indexOf("\"token\":\"");
                if (idx > 0) {
                    int start = idx + 9;
                    int end = body.indexOf("\"", start);
                    return body.substring(start, end);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
