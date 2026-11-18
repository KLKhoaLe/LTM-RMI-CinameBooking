package vn.khoa.cinema.common;

import java.io.*;
import java.net.*;
import java.rmi.server.RMIClientSocketFactory;

/** Được đóng trong stub để lời gọi RMI/callback không chờ vô hạn khi máy kia mất mạng. */
public final class TimeoutSocketFactory implements RMIClientSocketFactory, Serializable {
    private final int readTimeout;

    public TimeoutSocketFactory(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 2000);
            socket.setSoTimeout(readTimeout);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TimeoutSocketFactory f && f.readTimeout == readTimeout;
    }

    @Override
    public int hashCode() {
        return readTimeout;
    }
}
