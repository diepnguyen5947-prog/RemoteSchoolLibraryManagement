package com.library.model;

import java.io.Serializable;
import java.util.Date;

/**
 * DTO đại diện cho phiên làm việc đã xác thực của người dùng (Token-based Session).
 * Tuân thủ Java Serialization (Bài 6) để truyền qua Java RMI (Bài 9).
 */
public class UserSessionDTO implements Serializable {
    private static final long serialVersionUID = 1002L;

    private String token;
    private DocGiaDTO user;
    private Date loginTime;

    public UserSessionDTO() {}

    public UserSessionDTO(String token, DocGiaDTO user) {
        this.token = token;
        this.user = user;
        this.loginTime = new Date();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public DocGiaDTO getUser() {
        return user;
    }

    public void setUser(DocGiaDTO user) {
        this.user = user;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Date loginTime) {
        this.loginTime = loginTime;
    }

    @Override
    public String toString() {
        return "UserSessionDTO{" +
                "token='" + (token != null ? token.substring(0, Math.min(token.length(), 8)) + "..." : "null") + '\'' +
                ", user=" + (user != null ? user.getHoTen() + " (" + user.getVaiTro() + ")" : "null") +
                ", loginTime=" + loginTime +
                '}';
    }
}
