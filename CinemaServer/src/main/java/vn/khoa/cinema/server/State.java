package vn.khoa.cinema.server;

import vn.khoa.cinema.common.Models.*;

import java.io.Serializable;
import java.util.*;

/** Snapshot bất biến; chỉ thay snapshot hiện tại sau khi lưu file thành công. */
record State(
        Map<String, User> users,
        Map<String, Movie> movies,
        Map<String, Showtime> shows,
        Map<String, Booking> bookings,
        long revision)
        implements Serializable {
    State {
        users = Map.copyOf(users);
        movies = Map.copyOf(movies);
        shows = Map.copyOf(shows);
        bookings = Map.copyOf(bookings);
    }

    State withUsers(Map<String, User> updated) {
        return new State(updated, movies, shows, bookings, revision);
    }

    State withBookings(Map<String, Booking> updated) {
        return new State(users, movies, shows, updated, revision + 1);
    }
}

record User(String username, String displayName, String passwordHash) implements Serializable {}
