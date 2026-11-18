package vn.khoa.cinema.client;

import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

import javax.swing.*;
import javax.swing.border.*;

/** Các thành phần giao diện dùng lại. Không chứa nghiệp vụ hoặc lời gọi RMI. */
final class Ui {
    static final Color BG = new Color(0xF3F5F6),
            WHITE = Color.WHITE,
            INK = new Color(0x182D38),
            MUTED = new Color(0x6E7E86),
            LINE = new Color(0xDFE5E8),
            ACCENT = new Color(0x237D77),
            PALE = new Color(0xE4F0ED),
            DANGER = new Color(0xA94D4D);
    static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);

    static void setup() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        for (Object key : UIManager.getDefaults().keySet().toArray())
            if (key.toString().endsWith(".font")) UIManager.put(key, BODY);
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", BG);
        UIManager.put("TextField.background", WHITE);
        UIManager.put("PasswordField.background", WHITE);
        UIManager.put("ComboBox.background", WHITE);
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("ScrollBar.width", 9);
        UIManager.put("ToolTip.background", WHITE);
    }

    private static final class ScrollColumn extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 20;
        }

        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return 120;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static final class SearchField extends JTextField {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setColor(MUTED);
                g.setFont(BODY);
                g.drawString(
                        "Tìm tên phim hoặc thể loại…",
                        12,
                        (getHeight() + g.getFontMetrics().getAscent()) / 2 - 3);
                g.dispose();
            }
        }
    }

    static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(BODY.deriveFont((float) size));
        label.setForeground(color);
        return label;
    }

    static JLabel title(String text, int size) {
        JLabel label = label(text, size, INK);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    static JPanel panel(LayoutManager layout, Color color) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(color);
        return panel;
    }

    static JPanel column(Color color) {
        JPanel panel = new ScrollColumn();
        panel.setBackground(color);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    static Border padding(int n) {
        return BorderFactory.createEmptyBorder(n, n, n, n);
    }

    static JPanel card(LayoutManager layout) {
        JPanel panel = panel(layout, WHITE);
        panel.setBorder(new CompoundBorder(new LineBorder(LINE), padding(20)));
        return panel;
    }

    static JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(null);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getViewport().setBackground(BG);
        pane.getVerticalScrollBar().setUnitIncrement(20);
        return pane;
    }

    static JTextArea paragraph(String text) {
        JTextArea area = new JTextArea(text);
        area.setFont(BODY);
        area.setForeground(MUTED);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFocusable(false);
        return area;
    }

    static void field(JTextField field) {
        field.setBorder(
                new CompoundBorder(
                        new LineBorder(LINE), BorderFactory.createEmptyBorder(11, 12, 11, 12)));
        field.setFont(BODY);
    }

    static String money(long value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ";
    }

    static String html(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static JButton button(String text, boolean primary) {
        return new SoftButton(text, primary ? ACCENT : WHITE, primary ? WHITE : INK);
    }

    static class SoftButton extends JButton {
        Color fill;

        SoftButton(String text, Color fill, Color foreground) {
            super(text);
            this.fill = fill;
            setForeground(foreground);
            setFont(BODY.deriveFont(Font.BOLD));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(!isEnabled() ? LINE : getModel().isRollover() ? fill.darker() : fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            if (fill.equals(WHITE)) {
                g.setColor(LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
