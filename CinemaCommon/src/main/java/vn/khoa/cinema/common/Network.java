package vn.khoa.cinema.common;

import java.net.*;
import java.util.*;

public final class Network {
    public static final int REGISTRY_PORT = 1099, SERVICE_PORT = 5000, DISCOVERY_PORT = 45888;
    public static final String DISCOVER = "CINEMA_RMI_DISCOVER_V1", ANSWER = "CINEMA_RMI_SERVER_V1";

    private Network() {}

    /** Ưu tiên card mạng LAN; quản trị viên có thể override trên server nhiều card mạng. */
    public static String localAddress() throws SocketException {
        List<String> addresses = new ArrayList<>();
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback()) continue;
            for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
                    String name = nic.getName().toLowerCase(Locale.ROOT);
                    if (!nic.isVirtual()
                            && !name.contains("vmnet")
                            && !name.contains("docker")
                            && !name.contains("vbox")) addresses.addFirst(address.getHostAddress());
                    else addresses.add(address.getHostAddress());
                }
            }
        }
        return addresses.isEmpty() ? "127.0.0.1" : addresses.getFirst();
    }
}
