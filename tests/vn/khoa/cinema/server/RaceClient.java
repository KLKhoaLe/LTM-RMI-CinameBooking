package vn.khoa.cinema.server;

import vn.khoa.cinema.common.*;

import java.rmi.registry.LocateRegistry;
import java.util.*;

public final class RaceClient {
    public static void main(String[] args) throws Exception {
        CinemaService api =
                (CinemaService)
                        LocateRegistry.getRegistry("127.0.0.1", Integer.parseInt(args[0]))
                                .lookup(CinemaService.NAME);
        long wait = Long.parseLong(args[3]) - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        try {
            api.book(args[1], args[2], List.of("F1"), UUID.randomUUID().toString());
            System.out.println("WIN");
        } catch (CinemaException e) {
            if (!e.code().equals("SEAT_TAKEN")) throw e;
            System.out.println("CONFLICT");
        }
    }
}
