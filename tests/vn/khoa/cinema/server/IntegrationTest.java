package vn.khoa.cinema.server;

import vn.khoa.cinema.common.*;
import vn.khoa.cinema.common.Models.*;

import java.io.*;
import java.net.ServerSocket;
import java.nio.file.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Chạy qua stub thật (TCP/serialization), không gọi thẳng implementation để test nghiệp vụ. */
public final class IntegrationTest {
    private static int passed;

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }

    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    private static void rejects(String code, Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + code);
        } catch (CinemaException e) {
            check(e.code().equals(code), "reject " + code);
        }
    }

    private static final class TestClock extends Clock {
        Instant instant = Instant.parse("2030-05-15T01:00:00Z");

        public ZoneId getZone() {
            return Models.ZONE;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return instant;
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        Path data = Files.createTempDirectory("cinema-test-");
        TestClock clock = new TestClock();
        CinemaServiceImpl implementation = new CinemaServiceImpl(data, clock);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Registry registry = LocateRegistry.createRegistry(port);
        CinemaService exported =
                (CinemaService)
                        UnicastRemoteObject.exportObject(
                                implementation,
                                0,
                                new TimeoutSocketFactory(7000),
                                null,
                                RpcFilters.incoming());
        registry.rebind(CinemaService.NAME, exported);
        CinemaService api =
                (CinemaService)
                        LocateRegistry.getRegistry("127.0.0.1", port).lookup(CinemaService.NAME);
        SeatListener callbackObject = null;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            check(api.ping().equals("CINEMA_RMI_V1"), "RMI lookup + ping");
            rejects("AUTH", () -> api.movies("invalid"));
            Session a = api.register("alice", "Alice", "Password123");
            Session b = api.register("bob", "Bob", "Password456");
            rejects("USERNAME_TAKEN", () -> api.register("ALICE", "Another", "Password123"));
            rejects("VALIDATION", () -> api.register("xx", "Test", "short"));
            rejects("LOGIN", () -> api.login("alice", "WrongPassword"));
            check(api.login("ALICE", "Password123").username().equals("alice"), "normalized login");
            check(
                    api.movies(a.token()).size() == 6 && api.showtimes(a.token()).size() == 84,
                    "6 movies / 84 shows");
            Showtime show = api.showtimes(a.token()).get(20);
            String id = show.id();
            rejects(
                    "VALIDATION",
                    () ->
                            api.book(
                                    a.token(),
                                    id,
                                    List.of("A1", "A1"),
                                    UUID.randomUUID().toString()));
            rejects(
                    "VALIDATION",
                    () -> api.book(a.token(), id, List.of("Z99"), UUID.randomUUID().toString()));
            rejects(
                    "VALIDATION",
                    () ->
                            api.book(
                                    a.token(),
                                    id,
                                    List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7"),
                                    UUID.randomUUID().toString()));
            CountDownLatch notified = new CountDownLatch(1);
            callbackObject =
                    (changed, revision) -> {
                        if (changed.equals(id)) notified.countDown();
                    };
            SeatListener callback =
                    (SeatListener)
                            UnicastRemoteObject.exportObject(
                                    callbackObject, 0, new TimeoutSocketFactory(2000), null);
            api.subscribe(b.token(), id, callback);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger wins = new AtomicInteger(), conflicts = new AtomicInteger();
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String token = i % 2 == 0 ? a.token() : b.token();
                calls.add(
                        pool.submit(
                                () -> {
                                    try {
                                        start.await();
                                        api.book(
                                                token,
                                                id,
                                                List.of("A1"),
                                                UUID.randomUUID().toString());
                                        wins.incrementAndGet();
                                    } catch (CinemaException e) {
                                        if (e.code().equals("SEAT_TAKEN"))
                                            conflicts.incrementAndGet();
                                        else throw new RuntimeException(e);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }));
            }
            start.countDown();
            for (Future<?> call : calls) call.get(20, TimeUnit.SECONDS);
            check(
                    wins.get() == 1 && conflicts.get() == 19,
                    "20 simultaneous RMI requests: exactly one winner");
            check(notified.await(4, TimeUnit.SECONDS), "real RMI callback received");
            rejects(
                    "SEAT_TAKEN",
                    () ->
                            api.book(
                                    a.token(),
                                    id,
                                    List.of("A1", "A2"),
                                    UUID.randomUUID().toString()));
            check(
                    !api.seatMap(a.token(), id).booked().contains("A2"),
                    "failed multi-seat booking leaves free seats untouched");
            String request = UUID.randomUUID().toString();
            Booking order = api.book(a.token(), id, List.of("B2", "B1"), request);
            Booking retry = api.book(a.token(), id, List.of("B1", "B2"), request);
            check(
                    order.id().equals(retry.id()) && order.total() == show.price() * 2,
                    "idempotent retry + server price + sorted seats");
            rejects("REQUEST_REUSED", () -> api.book(a.token(), id, List.of("B3"), request));
            rejects("NOT_FOUND", () -> api.cancel(b.token(), order.id()));
            check(
                    api.myBookings(b.token()).stream().noneMatch(x -> x.id().equals(order.id())),
                    "booking privacy");
            api.cancel(a.token(), order.id());
            check(!api.seatMap(a.token(), id).booked().contains("B1"), "cancel releases seats");
            check(api.cancel(a.token(), order.id()).cancelled(), "cancel retry is idempotent");
            check(
                    api.book(a.token(), id, List.of("B1", "B2"), request).cancelled(),
                    "old request cannot resurrect cancelled booking");
            api.book(b.token(), id, List.of("B1"), UUID.randomUUID().toString());
            check(
                    api.seatMap(a.token(), id).booked().contains("B1"),
                    "released seat can be rebooked");
            Showtime other = api.showtimes(a.token()).get(21);
            api.book(a.token(), other.id(), List.of("A1"), UUID.randomUUID().toString());
            check(
                    api.seatMap(a.token(), other.id()).booked().contains("A1"),
                    "same seat label in different show is independent");
            Files.createDirectory(data.resolve("cinema.tmp"));
            rejects(
                    "STORAGE",
                    () -> api.book(a.token(), id, List.of("F8"), UUID.randomUUID().toString()));
            check(
                    !api.seatMap(a.token(), id).booked().contains("F8"),
                    "failed disk write rolls back in-memory transaction");
            // FileStore cleanup may remove the empty test directory.
            Files.deleteIfExists(data.resolve("cinema.tmp"));
            try {
                new CinemaServiceImpl(data, clock);
                throw new AssertionError("second server opened");
            } catch (IOException expected) {
                check(true, "exclusive data directory lock");
            }
            // Hai process Java riêng cùng tranh chấp: không chỉ thread trong một JVM.
            long go = System.currentTimeMillis() + 1800;
            String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String cp = System.getProperty("java.class.path");
            Process p1 =
                    new ProcessBuilder(
                                    javaCommand,
                                    "-cp",
                                    cp,
                                    RaceClient.class.getName(),
                                    "" + port,
                                    a.token(),
                                    id,
                                    "" + go)
                            .redirectErrorStream(true)
                            .start();
            Process p2 =
                    new ProcessBuilder(
                                    javaCommand,
                                    "-cp",
                                    cp,
                                    RaceClient.class.getName(),
                                    "" + port,
                                    b.token(),
                                    id,
                                    "" + go)
                            .redirectErrorStream(true)
                            .start();
            boolean finished1 = p1.waitFor(15, TimeUnit.SECONDS),
                    finished2 = p2.waitFor(15, TimeUnit.SECONDS);
            String out1 = new String(p1.getInputStream().readAllBytes()),
                    out2 = new String(p2.getInputStream().readAllBytes());
            check(
                    finished1
                            && finished2
                            && ((out1.contains("WIN") && out2.contains("CONFLICT"))
                                    || (out2.contains("WIN") && out1.contains("CONFLICT"))),
                    "two separate Java clients: one win, one conflict");
            // Biên thời gian: đóng bán trước 5 phút, hủy trước 30 phút.
            Showtime early = api.showtimes(a.token()).getFirst();
            Booking earlyOrder =
                    api.book(a.token(), early.id(), List.of("E1"), UUID.randomUUID().toString());
            clock.instant = early.startsAt().minusMinutes(30).atZone(Models.ZONE).toInstant();
            rejects("CANCEL_CLOSED", () -> api.cancel(a.token(), earlyOrder.id()));
            clock.instant = early.startsAt().minusMinutes(5).atZone(Models.ZONE).toInstant();
            rejects(
                    "CLOSED",
                    () ->
                            api.book(
                                    a.token(),
                                    early.id(),
                                    List.of("E2"),
                                    UUID.randomUUID().toString()));
            api.logout(b.token());
            rejects("AUTH", () -> api.myBookings(b.token()));
            clock.instant = clock.instant.plus(Duration.ofHours(9));
            rejects("AUTH", () -> api.myBookings(a.token()));
            UnicastRemoteObject.unexportObject(implementation, true);
            implementation.close();
            implementation = new CinemaServiceImpl(data, clock);
            CinemaService restarted =
                    (CinemaService) UnicastRemoteObject.exportObject(implementation, 0);
            Session persisted = restarted.login("alice", "Password123");
            check(
                    restarted.myBookings(persisted.token()).stream()
                            .anyMatch(x -> x.id().equals(order.id()) && x.cancelled()),
                    "users / bookings / cancellations survive restart");
            check(
                    restarted
                            .book(persisted.token(), id, List.of("B1", "B2"), request)
                            .id()
                            .equals(order.id()),
                    "request deduplication survives restart");
            String fileBytes =
                    new String(
                            Files.readAllBytes(data.resolve("cinema.bin")),
                            java.nio.charset.StandardCharsets.ISO_8859_1);
            check(
                    !fileBytes.contains("Password123") && !fileBytes.contains("Khoa@123"),
                    "no plaintext passwords in persistence");
            System.out.println("ALL " + passed + " CHECKS PASSED");
        } finally {
            pool.shutdownNow();
            if (callbackObject != null) UnicastRemoteObject.unexportObject(callbackObject, true);
            UnicastRemoteObject.unexportObject(implementation, true);
            implementation.close();
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }
}
