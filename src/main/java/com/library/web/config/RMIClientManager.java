package com.library.web.config;

import com.library.service.ILibraryService;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Lớp quản lý kết nối RMI Client (RMIClientManager).
 * Chịu trách nhiệm định vị RMI Registry, tra cứu Remote Stub ILibraryService,
 * và tự động kết nối lại (auto-reconnect) khi gặp sự cố mạng hoặc Server khởi động lại.
 */
public class RMIClientManager {

    private static final String RMI_HOST = System.getProperty("rmi.host", "localhost");
    private static final int RMI_PORT = Integer.getInteger("rmi.port", 1099);
    private static final String SERVICE_NAME = System.getProperty("rmi.service", "LibraryService");
    private static final String RMI_URL = String.format("rmi://%s:%d/%s", RMI_HOST, RMI_PORT, SERVICE_NAME);

    private static RMIClientManager instance;
    private ILibraryService cachedService;

    private RMIClientManager() {
    }

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
                // Kiểm tra tính sẵn sàng của kết nối (ping nhẹ)
                cachedService.getAllBooks();
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
        try {
            System.out.println("[RMIClientManager] Đang tra cứu RMI Service tại: " + RMI_URL);
            Registry registry = LocateRegistry.getRegistry(RMI_HOST, RMI_PORT);
            cachedService = (ILibraryService) registry.lookup(SERVICE_NAME);
            System.out.println("[RMIClientManager] -> KẾT NỐI RMI CLIENT THÀNH CÔNG!");
            return cachedService;
        } catch (NotBoundException e) {
            cachedService = null;
            String msg = "Dịch vụ '" + SERVICE_NAME + "' chưa được đăng ký trong RMI Registry!";
            System.err.println("[RMIClientManager] Lỗi: " + msg);
            throw new RemoteException(msg, e);
        } catch (Exception e) {
            cachedService = null;
            String msg = "Không thể kết nối tới RMI Server tại " + RMI_URL + ": " + e.getMessage();
            System.err.println("[RMIClientManager] Lỗi kết nối: " + msg);
            throw new RemoteException(msg, e);
        }
    }

    public synchronized void resetConnection() {
        this.cachedService = null;
    }

    public static String getRmiUrl() {
        return RMI_URL;
    }
}
