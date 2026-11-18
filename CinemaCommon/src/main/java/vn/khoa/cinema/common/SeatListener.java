package vn.khoa.cinema.common;

import java.rmi.*;

public interface SeatListener extends Remote {
    // Chỉ báo thay đổi; client đọc lại dữ liệu chính thức từ server.
    void seatsChanged(String showtimeId, long revision) throws RemoteException;
}
