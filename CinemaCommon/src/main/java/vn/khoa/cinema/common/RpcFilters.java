package vn.khoa.cinema.common;

import java.io.ObjectInputFilter;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Giới hạn dữ liệu giải tuần tự; chỉ cho phép proxy callback đúng interface. */
public final class RpcFilters {
    private RpcFilters() {}

    public static ObjectInputFilter incoming() {
        ObjectInputFilter classes =
                ObjectInputFilter.Config.createFilter(
                        "vn.khoa.cinema.common.**;java.base/*;java.rmi/*;!*");
        return info -> {
            if (info.depth() > 30 || info.references() > 10000 || info.arrayLength() > 10000)
                return ObjectInputFilter.Status.REJECTED;
            Class<?> type = info.serialClass();
            if (type != null && Proxy.isProxyClass(type)) {
                return Arrays.equals(type.getInterfaces(), new Class<?>[] {SeatListener.class})
                        ? ObjectInputFilter.Status.ALLOWED
                        : ObjectInputFilter.Status.REJECTED;
            }
            return classes.checkInput(info);
        };
    }
}
