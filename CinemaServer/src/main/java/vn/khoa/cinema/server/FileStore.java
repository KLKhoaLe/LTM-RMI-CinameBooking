package vn.khoa.cinema.server;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Clock;

/** Một server / một file. FileLock ngăn hai server cùng ghi một bộ dữ liệu. */
final class FileStore implements AutoCloseable {
    private final Path file;
    private final FileChannel lockChannel;
    private final FileLock fileLock;
    private State state;

    FileStore(Path directory, Clock clock) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("cinema.bin");
        lockChannel =
                FileChannel.open(
                        directory.resolve("server.lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lockChannel.close();
            throw new IOException("Bộ dữ liệu đang được server khác sử dụng.", e);
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IOException("Bộ dữ liệu đang được server khác sử dụng.");
        }
        fileLock = acquired;
        try {
            if (Files.exists(file)) {
                try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(file))) {
                    input.setObjectInputFilter(
                            ObjectInputFilter.Config.createFilter(
                                    "maxdepth=30;maxrefs=1000000;maxbytes=50000000;vn.khoa.cinema.**;java.base/*;!*"));
                    state = (State) input.readObject();
                } catch (ClassNotFoundException | ClassCastException e) {
                    throw new IOException("File dữ liệu không hợp lệ.", e);
                }
            } else commit(SeedData.create(clock));
            // Mỗi lần khởi động bổ sung lịch 7 ngày tới, giữ nguyên vé và lịch cũ.
            State fresh = SeedData.create(clock);
            var shows = new java.util.HashMap<>(state.shows());
            fresh.shows().forEach(shows::putIfAbsent);
            if (shows.size() != state.shows().size())
                commit(
                        new State(
                                state.users(),
                                state.movies(),
                                shows,
                                state.bookings(),
                                state.revision()));
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    State current() {
        return state;
    } // Chỉ truy cập trong synchronized(store).

    void commit(State next) throws IOException {
        Path temporary = file.resolveSibling("cinema.tmp");
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile());
                    ObjectOutputStream output = new ObjectOutputStream(stream)) {
                output.writeObject(next);
                output.flush();
                stream.getFD().sync();
            }
            // Không fallback sang ghi đè không nguyên tử: lỗi lưu phải giữ nguyên trạng thái cũ.
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            state = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void close() throws IOException {
        fileLock.release();
        lockChannel.close();
    }
}
