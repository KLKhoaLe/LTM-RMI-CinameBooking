package vn.khoa.cinema.server;

import vn.khoa.cinema.common.SeatListener;

import java.util.concurrent.*;

/** Callback chạy ngoài khóa đặt vé. Client mất mạng không được chặn giao dịch của người khác. */
final class CallbackHub implements AutoCloseable {
    private record Watch(String showtimeId, SeatListener listener) {}

    private final ConcurrentMap<String, Watch> watchers = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor workers =
            new ThreadPoolExecutor(
                    2,
                    4,
                    30,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(128),
                    Thread.ofPlatform().daemon().name("callback-", 0).factory(),
                    new ThreadPoolExecutor.DiscardPolicy());

    void subscribe(String token, String showtimeId, SeatListener listener) {
        if (listener == null) watchers.remove(token);
        else watchers.put(token, new Watch(showtimeId, listener));
    }

    void remove(String token) {
        watchers.remove(token);
    }

    void publish(String showtimeId, long revision) {
        watchers.forEach(
                (token, watch) -> {
                    if (!watch.showtimeId().equals(showtimeId)) return;
                    workers.execute(
                            () -> {
                                try {
                                    watch.listener().seatsChanged(showtimeId, revision);
                                } catch (Exception e) {
                                    watchers.remove(token, watch);
                                    System.out.println(
                                            "CALLBACK unreachable; client can refresh by polling.");
                                }
                            });
                });
    }

    @Override
    public void close() {
        workers.shutdownNow();
        watchers.clear();
    }
}
