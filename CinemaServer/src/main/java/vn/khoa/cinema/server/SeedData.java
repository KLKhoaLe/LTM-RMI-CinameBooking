package vn.khoa.cinema.server;

import vn.khoa.cinema.common.Models.*;

import java.time.*;
import java.util.*;

/** Phim hư cấu để demo, lịch 7 ngày, 3 phòng. Không phụ thuộc API hoặc Internet. */
final class SeedData {
    static State create(Clock clock) {
        Map<String, Movie> movies = new LinkedHashMap<>();
        add(
                movies,
                "M01",
                "Phía Sau Bình Minh",
                "Phiêu lưu",
                115,
                "T13",
                "Một chuyến tàu cuối cùng đưa ba người bạn đi tìm ngọn hải đăng giữa những miền ký"
                    + " ức.",
                "#3B6869");
        add(
                movies,
                "M02",
                "Mật Mã Thành Phố",
                "Trinh thám",
                125,
                "T16",
                "Một kỹ sư trẻ lần theo chuỗi tín hiệu bí ẩn, khám phá câu chuyện bị lãng quên của"
                    + " thành phố.",
                "#515B79");
        add(
                movies,
                "M03",
                "Nhà Có Bốn Mùa",
                "Gia đình",
                105,
                "P",
                "Bốn thế hệ cùng trở về căn nhà cũ và học cách lắng nghe những điều giản dị.",
                "#947653");
        add(
                movies,
                "M04",
                "Hành Tinh Nhỏ",
                "Hoạt hình",
                95,
                "P",
                "Robot giao thư và chú mèo máy bắt đầu hành trình gieo lại những khu rừng ngoài"
                    + " không gian.",
                "#66855E");
        add(
                movies,
                "M05",
                "Đêm Không Tiếng Động",
                "Hồi hộp",
                110,
                "T18",
                "Trong một đài phát thanh biệt lập, tín hiệu lúc nửa đêm dẫn đến những lựa chọn"
                    + " không thể quay lại.",
                "#665875");
        add(
                movies,
                "M06",
                "Ngày Mai Gặp Lại",
                "Tình cảm",
                120,
                "T13",
                "Hai người xa lạ gặp nhau tại một hiệu sách nhỏ, cùng viết tiếp những dự định còn"
                    + " dang dở.",
                "#A16F69");
        Map<String, Showtime> shows = new LinkedHashMap<>();
        LocalDate first = LocalDate.now(clock);
        for (int day = 0; day < 7; day++)
            for (int room = 1; room <= 3; room++)
                for (int slot = 0; slot < 4; slot++) {
                    LocalDate date = first.plusDays(day);
                    String id = date + "-P" + room + "-" + slot;
                    String movieId = "M%02d".formatted(1 + (room * 2 + slot + day) % 6);
                    int hour = new int[] {9, 13, 17, 21}[slot];
                    long price = date.getDayOfWeek().getValue() >= 6 ? 90000 : 75000;
                    shows.put(
                            id,
                            new Showtime(
                                    id,
                                    movieId,
                                    "Phòng 0" + room,
                                    date.atTime(hour, 0),
                                    6,
                                    8,
                                    price));
                }
        Map<String, User> users = new HashMap<>();
        users.put("khoa", new User("khoa", "Lê Văn Khoa", PasswordHasher.hash("Khoa@123")));
        users.put("linh", new User("linh", "Nguyễn Linh", PasswordHasher.hash("Linh@123")));
        Map<String, Booking> bookings = new HashMap<>();
        int index = 0;
        for (Showtime show : shows.values()) {
            // Một số ghế đặt mẫu có chủ sở hữu thật; không giả màu ghế trong giao diện.
            String id = "DEMO-%03d".formatted(++index);
            List<String> seats = index % 2 == 0 ? List.of("C4", "C5") : List.of("D3", "D4", "D5");
            bookings.put(
                    id,
                    new Booking(
                            id,
                            "linh",
                            show.id(),
                            seats,
                            show.price() * seats.size(),
                            first.atStartOfDay(),
                            false,
                            id));
        }
        return new State(users, movies, shows, bookings, 1);
    }

    private static void add(
            Map<String, Movie> movies,
            String id,
            String title,
            String genre,
            int minutes,
            String rating,
            String description,
            String color) {
        movies.put(id, new Movie(id, title, genre, minutes, rating, description, color));
    }
}
