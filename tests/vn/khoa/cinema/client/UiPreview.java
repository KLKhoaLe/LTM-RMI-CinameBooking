package vn.khoa.cinema.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;

import javax.imageio.ImageIO;
import javax.swing.*;

/** Render chính các panel Swing, dùng dữ liệu RMI thật, không phải mockup HTML. */
public final class UiPreview {
    private static CinemaPanel cinema;
    private static LoginPanel login;

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }

    private static void save(JComponent panel, String path) throws Exception {
        panel.setSize(1280, 900);
        layout(panel);
        layout(panel);
        BufferedImage image = new BufferedImage(1280, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        panel.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File(path));
    }

    public static void main(String[] args) throws Exception {
        File folder = new File(args.length == 0 ? "docs/previews" : args[0]);
        folder.mkdirs();
        SwingUtilities.invokeAndWait(
                () -> {
                    Ui.setup();
                    login = new LoginPanel((c, s) -> {});
                });
        Thread.sleep(3000);
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        save(login, new File(folder, "01-login.png").toString());
                        Field toggle = LoginPanel.class.getDeclaredField("toggle");
                        toggle.setAccessible(true);
                        ((JButton) toggle.get(login)).doClick();
                        save(login, new File(folder, "01b-register.png").toString());
                        login.stop();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        ClientConnection connection = ClientConnection.connect();
        var session = connection.service.login("khoa", "Khoa@123");
        SwingUtilities.invokeAndWait(
                () -> {
                    cinema = new CinemaPanel(connection, session, () -> {});
                    cinema.setSize(1280, 900);
                    layout(cinema);
                });
        Thread.sleep(1300);
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        Field field = CinemaPanel.class.getDeclaredField("dates");
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        JComboBox<LocalDate> dates = (JComboBox<LocalDate>) field.get(cinema);
                        if (dates.getItemCount() > 1) dates.setSelectedIndex(1);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        Thread.sleep(1300);
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        Field buttonsField = CinemaPanel.class.getDeclaredField("seatButtons");
                        buttonsField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, JButton> buttons =
                                (java.util.Map<String, JButton>) buttonsField.get(cinema);
                        buttons.get("E4").doClick();
                        buttons.get("E5").doClick();
                        save(cinema, new File(folder, "02-booking.png").toString());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        var show =
                connection.service.showtimes(session.token()).stream()
                        .filter(
                                x ->
                                        x.startsAt()
                                                .toLocalDate()
                                                .equals(
                                                        LocalDate.now(
                                                                        vn.khoa.cinema.common.Models
                                                                                .ZONE)
                                                                .plusDays(1)))
                        .findFirst()
                        .orElseThrow();
        connection.service.book(
                session.token(),
                show.id(),
                java.util.List.of("F6", "F7"),
                java.util.UUID.randomUUID().toString());
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        Field field = CinemaPanel.class.getDeclaredField("ticketsTab");
                        field.setAccessible(true);
                        ((JButton) field.get(cinema)).doClick();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        save(cinema, new File(folder, "03-tickets.png").toString());
                        cinema.stop();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        connection.service.logout(session.token());
        connection.close();
        System.out.println("UI previews written to " + folder.getAbsolutePath());
        System.exit(0);
    }
}
