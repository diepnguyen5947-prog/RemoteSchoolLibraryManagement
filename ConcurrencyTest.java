import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Lớp kiểm thử đồng thời (Concurrency Test).
 * Gửi 2 yêu cầu HTTP POST đồng thời đến Web Server tại cùng 1 mili-giây
 * để kiểm tra cơ chế chống mượn trùng (Race Condition) của RMI Server và SQL Server.
 */
public class ConcurrencyTest {

    public static void main(String[] args) throws Exception {
        System.out.println("====================================================================");
        System.out.println("  KIỂM THỬ ĐỒNG THỜI (CONCURRENCY TEST) GỬI 2 HTTP REQUEST MƯỢN SÁCH");
        System.out.println("====================================================================");

        int maSach = 5; // Cuốn sách Số đỏ
        String url = "http://localhost:8080/api/muon-sach";

        System.out.println("Mục tiêu kiểm thử: Cuốn sách mã " + maSach + " (Số đỏ)");
        System.out.println("Gửi 2 HTTP POST requests song song:");
        System.out.println("  - Request 1: Độc giả 1 (Nguyễn Văn An) mượn sách mã " + maSach);
        System.out.println("  - Request 2: Độc giả 2 (Trần Thị Bình) mượn sách mã " + maSach);
        System.out.println("--------------------------------------------------------------------");

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("maDocGia=1&maSach=" + maSach + "&soNgayMuon=14"))
                .build();

        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("maDocGia=2&maSach=" + maSach + "&soNgayMuon=7"))
                .build();

        // Kích hoạt đồng thời bằng CompletableFuture
        long start = System.currentTimeMillis();
        CompletableFuture<HttpResponse<String>> future1 = client.sendAsync(req1, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> future2 = client.sendAsync(req2, HttpResponse.BodyHandlers.ofString());

        CompletableFuture.allOf(future1, future2).join();
        long duration = System.currentTimeMillis() - start;

        HttpResponse<String> resp1 = future1.get();
        HttpResponse<String> resp2 = future2.get();

        System.out.println("\n>>> KẾT QUẢ PHẢN HỒI TỪ YÊU CẦU 1:");
        System.out.println("    Status Code: " + resp1.statusCode());
        System.out.println("    Nội dung:    " + resp1.body());

        System.out.println("\n>>> KẾT QUẢ PHẢN HỒI TỪ YÊU CẦU 2:");
        System.out.println("    Status Code: " + resp2.statusCode());
        System.out.println("    Nội dung:    " + resp2.body());

        System.out.println("\n--------------------------------------------------------------------");
        System.out.println("Thời gian thực thi 2 luồng đồng thời: " + duration + " ms");

        boolean oneSuccessOneReject = (resp1.statusCode() == 200 && resp2.statusCode() == 409) ||
                                     (resp2.statusCode() == 200 && resp1.statusCode() == 409);

        if (oneSuccessOneReject) {
            System.out.println(">>> ĐÁNH GIÁ: KIỂM THỬ ĐỒNG THỜI ĐẠT ĐỘ CHÍNH XÁC 100%!");
            System.out.println("    [OK] ĐÚNG 1 yêu cầu mượn thành công (HTTP 200 - Đã cấp mã phiếu).");
            System.out.println("    [OK] ĐÚNG 1 yêu cầu bị từ chối (HTTP 409 - Sách đang được mượn bởi người khác).");
            System.out.println("    [OK] Khóa dòng UPDLOCK của SQL Server và RMI Server đã ngăn chặn hoàn toàn Race Condition!");
        } else {
            System.out.println(">>> ĐÁNH GIÁ: Cần kiểm tra lại kết quả.");
        }
        System.out.println("====================================================================");
    }
}
