package vn.khoa.cinema.client;

import vn.khoa.cinema.common.Models.Session;

import java.awt.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import javax.swing.*;

final class LoginPanel extends JPanel {
    private final JTextField username = new JTextField(), displayName = new JTextField();
    private final JPasswordField password = new JPasswordField(), confirm = new JPasswordField();
    private final JLabel status = Ui.label("Đang tự tìm server trong mạng LAN…", 12, Ui.MUTED);
    private final JPanel form = Ui.column(Ui.WHITE);
    private final JButton submit = Ui.button("Đăng nhập", true),
            toggle = Ui.button("Tạo tài khoản mới", false);
    private final JLabel heading = Ui.title("Chào mừng trở lại", 26);
    private final BiConsumer<ClientConnection, Session> signedIn;
    private ClientConnection connection;
    private boolean registering, connecting, busy, active = true;
    private final Timer retry;

    LoginPanel(BiConsumer<ClientConnection, Session> signedIn) {
        this.signedIn = signedIn;
        setLayout(new GridBagLayout());
        setBackground(Ui.BG);
        JPanel shell = Ui.panel(new GridLayout(1, 2), Ui.WHITE);
        shell.setPreferredSize(new Dimension(930, 690));
        JPanel brand =
                new JPanel(new BorderLayout(0, 35)) {
                    @Override
                    protected void paintComponent(Graphics graphics) {
                        super.paintComponent(graphics);
                        Graphics2D g = (Graphics2D) graphics.create();
                        g.setColor(new Color(255, 255, 255, 12));
                        g.fillOval(-80, 220, 420, 420);
                        g.fillOval(160, 60, 320, 320);
                        g.dispose();
                    }
                };
        brand.setBackground(Ui.INK);
        brand.setBorder(Ui.padding(40));
        brand.add(Ui.label("C I N E M A   /   B O O K I N G", 16, Ui.WHITE), BorderLayout.NORTH);
        JPanel copy = Ui.column(Ui.INK);
        copy.setOpaque(false);
        JTextArea text =
                Ui.paragraph(
                        "Chọn bộ phim bạn thích, tìm suất chiếu phù hợp và dành một buổi tối cho"
                            + " màn ảnh rộng.");
        text.setForeground(new Color(0xC6D2D6));
        text.setMaximumSize(new Dimension(340, 100));
        copy.add(text);
        brand.add(copy, BorderLayout.CENTER);
        brand.add(
                Ui.label("Đặt vé mô phỏng  •  Không thanh toán tiền thật", 12, new Color(0xB7C9CC)),
                BorderLayout.SOUTH);
        JPanel right = Ui.panel(new BorderLayout(0, 18), Ui.WHITE);
        right.setBorder(Ui.padding(36));
        JPanel intro = Ui.column(Ui.WHITE);
        intro.add(heading);
        intro.add(Box.createVerticalStrut(12));
        intro.add(Ui.label("Tài khoản của bạn, trải nghiệm của bạn.", 13, Ui.MUTED));
        right.add(intro, BorderLayout.NORTH);
        right.add(form, BorderLayout.CENTER);
        JPanel footer = Ui.column(Ui.WHITE);
        status.setAlignmentX(0);
        footer.add(status);
        right.add(footer, BorderLayout.SOUTH);
        for (JTextField field : new JTextField[] {username, displayName, password, confirm})
            Ui.field(field);
        submit.addActionListener(e -> authenticate());
        password.addActionListener(e -> authenticate());
        confirm.addActionListener(e -> authenticate());
        toggle.addActionListener(
                e -> {
                    registering = !registering;
                    password.setText("");
                    confirm.setText("");
                    renderForm();
                });
        shell.add(brand);
        shell.add(right);
        add(shell);
        renderForm();
        retry = new Timer(5000, e -> connect());
        retry.start();
        connect();
    }

    private void renderForm() {
        form.removeAll();
        heading.setText(registering ? "Tạo tài khoản" : "Chào mừng trở lại");
        field("Tên đăng nhập", username);
        if (registering) field("Họ và tên", displayName);
        field("Mật khẩu", password);
        if (registering) field("Nhập lại mật khẩu", confirm);
        form.add(Box.createVerticalStrut(8));
        submit.setText(registering ? "Đăng ký và tiếp tục" : "Đăng nhập");
        toggle.setText(registering ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký");
        submit.setAlignmentX(0);
        toggle.setAlignmentX(0);
        form.add(submit);
        form.add(Box.createVerticalStrut(10));
        form.add(toggle);
        form.add(Box.createVerticalGlue());
        submit.setEnabled(connection != null && !busy);
        form.revalidate();
        form.repaint();
    }

    private void field(String text, JTextField field) {
        JLabel label = Ui.label(text, 13, Ui.MUTED);
        label.setAlignmentX(0);
        field.setAlignmentX(0);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        form.add(label);
        form.add(Box.createVerticalStrut(5));
        form.add(field);
        form.add(Box.createVerticalStrut(12));
    }

    private void connect() {
        if (!active || connecting || connection != null || busy) return;
        connecting = true;
        new SwingWorker<ClientConnection, Void>() {
            protected ClientConnection doInBackground() throws Exception {
                return ClientConnection.connect();
            }

            protected void done() {
                connecting = false;
                try {
                    ClientConnection found = get();
                    if (!active) {
                        found.close();
                        return;
                    }
                    connection = found;
                    status.setText("Đã kết nối • Sẵn sàng đăng nhập");
                    status.setForeground(Ui.ACCENT);
                    submit.setEnabled(true);
                } catch (Exception e) {
                    status.setText("Chưa thấy server • Tự thử lại sau vài giây");
                    status.setForeground(Ui.DANGER);
                }
            }
        }.execute();
    }

    private void authenticate() {
        if (busy || connection == null) return;
        String name = username.getText(),
                fullName = displayName.getText(),
                secret = new String(password.getPassword());
        if (registering && !secret.equals(new String(confirm.getPassword()))) {
            status.setText("Hai mật khẩu chưa trùng nhau.");
            return;
        }
        boolean register = registering;
        busy = true;
        submit.setEnabled(false);
        toggle.setEnabled(false);
        status.setText("Đang xác thực…");
        new SwingWorker<Session, Void>() {
            protected Session doInBackground() throws Exception {
                return register
                        ? connection.service.register(name, fullName, secret)
                        : connection.service.login(name, secret);
            }

            protected void done() {
                busy = false;
                toggle.setEnabled(true);
                try {
                    Session session = get();
                    active = false;
                    retry.stop();
                    password.setText("");
                    confirm.setText("");
                    signedIn.accept(connection, session);
                } catch (Exception e) {
                    Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
                    status.setText("Đăng nhập / đăng ký chưa thành công");
                    status.setForeground(Ui.DANGER);
                    JOptionPane.showMessageDialog(
                            LoginPanel.this,
                            cause.getMessage(),
                            "Thông báo",
                            JOptionPane.INFORMATION_MESSAGE);
                    if (cause instanceof java.rmi.RemoteException) {
                        connection.close();
                        connection = null;
                    }
                    submit.setEnabled(connection != null);
                }
            }
        }.execute();
    }

    void stop() {
        active = false;
        retry.stop();
        if (connection != null) connection.close();
    }
}
