package vn.khoa.cinema.server;

import vn.khoa.cinema.common.*;
import vn.khoa.cinema.common.Models.*;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Nơi tập trung quy tắc nghiệp vụ. RMI có thể gọi nhiều phương thức này đồng thời. */
public final class CinemaServiceImpl implements CinemaService, AutoCloseable {
    private record LoginSession(String username, Instant expiresAt) {}

    private final FileStore store;
    private final Clock clock;
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();
    private final CallbackHub callbacks = new CallbackHub();

    public CinemaServiceImpl(java.nio.file.Path data, Clock clock) throws IOException {
        this.clock = clock;
        this.store = new FileStore(data, clock);
    }

    @Override
    public String ping() {
        return "CINEMA_RMI_V1";
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private CinemaException error(String code, String message) {
        return new CinemaException(code, message);
    }

    private String user(String token) throws CinemaException {
        LoginSession session = token == null ? null : sessions.get(token);
        if (session == null || !clock.instant().isBefore(session.expiresAt())) {
            if (token != null) {
                sessions.remove(token);
                callbacks.remove(token);
            }
            throw error("AUTH", "Phiên đã hết hạn. Vui lòng đăng nhập lại.");
        }
        return session.username();
    }

    private Session createSession(User account) {
        sessions.forEach(
                (key, value) -> {
                    if (!clock.instant().isBefore(value.expiresAt())) {
                        sessions.remove(key);
                        callbacks.remove(key);
                    }
                });
        String token = UUID.randomUUID().toString();
        sessions.put(
                token,
                new LoginSession(account.username(), clock.instant().plus(Duration.ofHours(8))));
        return new Session(token, account.username(), account.displayName());
    }

    private String normalize(String username) throws CinemaException {
        if (username == null || !username.trim().matches("[A-Za-z0-9_]{3,24}"))
            throw error(
                    "VALIDATION", "Tên đăng nhập: 3–24 ký tự, gồm chữ không dấu, số hoặc dấu _.");
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) throws CinemaException {
        if (password == null || password.length() < 8 || password.length() > 72)
            throw error("VALIDATION", "Mật khẩu cần từ 8 đến 72 ký tự.");
    }

    @Override
    public Session register(String username, String displayName, String password)
            throws CinemaException {
        String name = normalize(username);
        validatePassword(password);
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 60)
            throw error("VALIDATION", "Họ tên cần từ 1 đến 60 ký tự.");
        User account = new User(name, displayName.trim(), PasswordHasher.hash(password));
        synchronized (store) {
            State current = store.current();
            if (current.users().containsKey(name))
                throw error("USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng.");
            Map<String, User> users = new HashMap<>(current.users());
            users.put(name, account);
            save(current.withUsers(users));
        }
        return createSession(account);
    }

