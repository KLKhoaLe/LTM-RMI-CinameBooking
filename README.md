# Cinema RMI — Hai project đặt vé xem phim

Ứng dụng mô phỏng cho nhóm hai người, Java 21+, Swing, Java RMI. Giao diện tiếng Việt, không cần MySQL, không cần Internet khi chạy. Dùng Windows hoặc Linux; không bắt buộc máy ảo.

## Chạy nhanh (không phải build trước)

1. Có Java 21 hoặc mới hơn. Chạy `CinemaServer/run.bat` trên Windows (Linux: `sh CinemaServer/run.sh`). Đợi `SERVER READY`.
2. Chạy `CinemaClient/run.bat` trên một hoặc nhiều máy. Client tự kết nối; đăng nhập hoặc đăng ký. Có thể mở hai client trên cùng máy.

Tài khoản mẫu:

| Tên đăng nhập | Mật khẩu | Ghi chú |
| --- | --- | --- |
| khoa | Khoa@123 | Chưa có vé lúc khởi tạo |
| linh | Linh@123 | Có các vé mẫu tạo ra ghế xám |

Các phim là dữ liệu hư cấu, poster được vẽ bằng Java2D. Không tích hợp hãng phim, thanh toán, email hoặc rạp thật.

## Mở hai project riêng trong IntelliJ

- File → Open → chọn `CinemaServer/pom.xml` → Open as Project. Chọn Project SDK JDK 21 hoặc 22. Mở `vn.khoa.cinema.server.ServerMain`, bấm Run.
- Mở cửa sổ IntelliJ thứ hai với `CinemaClient/pom.xml`. Chọn cùng JDK, chạy `vn.khoa.cinema.client.ClientMain`.
- Hai project độc lập, mỗi project đã kèm thư viện common và mã nguồn của thư viện trong `repository/`. Không cần tự cài common trước.
- IntelliJ/Maven có thể cần Internet lần đầu để tải plugin Maven chuẩn. Nếu bị chặn, vẫn chạy JAR đã đóng gói bằng `run.bat` được.
- Có thể mở `pom.xml` ở thư mục gốc để thấy cả hai ứng dụng và module thư viện `CinemaCommon` trong một cửa sổ. Đây là cách thuận tiện nhất khi sửa interface.
- Để chạy hai client trong IntelliJ: Edit Configurations → Modify options → Allow multiple instances (tên có thể khác theo phiên bản), hoặc mở `run.bat` hai lần.

## Thư mục

| Đường dẫn | Mục đích |
| --- | --- |
| CinemaServer/src/main/java | Mã server, xử lý nghiệp vụ, lưu trữ, discovery |
| CinemaClient/src/main/java | Giao diện và kết nối RMI |
| CinemaCommon/src/main/java | Nguồn chính thức của interface, DTO, callback |
| CinemaServer/app/CinemaServer.jar | Bản chạy server đã build |
| CinemaClient/app/CinemaClient.jar | Bản chạy client đã build |
| docs/NGHIEP_VU.md | Quy tắc, phạm vi và dữ liệu mẫu |
| docs/GIAI_THICH_CODE.md | Thứ tự đọc code, cách giải thích RMI và đặt trùng |
| docs/DEMO_VA_KET_NOI.md | Chạy hai máy, firewall, kịch bản demo |
| docs/KIEM_THU.md | Kết quả kiểm thử và giới hạn đã xác định |
| docs/previews | Ảnh render từ chính giao diện Swing |
| tests | Kiểm thử qua RMI thật, gồm hai process Java riêng |

## Build lại khi sửa code

Tại thư mục gốc:

```text
java Build.java
java Build.java test
```

Hoặc Windows: `build.bat`, `build.bat test`; Linux: `sh build.sh`, `sh build.sh test`.

Chỉ cần JDK 21+, không tải thư viện. Build từng module riêng: common → server/client; hai ứng dụng không được tham chiếu mã nội bộ của nhau. Build tạo lại JAR chạy và bản common dùng trong hai project độc lập.

Nếu sửa `CinemaCommon`, chạy build gốc rồi Reload All Maven Projects ở hai IDE. Nếu IDE vẫn giữ common cũ, mở project gốc hoặc xóa riêng cache `~/.m2/repository/vn/khoa/cinema-common/1.0.0` rồi reload. Không sửa bản nguồn nằm bên trong JAR.

Maven cũng hỗ trợ `mvn clean package` ở gốc hoặc ở từng project. `mvn package` tạo JAR module trong `target`, không phải JAR chạy độc lập; dùng `java Build.java` để cập nhật các JAR đầy đủ trong `app`.

## Kết nối không có màn hình cấu hình

Client thử server trên máy hiện tại, sau đó tìm server qua UDP broadcast trong cùng LAN. Tất cả thao tác đăng ký, đăng nhập, đọc lịch, đặt/hủy vé đều qua RMI. Cổng callback do hệ điều hành tự chọn; client không phải nhập cổng.

Điều kiện: một server hoạt động, các máy cùng LAN có thể liên lạc, ứng dụng Java được cho phép qua firewall. Wi-Fi khách, AP isolation, VLAN hoặc router chặn broadcast có thể ngăn discovery. Không có chương trình nào bảo đảm vượt qua mọi firewall mà không được cho phép.

Không tắt firewall. Nếu Windows hỏi, cho phép Java trên mạng Private tin cậy. Hướng dẫn và script tùy chọn cho người triển khai nằm trong `docs/DEMO_VA_KET_NOI.md`. Callback bị chặn vẫn có lấy lại trạng thái mỗi 2 giây; polling không được gọi là callback.

Bản này nhắm tới demo LAN. Chưa triển khai Internet, chưa bật TLS; không đưa registry/dịch vụ ra Internet công khai như một sản phẩm hoàn chỉnh. Khi biết yêu cầu của thầy, có thể thay endpoint và cách kết nối mà giữ lại nghiệp vụ.

## Dữ liệu

Server lưu tại `user.home/.cinema-rmi/data/cinema.bin` (Windows thường là `C:\Users\TEN_BAN\.cinema-rmi\data`). Đây là snapshot nhị phân Java, không phải SQLite. Không cần cài DB; chỉ server đọc/ghi file. Lựa chọn này nhằm giảm cấu hình và giúp dễ giải thích giao dịch một server.

Lần đầu có 6 phim, 3 phòng, mỗi phòng 48 ghế, 4 suất/phòng/ngày, lịch 7 ngày: tổng 84 suất. Mỗi lần khởi động server bổ sung lịch 7 ngày tới nếu thiếu, không xóa vé cũ. Nếu server chạy liên tục quá một tuần, khởi động lại để bổ sung lịch.

Mật khẩu lưu dạng PBKDF2-HMAC-SHA256 với salt riêng. Không lưu plaintext. Lưu trữ và khóa dữ liệu giới hạn ở một server; không phải kiến trúc nhiều server phân tán.

Muốn tạo lại dữ liệu mẫu: dừng server, sao lưu rồi đổi tên thư mục `data`; chạy lại. Không làm khi cần giữ vé đã đặt.
