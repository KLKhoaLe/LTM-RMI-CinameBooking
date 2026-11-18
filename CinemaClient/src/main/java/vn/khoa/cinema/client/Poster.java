package vn.khoa.cinema.client;

import vn.khoa.cinema.common.Models.Movie;

import java.awt.*;
import java.awt.geom.*;

import javax.swing.*;

/** Poster vector tự vẽ, không cần tải ảnh hoặc dùng tài sản của hãng phim. */
final class Poster extends JPanel {
    private final Movie movie;

    Poster(Movie movie) {
        this.movie = movie;
        setPreferredSize(new Dimension(112, 152));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        Color base = Color.decode(movie.color());
        g.setPaint(new GradientPaint(0, 0, base.brighter(), w, h, base.darker()));
        g.fillRoundRect(0, 0, w, h, 10, 10);
        g.setColor(new Color(255, 255, 255, 45));
        g.fillOval(w / 3, h / 5, w, w);
        g.setStroke(new BasicStroke(1.3f));
        g.setColor(new Color(255, 255, 255, 65));
        for (int i = 0; i < 5; i++)
            g.draw(new Arc2D.Double(-w / 2., h * .4 + i * 9, w * 2., h * .7, 0, 180, Arc2D.OPEN));
        g.setColor(Color.WHITE);
        g.setFont(Ui.BODY.deriveFont(Font.BOLD, 11));
        g.drawString("CINEMA ORIGINAL", 10, 21);
        g.setFont(Ui.BODY.deriveFont(Font.BOLD, 38));
        g.drawString(movie.id().substring(1), 12, h - 30);
        g.setFont(Ui.BODY.deriveFont(10f));
        g.drawString(movie.genre().toUpperCase(), 12, h - 13);
        g.dispose();
    }
}
