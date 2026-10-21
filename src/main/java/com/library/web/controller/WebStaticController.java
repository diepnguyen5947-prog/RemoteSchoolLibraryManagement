package com.library.web.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Controller phục vụ các tệp HTML giao diện phân quyền cho Độc giả & Thủ thư:
 * - /login -> login.html
 * - /reader/dashboard -> reader_dashboard.html
 * - /reader/my-loans -> reader_loans.html
 * - /admin/dashboard -> admin_dashboard.html
 * - /admin/books -> admin_books.html
 * - /admin/loans -> admin_loans.html
 * - /admin/readers -> admin_readers.html
 */
public class WebStaticController implements HttpHandler {

    private final String baseDir;

    public WebStaticController(String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        String targetFile;
        if ("/".equals(path)) {
            targetFile = "login.html"; // Mặc định mở trang đăng nhập
        } else if ("/login".equalsIgnoreCase(path) || "/login.html".equalsIgnoreCase(path)) {
            targetFile = "login.html";
        } else if ("/reader/dashboard".equalsIgnoreCase(path) || "/reader".equalsIgnoreCase(path)) {
            targetFile = "reader_dashboard.html";
        } else if ("/reader/my-loans".equalsIgnoreCase(path)) {
            targetFile = "reader_loans.html";
        } else if ("/reader/wishlist".equalsIgnoreCase(path) || "/reader/yeu-thich".equalsIgnoreCase(path)) {
            targetFile = "reader_wishlist.html";
        } else if ("/book-detail".equalsIgnoreCase(path) || "/book-detail.html".equalsIgnoreCase(path) || "/sach".equalsIgnoreCase(path) || "/sach-chi-tiet".equalsIgnoreCase(path)) {
            targetFile = "book_detail.html";
        } else if ("/admin/dashboard".equalsIgnoreCase(path) || "/admin".equalsIgnoreCase(path)) {
            targetFile = "admin_dashboard.html";
        } else if ("/admin/books".equalsIgnoreCase(path)) {
            targetFile = "admin_books.html";
        } else if ("/admin/loans".equalsIgnoreCase(path)) {
            targetFile = "admin_loans.html";
        } else if ("/admin/loan-history".equalsIgnoreCase(path) || "/admin/history".equalsIgnoreCase(path) || "/admin/lich-su".equalsIgnoreCase(path)) {
            targetFile = "admin_loan_history.html";
        } else if ("/admin/readers".equalsIgnoreCase(path)) {
            targetFile = "admin_readers.html";
        } else if ("/admin/feedback".equalsIgnoreCase(path) || "/admin/phan-hoi".equalsIgnoreCase(path)) {
            targetFile = "admin_feedback.html";
        } else if ("/phieu-muon".equalsIgnoreCase(path) || "/phieu-muon.html".equalsIgnoreCase(path)) {
            targetFile = "phieu-muon.html";
        } else if ("/index.html".equalsIgnoreCase(path)) {
            targetFile = "index.html";
        } else {
            targetFile = path.startsWith("/") ? path.substring(1) : path;
        }

        File file = new File(baseDir, targetFile);
        if (!file.exists() || file.isDirectory()) {
            String notFound = "<h1>404 Not Found</h1><p>Trang hoặc tài nguyên không tồn tại: " + path + "</p>";
            exchange.sendResponseHeaders(404, notFound.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound.getBytes());
            }
            return;
        }

        String mimeType = "application/octet-stream";
        String lower = targetFile.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            mimeType = "text/html; charset=UTF-8";
        } else if (lower.endsWith(".css")) {
            mimeType = "text/css; charset=UTF-8";
        } else if (lower.endsWith(".js")) {
            mimeType = "application/javascript; charset=UTF-8";
        } else if (lower.endsWith(".json")) {
            mimeType = "application/json; charset=UTF-8";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            mimeType = "image/jpeg";
        } else if (lower.endsWith(".png")) {
            mimeType = "image/png";
        } else if (lower.endsWith(".webp")) {
            mimeType = "image/webp";
        } else if (lower.endsWith(".gif")) {
            mimeType = "image/gif";
        } else if (lower.endsWith(".svg")) {
            mimeType = "image/svg+xml";
        }

        exchange.getResponseHeaders().set("Content-Type", mimeType);
        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
