package vn.khoa.cinema.client;

import vn.khoa.cinema.common.*;
import vn.khoa.cinema.common.Models.*;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.*;

/** Điều khiển giao diện. Lời gọi RMI luôn chạy trong SwingWorker, không làm treo EDT. */
public final class CinemaPanel extends JPanel {
    private final ClientConnection connection;
    private final Session session;
    private final Runnable logout;
    private final JPanel movieList = Ui.column(Ui.BG),
            details = Ui.panel(new BorderLayout(), Ui.BG),
            ticketList = Ui.column(Ui.BG),
            summaryHost = Ui.panel(new BorderLayout(), Ui.BG),
            pages = Ui.panel(new CardLayout(), Ui.BG);
    private final JLabel connectionStatus = Ui.label("Đã kết nối", 12, Ui.ACCENT),
            selectedText = Ui.label("Chưa chọn ghế", 14, Ui.INK),
            totalText = Ui.title("0 đ", 24),
            seatStatus = Ui.label("", 12, Ui.MUTED);
    private final JTextField search = new Ui.SearchField();
    private final JComboBox<LocalDate> dates = new JComboBox<>();
    private final JPanel seatsGrid = Ui.panel(new GridLayout(6, 8, 7, 9), Ui.WHITE);
    private final JButton bookButton = Ui.button("Xác nhận đặt vé", true),
            moviesTab = Ui.button("Đặt vé", true),
            ticketsTab = Ui.button("Vé của tôi", false);
    private final Map<String, JButton> showButtons = new LinkedHashMap<>();
    private final Map<String, JButton> seatButtons = new LinkedHashMap<>();
    private final Set<String> selected = new TreeSet<>();
    private List<Movie> movies = List.of();
    private List<Showtime> shows = List.of();
    private List<Booking> bookings = List.of();
    private Movie currentMovie;
    private Showtime currentShow;
    private SeatMap currentMap;
    private boolean stopped, refreshing, mutation, ticketsVisible, loadingDates;
    private int refreshCount, connectionFailures;
    private String requestId;
    private String requestFingerprint;
    private final javax.swing.Timer timer;

