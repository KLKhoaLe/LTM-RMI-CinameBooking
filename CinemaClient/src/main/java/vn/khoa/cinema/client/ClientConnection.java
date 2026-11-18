package vn.khoa.cinema.client;

import vn.khoa.cinema.common.*;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.function.BiConsumer;

/** Tìm server tự động trong LAN. Người dùng không phải nhập IP hoặc cổng. */
public final class ClientConnection implements AutoCloseable {
    public final CinemaService service;
    public final String host;
    private final SeatListener localListener;
    public final SeatListener callback;
    public volatile BiConsumer<String, Long> onSeatsChanged = (id, revision) -> {};

    private ClientConnection(CinemaService service, String host) throws Exception {
        this.service = service;
        this.host = host;
        try (DatagramSocket route = new DatagramSocket()) {
            route.connect(InetAddress.getByName(host), Network.REGISTRY_PORT);
            System.setProperty(
                    "java.rmi.server.hostname", route.getLocalAddress().getHostAddress());
        }
        localListener = (id, revision) -> onSeatsChanged.accept(id, revision);
        // port=0: hệ điều hành chọn cổng trống, không yêu cầu người dùng cấu hình.
        callback =
                (SeatListener)
                        UnicastRemoteObject.exportObject(
                                localListener, 0, new TimeoutSocketFactory(2000), null);
    }

    public static ClientConnection connect() throws Exception {
        // Endpoint do người đóng gói đặt nếu triển khai ngoài LAN; không có ô cấu hình trong UI.
        String fixed = System.getProperty("cinema.server", "").trim();
        if (!fixed.isEmpty()) return lookup(fixed);
        try {
            return lookup("127.0.0.1");
        } catch (Exception ignored) {
        }
        Set<InetAddress> destinations = new LinkedHashSet<>();
        destinations.add(InetAddress.getByName("255.255.255.255"));
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback()) continue;
            for (InterfaceAddress address : nic.getInterfaceAddresses())
                if (address.getBroadcast() != null) destinations.add(address.getBroadcast());
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(450);
            byte[] request = Network.DISCOVER.getBytes(StandardCharsets.UTF_8);
            for (int attempt = 0; attempt < 3; attempt++) {
                for (InetAddress target : destinations) {
                    try {
                        socket.send(
                                new DatagramPacket(
                                        request, request.length, target, Network.DISCOVERY_PORT));
                    } catch (Exception ignored) {
                    } // Một card ảo hỏng không được chặn các card còn lại.
                }
                long deadline = System.currentTimeMillis() + 900;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        DatagramPacket answer = new DatagramPacket(new byte[256], 256);
                        socket.receive(answer);
                        String text =
                                new String(
                                        answer.getData(),
                                        0,
                                        answer.getLength(),
                                        StandardCharsets.UTF_8);
                        if (text.equals(Network.ANSWER + ":" + Network.REGISTRY_PORT)) {
                            try {
                                return lookup(answer.getAddress().getHostAddress());
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                    }
                }
            }
        }
        throw new RemoteException(
                "Chưa tìm thấy server. Hãy bật CinemaServer và dùng cùng mạng LAN.");
    }

    public static ClientConnection lookup(String host) throws Exception {
        CinemaService service =
                (CinemaService)
                        LocateRegistry.getRegistry(
                                        host, Network.REGISTRY_PORT, new TimeoutSocketFactory(4000))
                                .lookup(CinemaService.NAME);
        if (!service.ping().equals("CINEMA_RMI_V1"))
            throw new RemoteException("Phiên bản server không tương thích.");
        return new ClientConnection(service, host);
    }

    @Override
    public void close() {
        try {
            UnicastRemoteObject.unexportObject(localListener, true);
        } catch (Exception ignored) {
        }
    }
}
