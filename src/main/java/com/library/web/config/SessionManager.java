package com.library.web.config;

import com.library.model.DocGiaDTO;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý phiên đăng nhập và phân quyền Token-based Session.
 */
public class SessionManager {

    private static final Map<String, DocGiaDTO> SESSIONS = new ConcurrentHashMap<>();

    public static String createSession(DocGiaDTO user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        SESSIONS.put(token, user);
        return token;
    }

    public static DocGiaDTO getUser(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        return SESSIONS.get(token.trim());
    }

    public static void removeSession(String token) {
        if (token != null) {
            SESSIONS.remove(token.trim());
        }
    }
}
