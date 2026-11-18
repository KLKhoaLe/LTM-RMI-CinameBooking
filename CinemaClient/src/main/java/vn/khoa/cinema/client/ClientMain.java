package vn.khoa.cinema.client;

import vn.khoa.cinema.common.Models.Session;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

public final class ClientMain {
    private final JFrame frame = new JFrame("Cinema • Đặt vé xem phim");
    private ClientConnection connection;
    private Session session;
    private CinemaPanel cinema;
    private LoginPanel login;

    private ClientMain() {
        Ui.setup();
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(Math.min(1280, screen.width - 40), Math.min(900, screen.height - 60));
        frame.setMinimumSize(
                new Dimension(
                        Math.min(1100, screen.width - 40), Math.min(760, screen.height - 60)));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        cleanup();
                        frame.dispose();
                        System.exit(0);
                    }
                });
        showLogin();
        frame.setVisible(true);
    }

    private void showLogin() {
        login = new LoginPanel(this::signedIn);
        frame.setContentPane(login);
        frame.revalidate();
    }

    private void signedIn(ClientConnection connection, Session session) {
        this.connection = connection;
        this.session = session;
        cinema = new CinemaPanel(connection, session, this::logout);
        frame.setContentPane(cinema);
        frame.revalidate();
    }

    private void logout() {
        cleanup();
        showLogin();
    }

    private void cleanup() {
        if (cinema != null) {
            cinema.stop();
            cinema = null;
        }
        if (login != null) {
            login.stop();
            login = null;
        }
        ClientConnection old = connection;
        Session oldSession = session;
        connection = null;
        session = null;
        if (old != null) {
            Thread.ofPlatform()
                    .daemon()
                    .start(
                            () -> {
                                try {
                                    if (oldSession != null) old.service.logout(oldSession.token());
                                } catch (Exception ignored) {
                                } finally {
                                    old.close();
                                }
                            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientMain::new);
    }
}
