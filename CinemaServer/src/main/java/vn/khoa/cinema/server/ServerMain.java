package vn.khoa.cinema.server;

import vn.khoa.cinema.common.*;
import vn.khoa.cinema.common.Models;

import java.io.ObjectInputFilter;
import java.nio.file.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.time.Clock;

public final class ServerMain {
    public static void main(String[] args) throws Exception {
        String address = System.getProperty("cinema.host", Network.localAddress());
        System.setProperty("java.rmi.server.hostname", address);
        System.setProperty("java.rmi.server.useCodebaseOnly", "true");
        Path data =
                Path.of(
                        System.getProperty(
                                "cinema.data",
                                Path.of(System.getProperty("user.home"), ".cinema-rmi", "data")
                                        .toString()));
        CinemaServiceImpl service = new CinemaServiceImpl(data, Clock.system(Models.ZONE));
        Registry registry = null;
        DiscoveryServer discovery = null;
        try {
            registry = LocateRegistry.createRegistry(Network.REGISTRY_PORT);
            // Cho phép DTO, lớp Java nền tảng và proxy callback; giới hạn đồ thị đầu vào.
            ObjectInputFilter filter = RpcFilters.incoming();
            CinemaService stub =
                    (CinemaService)
                            UnicastRemoteObject.exportObject(
                                    service,
                                    Network.SERVICE_PORT,
                                    new TimeoutSocketFactory(7000),
                                    null,
                                    filter);
            registry.rebind(CinemaService.NAME, stub);
            discovery = new DiscoveryServer();
        } catch (Exception e) {
            if (discovery != null) discovery.close();
            try {
                UnicastRemoteObject.unexportObject(service, true);
            } catch (Exception ignored) {
            }
            if (registry != null) UnicastRemoteObject.unexportObject(registry, true);
            service.close();
            throw e;
        }
        DiscoveryServer activeDiscovery = discovery;
        Registry activeRegistry = registry;
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    activeDiscovery.close();
                                    try {
                                        UnicastRemoteObject.unexportObject(service, true);
                                        UnicastRemoteObject.unexportObject(activeRegistry, true);
                                        service.close();
                                    } catch (Exception e) {
                                        System.err.println(e.getMessage());
                                    }
                                }));
        System.out.println(
                """
                ==================================================
                     CINEMA / RMI    •    SERVER READY
                ==================================================
                """);
        System.out.println("Advertised address : " + address);
        System.out.println("RMI registry / API : 1099 / 5000 TCP");
        System.out.println("LAN discovery      : 45888 UDP");
        System.out.println("Data               : " + data.toAbsolutePath());
        System.out.println("Client: mở ClientMain; không cần nhập IP/cổng.");
        System.out.println("Tài khoản demo: khoa / Khoa@123  |  linh / Linh@123");
        System.out.println("Giữ firewall bật; cho phép Java trên mạng Private. Ctrl+C để dừng.");
    }
}
