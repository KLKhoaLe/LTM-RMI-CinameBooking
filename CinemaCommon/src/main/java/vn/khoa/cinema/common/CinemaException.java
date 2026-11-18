package vn.khoa.cinema.common;

/** Lỗi nghiệp vụ có mã riêng, tách biệt với RemoteException (lỗi mạng). */
public class CinemaException extends Exception {
    private final String code;

    public CinemaException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
