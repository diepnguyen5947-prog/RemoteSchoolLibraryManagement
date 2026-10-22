package com.library.web.config;

import com.library.service.ILibraryService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Lớp quản lý kết nối RMI Client (RMIClientManager).
 * Chịu trách nhiệm định vị RMI Registry, tra cứu Remote Stub ILibraryService,
 * và tự động kết nối lại (auto-reconnect) khi gặp sự cố mạng hoặc Server khởi động lại.
 * Sử dụng phương thức ping() nhẹ nhàng thay vì truy vấn toàn bộ dữ liệu CSDL.
 */
public class RMIClientManager {

    private static String getRmiHost() {
        String host = System.getProperty("rmi.host");
        if (host == null || host.trim().isEmpty()) {
            host = System.getenv("RMI_HOST");
        }
        return (host != null && !host.trim().isEmpty()) ? host.trim() : "127.0.0.1";
    }

    private static int getRmiPort() {
        String portStr = System.getProperty("rmi.port");
        if (portStr == null || portStr.trim().isEmpty()) {
            portStr = System.getenv("RMI_PORT");
        }
        if (portStr != null && !portStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(portStr.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 1099;
    }

    private static String getServiceName() {
        String svc = System.getProperty("rmi.service");
        if (svc == null || svc.trim().isEmpty()) {
            svc = System.getenv("RMI_SERVICE");
        }
        return (svc != null && !svc.trim().isEmpty()) ? svc.trim() : "LibraryService";
    }

    private static RMIClientManager instance;
    private ILibraryService cachedService;

    private RMIClientManager() {}

    public static synchronized RMIClientManager getInstance() {
        if (instance == null) {
            instance = new RMIClientManager();
        }
        return instance;
    }

    /**
     * Lấy đối tượng Remote Stub ILibraryService.
     * Tự động tra cứu hoặc kết nối lại nếu kết nối trước đó bị gián đoạn.
     * 
     * @return Đối tượng ILibraryService
     * @throws RemoteException nếu không thể kết nối tới RMI Server
     */
    public synchronized ILibraryService getService() throws RemoteException {
        if (cachedService != null) {
            try {
                // Kiểm tra tính sẵn sàng bằng ping() nhẹ nhàng, không truy vấn CSDL
                cachedService.ping();
                return cachedService;
            } catch (RemoteException ex) {
                System.err.println("[RMIClientManager] CẢNH BÁO: Mất kết nối tới RMI Server (" + ex.getMessage() + "). Đang thử kết nối lại...");
                cachedService = null;
            }
        }

        return reconnect();
    }

    /**
     * Thực hiện kết nối lại tới RMI Server
     */
    public synchronized ILibraryService reconnect() throws RemoteException {
        String host = getRmiHost();
        int port = getRmiPort();
        String serviceName = getServiceName();
        String url = String.format("rmi://%s:%d/%s", host, port, serviceName);

        try {
            System.out.println("[RMIClientManager] Đang tra cứu RMI Service tại: " + url);
            Registry registry = LocateRegistry.getRegistry(host, port);
            cachedService = (ILibraryService) registry.lookup(serviceName);
            System.out.println("[RMIClientManager] -> KẾT NỐI RMI CLIENT THÀNH CÔNG!");
            return cachedService;
        } catch (NotBoundException e) {
            cachedService = null;
            String msg = "Dịch vụ '" + serviceName + "' chưa được đăng ký trong RMI Registry!";
            System.err.println("[RMIClientManager] Lỗi: " + msg);
            throw new RemoteException(msg, e);
        } catch (Exception e) {
            cachedService = null;
            String msg = "Không thể kết nối tới RMI Server tại " + url + ": " + e.getMessage();
            System.err.println("[RMIClientManager] Lỗi kết nối: " + msg);
            throw new RemoteException(msg, e);
        }
    }

    public synchronized void resetConnection() {
        this.cachedService = null;
    }

    public static String getRmiUrl() {
        return String.format("rmi://%s:%d/%s", getRmiHost(), getRmiPort(), getServiceName());
    }
}