    public CinemaPanel(ClientConnection connection, Session session, Runnable logout) {
        this.connection = connection;
        this.session = session;
        this.logout = logout;
        setLayout(new BorderLayout(0, 18));
        setBackground(Ui.BG);
        setBorder(Ui.padding(22));
        JPanel header = Ui.panel(new BorderLayout(20, 0), Ui.BG);
        JPanel branding = Ui.column(Ui.BG);
        branding.add(Ui.title("CINEMA /", 27));
        branding.add(Ui.label("Một buổi xem phim bắt đầu từ đây.", 12, Ui.MUTED));
        header.add(branding, BorderLayout.WEST);
        JPanel nav = Ui.panel(new FlowLayout(FlowLayout.CENTER, 10, 0), Ui.BG);
        nav.add(moviesTab);
        nav.add(ticketsTab);
        header.add(nav, BorderLayout.CENTER);
        JPanel account = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 10, 0), Ui.BG);
        account.add(Ui.label("<html>" + Ui.html(session.displayName()) + "</html>", 14, Ui.INK));
        JButton signout = Ui.button("Đăng xuất", false);
        account.add(signout);
        header.add(account, BorderLayout.EAST);
        signout.addActionListener(
                e -> {
                    if (!mutation) logout.run();
                });
        moviesTab.addActionListener(e -> showPage(false));
        ticketsTab.addActionListener(
                e -> {
                    showPage(true);
                    loadTickets();
                });
        add(header, BorderLayout.NORTH);
        JPanel browse = Ui.panel(new BorderLayout(20, 0), Ui.BG);
        JPanel left = Ui.panel(new BorderLayout(0, 14), Ui.BG);
        left.setPreferredSize(new Dimension(360, 600));
        JPanel filters = Ui.column(Ui.BG);
        filters.add(Ui.title("Đang chiếu", 24));
        filters.add(Box.createVerticalStrut(12));
        Ui.field(search);
        search.setToolTipText("Tìm theo tên phim hoặc thể loại");
        filters.add(search);
        filters.add(Box.createVerticalStrut(10));
        dates.setRenderer(
                new DefaultListCellRenderer() {
                    public Component getListCellRendererComponent(
                            JList<?> list,
                            Object value,
                            int index,
                            boolean selected,
                            boolean focus) {
                        return super.getListCellRendererComponent(
                                list,
                                value instanceof LocalDate d
                                        ? d.format(
                                                DateTimeFormatter.ofPattern(
                                                        "EEEE, dd/MM",
                                                        Locale.forLanguageTag("vi-VN")))
                                        : "Chọn ngày",
                                index,
                                selected,
                                focus);
                    }
                });
        filters.add(dates);
        for (Component c : filters.getComponents())
            if (c instanceof JComponent jc) jc.setAlignmentX(0);
        left.add(filters, BorderLayout.NORTH);
        left.add(Ui.scroll(movieList), BorderLayout.CENTER);
        search.getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {
                            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                                renderMovies();
                            }

                            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                                renderMovies();
                            }

                            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                                renderMovies();
                            }
                        });
        dates.addActionListener(
                e -> {
                    if (!loadingDates) {
                        renderMovies();
                        if (currentMovie != null) selectMovie(currentMovie);
                    }
                });
        JPanel rightPane = Ui.panel(new BorderLayout(0, 14), Ui.BG);
        rightPane.add(Ui.scroll(details), BorderLayout.CENTER);
        rightPane.add(summaryHost, BorderLayout.SOUTH);
        browse.add(left, BorderLayout.WEST);
        browse.add(rightPane, BorderLayout.CENTER);
        pages.add(browse, "browse");
        pages.add(Ui.scroll(ticketList), "tickets");
        add(pages, BorderLayout.CENTER);
        JPanel footer = Ui.panel(new BorderLayout(), Ui.BG);
        footer.add(connectionStatus, BorderLayout.WEST);
        footer.add(
                Ui.label(
                        "Bản mô phỏng • Không thu tiền • Hủy trước giờ chiếu hơn 30 phút",
                        12,
                        Ui.MUTED),
                BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        bookButton.addActionListener(e -> book());
        connection.onSeatsChanged =
                (id, revision) ->
                        SwingUtilities.invokeLater(
                                () -> {
                                    if (!stopped
                                            && currentShow != null
                                            && currentShow.id().equals(id)) {
                                        connectionStatus.setText("Nhận cập nhật từ server");
                                        refreshSeats(false);
                                    }
                                });
        timer =
                new javax.swing.Timer(
                        2000,
                        e -> {
                            if (!mutation) {
                                if (ticketsVisible) loadTickets();
                                else refreshSeats(false);
                            }
                        });
        timer.start();
        loadCatalog();
    }

    private void showPage(boolean tickets) {
        ticketsVisible = tickets;
        ((CardLayout) pages.getLayout()).show(pages, tickets ? "tickets" : "browse");
        if (!tickets) refreshSeats(false);
    }

    private <T> void task(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        new SwingWorker<T, Void>() {
            protected T doInBackground() throws Exception {
                return work.call();
            }

            protected void done() {
                if (stopped) return;
                try {
                    success.accept(get());
                } catch (Exception e) {
                    failure.accept(e instanceof ExecutionException ? e.getCause() : e);
                }
            }
        }.execute();
    }

    private void error(Throwable error) {
        if (error instanceof CinemaException business) {
            JOptionPane.showMessageDialog(
                    this, business.getMessage(), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            if (business.code().equals("AUTH")) logout.run();
        } else {
            connectionStatus.setText("Mất kết nối • Đang thử cập nhật lại");
            connectionStatus.setForeground(Ui.DANGER);
            JOptionPane.showMessageDialog(
                    this,
                    "Không nhận được phản hồi từ server. Nếu vừa đặt vé, hãy kiểm tra Vé của tôi"
                        + " hoặc gửi lại cùng lựa chọn; hệ thống chống tạo đơn trùng.",
                    "Kết nối gián đoạn",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private record Catalog(List<Movie> movies, List<Showtime> shows) {}

    private void loadCatalog() {
        task(
                () ->
                        new Catalog(
                                connection.service.movies(session.token()),
                                connection.service.showtimes(session.token())),
                catalog -> {
                    movies = catalog.movies();
                    shows = catalog.shows();
                    loadingDates = true;
                    shows.stream()
                            .map(s -> s.startsAt().toLocalDate())
                            .distinct()
                            .filter(d -> !d.isBefore(LocalDate.now(Models.ZONE)))
                            .forEach(dates::addItem);
                    shows.stream()
                            .filter(
                                    show ->
                                            LocalDateTime.now(Models.ZONE)
                                                    .isBefore(show.startsAt().minusMinutes(5)))
                            .findFirst()
                            .ifPresent(
                                    show -> dates.setSelectedItem(show.startsAt().toLocalDate()));
                    loadingDates = false;
                    renderMovies();
                    if (!movies.isEmpty()) selectMovie(movies.getFirst());
                },
                this::error);
    }

    private List<Showtime> movieShows(Movie movie) {
        return shows.stream()
                .filter(
                        s ->
                                s.movieId().equals(movie.id())
                                        && s.startsAt()
                                                .toLocalDate()
                                                .equals(dates.getSelectedItem()))
                .toList();
    }

    private void renderMovies() {
        movieList.removeAll();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        for (Movie movie : movies) {
            if (!(movie.title() + " " + movie.genre()).toLowerCase(Locale.ROOT).contains(query))
                continue;
            JPanel card = Ui.card(new BorderLayout(14, 0));
            card.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(
                                    currentMovie != null && currentMovie.id().equals(movie.id())
                                            ? Ui.ACCENT
                                            : Ui.LINE),
                            Ui.padding(12)));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 183));
            card.setAlignmentX(0);
            card.add(new Poster(movie), BorderLayout.WEST);
            JPanel text = Ui.column(Ui.WHITE);
            text.add(Ui.label("<html><b>" + Ui.html(movie.title()) + "</b></html>", 17, Ui.INK));
            text.add(Box.createVerticalStrut(10));
            text.add(Ui.label(movie.genre(), 12, Ui.MUTED));
            text.add(Box.createVerticalStrut(5));
            text.add(Ui.label(movie.minutes() + " phút  /  " + movie.rating(), 12, Ui.MUTED));
            text.add(Box.createVerticalGlue());
            JButton choose = Ui.button("Xem suất chiếu", false);
            choose.setFont(Ui.BODY.deriveFont(12f));
            choose.addActionListener(
                    e -> {
                        if (!mutation) selectMovie(movie);
                    });
            text.add(choose);
            card.add(text, BorderLayout.CENTER);
            movieList.add(card);
            movieList.add(Box.createVerticalStrut(12));
        }
        if (movieList.getComponentCount() == 0)
            movieList.add(Ui.label("Không tìm thấy phim phù hợp.", 14, Ui.MUTED));
        movieList.revalidate();
        movieList.repaint();
    }

    private void selectMovie(Movie movie) {
        currentMovie = movie;
        currentShow = null;
        currentMap = null;
        selected.clear();
        seatButtons.clear();
        details.removeAll();
        showButtons.clear();
        JPanel content = Ui.column(Ui.BG);
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        JPanel intro = Ui.card(new BorderLayout(0, 12));
        intro.setAlignmentX(0);
        intro.setPreferredSize(new Dimension(650, 250));
        intro.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        intro.setBorder(Ui.padding(16));
        JPanel titles = Ui.column(Ui.WHITE);
        titles.add(Ui.label("LỰA CHỌN CỦA BẠN", 11, Ui.ACCENT));
        titles.add(Box.createVerticalStrut(8));
        titles.add(Ui.title(movie.title(), 24));
        titles.add(Box.createVerticalStrut(8));
        titles.add(
                Ui.label(
                        movie.genre()
                                + "   •   "
                                + movie.minutes()
                                + " phút   •   "
                                + movie.rating(),
                        13,
                        Ui.MUTED));
        intro.add(titles, BorderLayout.NORTH);
        JTextArea synopsis = Ui.paragraph(movie.description());
        synopsis.setRows(2);
        intro.add(synopsis, BorderLayout.CENTER);
        JPanel times = Ui.panel(new FlowLayout(FlowLayout.LEFT, 8, 4), Ui.WHITE);
        for (Showtime show : movieShows(movie)) {
            JButton button =
                    Ui.button(
                            show.startsAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                                    + " · "
                                    + show.room(),
                            false);
            button.setEnabled(
                    LocalDateTime.now(Models.ZONE).isBefore(show.startsAt().minusMinutes(5)));
            button.addActionListener(
                    e -> {
                        if (!mutation) selectShow(show);
                    });
            times.add(button);
            showButtons.put(show.id(), button);
        }
        if (times.getComponentCount() == 0)
            times.add(Ui.label("Không có suất chiếu trong ngày này.", 13, Ui.MUTED));
        intro.add(times, BorderLayout.SOUTH);
        content.add(intro);
        content.add(Box.createVerticalStrut(16));
        JPanel seatCard = Ui.card(new BorderLayout(0, 16));
        seatCard.setAlignmentX(0);
        seatCard.setPreferredSize(new Dimension(650, 340));
        seatCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        seatCard.setBorder(Ui.padding(16));
        JPanel screen = Ui.column(Ui.WHITE);
        JLabel screenLabel = Ui.label("M À N   H Ì N H", 12, Ui.MUTED);
        screenLabel.setAlignmentX(.5f);
        JPanel screenLine = Ui.panel(new FlowLayout(), Ui.PALE);
        screenLine.setMaximumSize(new Dimension(420, 5));
        screenLine.setPreferredSize(new Dimension(420, 5));
        screen.add(screenLine);
        screen.add(Box.createVerticalStrut(8));
        screen.add(screenLabel);
        screen.add(Box.createVerticalStrut(10));
        seatCard.add(screen, BorderLayout.NORTH);
        seatsGrid.removeAll();
        seatsGrid.setPreferredSize(new Dimension(500, 220));
        seatCard.add(seatsGrid, BorderLayout.CENTER);
        JPanel legend = Ui.column(Ui.WHITE);
        legend.add(Ui.label("Trắng: còn trống     Xanh: đang chọn     Xám: đã đặt", 12, Ui.MUTED));
        legend.add(Box.createVerticalStrut(8));
        legend.add(seatStatus);
        seatCard.add(legend, BorderLayout.SOUTH);
        content.add(seatCard);
        content.add(Box.createVerticalStrut(16));
        JPanel summary = Ui.card(new BorderLayout(10, 8));
        summary.setAlignmentX(0);
        summary.setPreferredSize(new Dimension(650, 125));
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
        summary.setBorder(Ui.padding(16));
        JPanel totals = Ui.column(Ui.WHITE);
        totals.add(selectedText);
        totals.add(Box.createVerticalStrut(6));
        totals.add(totalText);
        summary.add(totals, BorderLayout.WEST);
        summary.add(bookButton, BorderLayout.EAST);
        summary.add(
                Ui.label("Tối đa 6 ghế / đơn. Chọn ghế chưa phải giữ ghế.", 12, Ui.MUTED),
                BorderLayout.SOUTH);
        summaryHost.removeAll();
        summaryHost.add(summary);
        summaryHost.revalidate();
        summaryHost.repaint();
        details.add(content, BorderLayout.NORTH);
        updateSummary();
        List<Showtime> available =
                movieShows(movie).stream()
                        .filter(
                                s ->
                                        LocalDateTime.now(Models.ZONE)
                                                .isBefore(s.startsAt().minusMinutes(5)))
                        .toList();
        if (!available.isEmpty()) selectShow(available.getFirst());
        else seatStatus.setText("Hãy chọn ngày khác để xem các suất còn mở bán.");
        renderMovies();
        details.revalidate();
        details.repaint();
    }

    private void selectShow(Showtime show) {
        currentShow = show;
        showButtons.forEach(
                (id, button) -> {
                    Ui.SoftButton time = (Ui.SoftButton) button;
                    time.fill = id.equals(show.id()) ? Ui.ACCENT : Ui.WHITE;
                    time.setForeground(id.equals(show.id()) ? Ui.WHITE : Ui.INK);
                    time.repaint();
                });
        currentMap = null;
        selected.clear();
        seatButtons.clear();
        seatsGrid.removeAll();
        seatsGrid.setLayout(new GridLayout(show.rows(), show.columns(), 7, 9));
        for (int row = 0; row < show.rows(); row++)
            for (int col = 1; col <= show.columns(); col++) {
                String seat = "" + (char) ('A' + row) + col;
                Ui.SoftButton button = new Ui.SoftButton(seat, Ui.WHITE, Ui.INK);
                button.setBorder(BorderFactory.createEmptyBorder(8, 5, 8, 5));
                button.setEnabled(false);
                button.addActionListener(
                        e -> {
                            if (mutation || currentMap == null) return;
                            if (!selected.remove(seat)) {
                                if (selected.size() == 6) {
                                    JOptionPane.showMessageDialog(this, "Mỗi đơn tối đa 6 ghế.");
                                    return;
                                }
                                selected.add(seat);
                            }
                            paintSeats();
                            updateSummary();
                        });
                seatButtons.put(seat, button);
                seatsGrid.add(button);
            }
        seatStatus.setText(
                show.startsAt().format(DateTimeFormatter.ofPattern("dd/MM • HH:mm"))
                        + "  /  "
                        + show.room()
                        + "  /  "
                        + Ui.money(show.price())
                        + " mỗi ghế");
        updateSummary();
        seatsGrid.revalidate();
        seatsGrid.repaint();
        refreshSeats(true);
    }

    private void refreshSeats(boolean subscribe) {
        if (stopped || currentShow == null || refreshing || mutation) return;
        String showId = currentShow.id();
        refreshing = true;
        boolean renew = subscribe || ++refreshCount % 8 == 0;
        task(
                () -> {
                    if (renew)
                        connection.service.subscribe(session.token(), showId, connection.callback);
                    return connection.service.seatMap(session.token(), showId);
                },
                map -> {
                    refreshing = false;
                    if (currentShow == null || !currentShow.id().equals(showId)) {
                        refreshSeats(true);
                        return;
                    }
                    if (currentMap != null && map.revision() < currentMap.revision()) return;
                    connectionFailures = 0;
                    currentMap = map;
                    boolean changed = selected.removeAll(map.booked());
                    if (changed)
                        connectionStatus.setText(
                                "Có ghế vừa được người khác đặt; đã bỏ khỏi lựa chọn.");
                    else
                        connectionStatus.setText(
                                "Đã đồng bộ lúc "
                                        + LocalTime.now(Models.ZONE)
                                                .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    connectionStatus.setForeground(Ui.ACCENT);
                    paintSeats();
                    updateSummary();
                },
                failure -> {
                    refreshing = false;
                    connectionStatus.setText("Mất kết nối • Tự thử lại");
                    connectionStatus.setForeground(Ui.DANGER);
                    if (failure instanceof CinemaException ce && ce.code().equals("AUTH")) {
                        timer.stop();
                        error(failure);
                    } else if (++connectionFailures >= 3) {
                        timer.stop();
                        logout.run();
                    }
                });
    }

    private void paintSeats() {
        if (currentMap == null) return;
        boolean open =
                LocalDateTime.now(Models.ZONE)
                        .isBefore(currentMap.showtime().startsAt().minusMinutes(5));
        seatButtons.forEach(
                (name, button) -> {
                    Ui.SoftButton seat = (Ui.SoftButton) button;
                    boolean taken = currentMap.booked().contains(name);
                    seat.fill = selected.contains(name) ? Ui.ACCENT : Ui.WHITE;
                    seat.setForeground(selected.contains(name) ? Ui.WHITE : Ui.INK);
                    seat.setEnabled(!taken && !mutation && open);
                    seat.setToolTipText(taken ? "Đã được đặt" : "Ghế " + name);
                    seat.repaint();
                });
    }

    private void updateSummary() {
        selectedText.setText(
                selected.isEmpty() ? "Chưa chọn ghế" : "Ghế: " + String.join(", ", selected));
        totalText.setText(
                Ui.money(currentShow == null ? 0 : currentShow.price() * selected.size()));
        bookButton.setEnabled(
                !mutation
                        && currentMap != null
                        && !selected.isEmpty()
                        && LocalDateTime.now(Models.ZONE)
                                .isBefore(currentShow.startsAt().minusMinutes(5)));
    }

    private void book() {
        if (mutation || currentShow == null || selected.isEmpty()) return;
        Showtime show = currentShow;
        List<String> seats = List.copyOf(selected);
        String message =
                currentMovie.title()
                        + "\n"
                        + show.startsAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + " · "
                        + show.room()
                        + "\nGhế: "
                        + String.join(", ", seats)
                        + "\nTổng tiền mô phỏng: "
                        + Ui.money(show.price() * seats.size())
                        + "\n\nXác nhận đặt vé?";
        if (JOptionPane.showConfirmDialog(
                        this, message, "Kiểm tra thông tin đặt vé", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return;
        String fingerprint = show.id() + ":" + seats;
        if (!fingerprint.equals(requestFingerprint)) {
            requestId = UUID.randomUUID().toString();
            requestFingerprint = fingerprint;
        }
        String request = requestId;
        mutation = true;
        updateSummary();
        paintSeats();
        task(
                () -> connection.service.book(session.token(), show.id(), seats, request),
                booking -> {
                    mutation = false;
                    selected.clear();
                    requestId = null;
                    requestFingerprint = null;
                    JOptionPane.showMessageDialog(
                            this,
                            "Đặt vé thành công!\n"
                                    + booking.id()
                                    + "\nGhế: "
                                    + String.join(", ", booking.seats())
                                    + "\nXem lại tại mục Vé của tôi.",
                            "Vé của bạn",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshSeats(false);
                    updateSummary();
                },
                failure -> {
                    mutation = false;
                    if (failure instanceof CinemaException) {
                        requestId = null;
                        requestFingerprint = null;
                    }
                    error(failure);
                    refreshSeats(false);
                    paintSeats();
                    updateSummary();
                });
    }

    private boolean ticketsLoading;

    private void loadTickets() {
        if (ticketsLoading || stopped || mutation) return;
        ticketsLoading = true;
        task(
                () -> connection.service.myBookings(session.token()),
                result -> {
                    ticketsLoading = false;
                    connectionFailures = 0;
                    boolean changed =
                            !bookings.equals(result) || ticketList.getComponentCount() == 0;
                    bookings = result;
                    if (changed) renderTickets();
                    connectionStatus.setText("Vé đã được đồng bộ");
                    connectionStatus.setForeground(Ui.ACCENT);
                },
                failure -> {
                    ticketsLoading = false;
                    connectionStatus.setText("Chưa tải được vé • Đang thử lại");
                    if (failure instanceof CinemaException ce && ce.code().equals("AUTH")) {
                        timer.stop();
                        error(failure);
                    } else if (++connectionFailures >= 3) {
                        timer.stop();
                        logout.run();
                    }
                });
    }

    private void renderTickets() {
        ticketList.removeAll();
        JLabel heading = Ui.title("Vé của tôi", 28);
        heading.setAlignmentX(0);
        ticketList.add(heading);
        ticketList.add(Box.createVerticalStrut(18));
        if (bookings.isEmpty())
            ticketList.add(
                    Ui.label("Bạn chưa đặt vé. Hãy chọn một bộ phim để bắt đầu.", 16, Ui.MUTED));
        for (Booking booking : bookings) {
            Showtime show =
                    shows.stream()
                            .filter(s -> s.id().equals(booking.showtimeId()))
                            .findFirst()
                            .orElse(null);
            if (show == null) continue;
            Movie movie =
                    movies.stream()
                            .filter(m -> m.id().equals(show.movieId()))
                            .findFirst()
                            .orElseThrow();
            JPanel card = Ui.card(new BorderLayout(20, 10));
            card.setAlignmentX(0);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
            JPanel info = Ui.column(Ui.WHITE);
            info.add(Ui.title(movie.title(), 21));
            info.add(Box.createVerticalStrut(8));
            info.add(
                    Ui.label(
                            show.startsAt()
                                            .format(
                                                    DateTimeFormatter.ofPattern(
                                                            "dd/MM/yyyy • HH:mm"))
                                    + "  /  "
                                    + show.room()
                                    + "  /  Ghế "
                                    + String.join(", ", booking.seats()),
                            14,
                            Ui.MUTED));
            info.add(Box.createVerticalStrut(8));
            JTextField code = new JTextField(booking.id());
            code.setEditable(false);
            code.setBorder(null);
            code.setBackground(Ui.WHITE);
            code.setForeground(Ui.MUTED);
            code.setFont(Ui.BODY.deriveFont(12f));
            info.add(code);
            info.add(Box.createVerticalStrut(8));
            info.add(
                    Ui.label(
                            booking.cancelled()
                                    ? "ĐÃ HỦY"
                                    : LocalDateTime.now(Models.ZONE).isBefore(show.startsAt())
                                            ? "ĐÃ XÁC NHẬN"
                                            : "ĐÃ QUA SUẤT CHIẾU",
                            12,
                            booking.cancelled() ? Ui.DANGER : Ui.ACCENT));
            card.add(info, BorderLayout.CENTER);
            JPanel action = Ui.column(Ui.WHITE);
            action.add(Ui.title(Ui.money(booking.total()), 22));
            action.add(Box.createVerticalStrut(12));
            JButton cancel = Ui.button("Hủy vé", false);
            cancel.setEnabled(
                    !booking.cancelled()
                            && LocalDateTime.now(Models.ZONE)
                                    .isBefore(show.startsAt().minusMinutes(30)));
            cancel.addActionListener(e -> cancel(booking));
            action.add(cancel);
            card.add(action, BorderLayout.EAST);
            ticketList.add(card);
            ticketList.add(Box.createVerticalStrut(12));
        }
        ticketList.revalidate();
        ticketList.repaint();
    }

    private void cancel(Booking booking) {
        if (mutation
                || JOptionPane.showConfirmDialog(
                                this,
                                "Hủy toàn bộ đơn gồm ghế "
                                        + String.join(", ", booking.seats())
                                        + "?",
                                "Hủy vé",
                                JOptionPane.YES_NO_OPTION)
                        != JOptionPane.YES_OPTION) return;
        mutation = true;
        task(
                () -> connection.service.cancel(session.token(), booking.id()),
                result -> {
                    mutation = false;
                    bookings = List.of();
                    loadTickets();
                },
                failure -> {
                    mutation = false;
                    error(failure);
                });
    }

    public void stop() {
        stopped = true;
        timer.stop();
        connection.onSeatsChanged = (id, revision) -> {};
    }
}