    @Override
    public Session login(String username, String password) throws CinemaException {
        String name = normalize(username);
        validatePassword(password);
        User account;
        synchronized (store) {
            account = store.current().users().get(name);
        }
        if (account == null || !PasswordHasher.verify(password, account.passwordHash()))
            throw error("LOGIN", "Tên đăng nhập hoặc mật khẩu không đúng.");
        return createSession(account);
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
            callbacks.remove(token);
        }
    }

    @Override
    public List<Movie> movies(String token) throws CinemaException {
        user(token);
        synchronized (store) {
            return store.current().movies().values().stream()
                    .sorted(Comparator.comparing(Movie::id))
                    .toList();
        }
    }

    @Override
    public List<Showtime> showtimes(String token) throws CinemaException {
        user(token);
        synchronized (store) {
            return store.current().shows().values().stream()
                    .sorted(Comparator.comparing(Showtime::startsAt).thenComparing(Showtime::room))
                    .toList();
        }
    }

    private Showtime show(String id) throws CinemaException {
        Showtime show = id == null ? null : store.current().shows().get(id);
        if (show == null) throw error("NOT_FOUND", "Không tìm thấy suất chiếu.");
        return show;
    }

    private Set<String> occupied(String showId) {
        Set<String> seats = new HashSet<>();
        for (Booking booking : store.current().bookings().values())
            if (!booking.cancelled() && booking.showtimeId().equals(showId))
                seats.addAll(booking.seats());
        return seats;
    }

    @Override
    public SeatMap seatMap(String token, String showtimeId) throws CinemaException {
        user(token);
        synchronized (store) {
            return new SeatMap(show(showtimeId), occupied(showtimeId), store.current().revision());
        }
    }

    @Override
    public Booking book(String token, String showtimeId, List<String> requested, String requestId)
            throws CinemaException {
        String username = user(token);
        if (requested == null
                || requested.isEmpty()
                || requested.size() > 6
                || requested.stream().anyMatch(Objects::isNull))
            throw error("VALIDATION", "Mỗi đơn cần chọn từ 1 đến 6 ghế.");
        if (new HashSet<>(requested).size() != requested.size())
            throw error("VALIDATION", "Danh sách ghế bị trùng.");
        if (requestId == null || !requestId.matches("[A-Za-z0-9-]{8,64}"))
            throw error("VALIDATION", "Mã yêu cầu không hợp lệ.");
        List<String> seats = requested.stream().sorted().toList();
        Booking result;
        long revision;
        synchronized (store) {
            // Retry cùng requestId trả lại đơn cũ, tránh tạo hai đơn khi mất phản hồi mạng.
            for (Booking old : store.current().bookings().values()) {
                if (old.username().equals(username) && old.requestId().equals(requestId)) {
                    if (!old.showtimeId().equals(showtimeId) || !old.seats().equals(seats))
                        throw error("REQUEST_REUSED", "Mã yêu cầu đã được dùng cho nội dung khác.");
                    return old;
                }
            }
            Showtime show = show(showtimeId);
            if (!now().isBefore(show.startsAt().minusMinutes(5)))
                throw error("CLOSED", "Ngừng đặt vé từ 5 phút trước giờ chiếu.");
            Set<String> valid = new HashSet<>();
            for (int row = 0; row < show.rows(); row++)
                for (int col = 1; col <= show.columns(); col++)
                    valid.add("" + (char) ('A' + row) + col);
            if (!valid.containsAll(seats))
                throw error("VALIDATION", "Có ghế không tồn tại trong phòng.");
            Set<String> conflict = occupied(showtimeId);
            conflict.retainAll(seats);
            if (!conflict.isEmpty())
                throw error(
                        "SEAT_TAKEN",
                        "Ghế "
                                + String.join(", ", new TreeSet<>(conflict))
                                + " vừa được đặt. Vui lòng chọn lại.");
            result =
                    new Booking(
                            "VE-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT),
                            username,
                            showtimeId,
                            seats,
                            Math.multiplyExact(show.price(), seats.size()),
                            now(),
                            false,
                            requestId);
            Map<String, Booking> bookings = new HashMap<>(store.current().bookings());
            bookings.put(result.id(), result);
            // KIỂM TRA + TẠO ĐƠN + LƯU FILE cùng một khóa: không ai chen vào giữa.
            save(store.current().withBookings(bookings));
            revision = store.current().revision();
        }
        callbacks.publish(showtimeId, revision);
        System.out.println(
                "BOOK "
                        + result.id()
                        + " user="
                        + username
                        + " show="
                        + showtimeId
                        + " seats="
                        + seats);
        return result;
    }

    @Override
    public List<Booking> myBookings(String token) throws CinemaException {
        String username = user(token);
        synchronized (store) {
            return store.current().bookings().values().stream()
                    .filter(b -> b.username().equals(username))
                    .sorted(
                            Comparator.comparing(Booking::createdAt)
                                    .reversed()
                                    .thenComparing(Booking::id))
                    .toList();
        }
    }

    @Override
    public Booking cancel(String token, String bookingId) throws CinemaException {
        String username = user(token);
        Booking result;
        long revision;
        synchronized (store) {
            Booking old = bookingId == null ? null : store.current().bookings().get(bookingId);
            if (old == null || !old.username().equals(username))
                throw error("NOT_FOUND", "Không tìm thấy vé thuộc tài khoản này.");
            if (old.cancelled()) return old;
            if (!now().isBefore(show(old.showtimeId()).startsAt().minusMinutes(30)))
                throw error("CANCEL_CLOSED", "Chỉ được hủy khi còn hơn 30 phút trước giờ chiếu.");
            result = old.cancel();
            Map<String, Booking> bookings = new HashMap<>(store.current().bookings());
            bookings.put(result.id(), result);
            save(store.current().withBookings(bookings));
            revision = store.current().revision();
        }
        callbacks.publish(result.showtimeId(), revision);
        System.out.println("CANCEL " + result.id());
        return result;
    }

    @Override
    public void subscribe(String token, String showtimeId, SeatListener listener)
            throws CinemaException {
        user(token);
        synchronized (store) {
            show(showtimeId);
        }
        callbacks.subscribe(token, showtimeId, listener);
    }

    private void save(State state) throws CinemaException {
        try {
            store.commit(state);
        } catch (IOException e) {
            System.err.println("SAVE FAILED: " + e);
            throw error("STORAGE", "Không lưu được dữ liệu. Giao dịch chưa được ghi nhận.");
        }
    }

    @Override
    public void close() throws IOException {
        callbacks.close();
        store.close();
    }
}
