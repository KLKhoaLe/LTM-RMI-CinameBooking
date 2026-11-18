package vn.khoa.cinema.server;

import vn.khoa.cinema.common.Network;

import java.net.*;
import java.nio.charset.StandardCharsets;

/** UDP chỉ tìm địa chỉ; mọi thao tác đăng nhập/đặt vé vẫn đi qua RMI. */
final class DiscoveryServer implements AutoCloseable {
    private final DatagramSocket socket;

    DiscoveryServer() throws SocketException {
        socket = new DatagramSocket(Network.DISCOVERY_PORT);
        Thread.ofPlatform().daemon().name("lan-discovery").start(this::listen);
    }

    private void listen() {
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[256], 256);
                socket.receive(packet);
                String request =
                        new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (!request.equals(Network.DISCOVER)) continue;
                byte[] response =
                        (Network.ANSWER + ":" + Network.REGISTRY_PORT)
                                .getBytes(StandardCharsets.UTF_8);
                socket.send(
                        new DatagramPacket(
                                response, response.length, packet.getAddress(), packet.getPort()));
            } catch (Exception e) {
                if (!socket.isClosed()) System.err.println("Discovery: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        socket.close();
    }
}
