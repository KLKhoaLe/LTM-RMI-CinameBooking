package vn.khoa.cinema.common;

import vn.khoa.cinema.common.Models.*;

import java.rmi.*;
import java.util.List;

/** Hợp đồng giữa hai project. Mọi kiểm tra nghiệp vụ được thực hiện ở server. */
public interface CinemaService extends Remote {
    String NAME = "CinemaBooking";

    String ping() throws RemoteException;

    Session register(String username, String displayName, String password)
            throws RemoteException, CinemaException;

    Session login(String username, String password) throws RemoteException, CinemaException;

    void logout(String token) throws RemoteException;

    List<Movie> movies(String token) throws RemoteException, CinemaException;

    List<Showtime> showtimes(String token) throws RemoteException, CinemaException;

    SeatMap seatMap(String token, String showtimeId) throws RemoteException, CinemaException;

    Booking book(String token, String showtimeId, List<String> seats, String requestId)
            throws RemoteException, CinemaException;

    List<Booking> myBookings(String token) throws RemoteException, CinemaException;

    Booking cancel(String token, String bookingId) throws RemoteException, CinemaException;

    void subscribe(String token, String showtimeId, SeatListener listener)
            throws RemoteException, CinemaException;
}
