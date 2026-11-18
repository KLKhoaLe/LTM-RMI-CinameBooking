package vn.khoa.cinema.common;

import java.io.Serializable;
import java.time.*;
import java.util.*;

/** DTO bất biến: RMI truyền bản sao dữ liệu, không truyền đối tượng lưu trữ của server. */
public final class Models {
    private Models() {}

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public record Movie(
            String id,
            String title,
            String genre,
            int minutes,
            String rating,
            String description,
            String color)
            implements Serializable {}

    public record Showtime(
            String id,
            String movieId,
            String room,
            LocalDateTime startsAt,
            int rows,
            int columns,
            long price)
            implements Serializable {}

    public record Session(String token, String username, String displayName)
            implements Serializable {}

    public record SeatMap(Showtime showtime, Set<String> booked, long revision)
            implements Serializable {
        public SeatMap {
            booked = Set.copyOf(booked);
        }
    }

    public record Booking(
            String id,
            String username,
            String showtimeId,
            List<String> seats,
            long total,
            LocalDateTime createdAt,
            boolean cancelled,
            String requestId)
            implements Serializable {
        public Booking {
            seats = List.copyOf(seats);
        }

        public Booking cancel() {
            return new Booking(id, username, showtimeId, seats, total, createdAt, true, requestId);
        }
    }
}
