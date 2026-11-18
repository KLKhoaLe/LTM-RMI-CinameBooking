# Kết nối và demo

## Hai laptop là đủ

Laptop A: CinemaServer + CinemaClient (khoa). Laptop B: CinemaClient (linh hoặc tài khoản mới). Cả hai cùng Wi-Fi/LAN tin cậy. Không bắt buộc Linux; có thể dùng Windows cho cả hai.

Demo trước trên cùng máy bằng hai cửa sổ client. Sau đó kiểm tra hai máy vật lý; không dùng localhost làm bằng chứng đã chạy LAN.

## Client tự động làm gì?

1. Thử Registry trên 127.0.0.1:1099.
2. Nếu không có, gửi broadcast UDP tới cổng 45888 trên các card mạng.
3. Server phản hồi, client lookup dịch vụ RMI tại địa chỉ gửi phản hồi.
4. Client chọn địa chỉ callback theo đường đi đến server, export port 0 (cổng tự chọn).
5. Hiện trạng thái đã kết nối, người dùng chỉ đăng nhập/đăng ký.

Một server trong một LAN. Nếu có nhiều server, bản này chọn server hợp lệ tìm được trước, chưa có màn hình chọn rạp/server.

## Firewall vẫn bật

| Máy | Kết nối nhận cần cho phép |
| --- | --- |
| Server | TCP 1099 Registry, TCP 5000 service, UDP 45888 discovery |
| Client | TCP đến tiến trình Java cho callback ở cổng động; chỉ từ LAN tin cậy |

Windows hỏi cho phép Java: chọn mạng Private khi đó là mạng tin cậy. Khi chạy trong IntelliJ, Java phải là đúng java.exe của JDK cấu hình trong IDE. Cho phép một Java khác trên PATH không tự áp dụng cho JDK của IntelliJ.

Có script tùy chọn `Allow-LanJava.ps1` dành cho người triển khai có quyền quản trị. Nó không tắt firewall, chỉ tạo rule trong profile Private, phạm vi LocalSubnet. Mặc định lấy Java trên PATH; có thể truyền -JavaPath đường dẫn đúng JDK của IDE. Chạy PowerShell Administrator sau khi tự xem nội dung:

```powershell
.\Allow-LanJava.ps1 -Role Server
.\Allow-LanJava.ps1 -Role Client
# Máy chạy cả server và client:
.\Allow-LanJava.ps1 -Role Both
```

Đây là cấu hình hệ điều hành một lần, không phải form nhập IP/cổng của client. Chương trình không tự sửa firewall hay yêu cầu mật khẩu admin. Nếu mạng/thiết bị không cho phép, không thể đảm bảo “chạy là kết nối” bằng riêng code Java.

Ubuntu server nếu đang dùng UFW: người quản trị cho phép TCP 1099,5000 và UDP 45888 từ subnet LAN thực tế. Không chạy lệnh mở toàn Internet chỉ để demo.

## Server có nhiều card mạng / VMware

Server tự chọn IPv4, có thể nhầm khi máy có VPN hoặc card mạng ảo. Xem `Advertised address` trong console. Người triển khai có thể chỉ định ở SERVER:

```text
java -Dcinema.host=192.168.1.20 -jar app/CinemaServer.jar
```

Trong IntelliJ thêm cùng tham số vào VM options của ServerMain. Thay IP bằng IP thực tế, không sao chép nguyên ví dụ. Cổng 1099/5000/45888 cố định, nếu bị chiếm phải dừng server cũ hoặc xử lý tiến trình chiếm cổng; không tự giết ứng dụng khác.

Nếu dùng VMware NAT, máy ngoài có thể không truy cập IP của VM. Để demo nhanh, chạy server trực tiếp trên laptop hoặc dùng bridged khi mạng cho phép. Không cần VM chỉ để chạy Java.

Broadcast không đi qua Internet/router theo cách chương trình đang dùng. Tùy chọn `-Dcinema.server=HOST` dành cho người đóng gói client khi triển khai sau này; không phải yêu cầu người dùng nhập vào giao diện. Callback qua NAT vẫn cần đường kết nối ngược thích hợp. Chưa triển khai host Internet/TLS trong bản này.

## Kịch bản trình bày 5–7 phút

1. Mở server, chỉ địa chỉ/cổng và file dữ liệu. Mở hai client với hai tài khoản khác nhau.
2. Chọn cùng phim, cùng ngày mai, cùng suất. Chỉ ghế xám (đã đặt) và trắng (trống).
3. Cả hai chọn cùng ghế A1. Trên cả hai máy bấm Xác nhận để mở hộp thoại, chưa bấm OK.
4. Máy A bấm OK trước; máy B bấm OK sau. A có vé, B bị báo xung đột. Callback có thể đã cập nhật phía sau hộp thoại B nhưng yêu cầu xác nhận vẫn được server kiểm tra.
5. Cho A đặt hai ghế khác. Mở Vé của tôi, kiểm tra giá, ghế, mã vé; hủy trước giờ chiếu hơn 30 phút. B thấy ghế được mở lại.
6. Đóng một client đột ngột: client còn lại vẫn hoạt động. Dừng server: client báo mất kết nối rồi quay về màn hình tự tìm server. Bật lại, đăng nhập, vé vẫn còn.
7. Chạy `java Build.java test` để minh họa cạnh tranh có barrier, không dựa vào tốc độ bấm chuột.

Trong demo race thủ công, nếu B chưa mở hộp xác nhận trước khi A đặt, callback có thể bỏ ghế đã mất khỏi lựa chọn của B; đó là hành vi đúng. Test tự động mới bảo đảm hai yêu cầu được phát sát nhau.

## Cách nói ngắn gọn

“Client tìm server bằng UDP, nhưng nghiệp vụ dùng RMI. Registry trả stub dịch vụ. Server dùng cùng một khóa để kiểm tra và lưu đặt vé, nên một ghế trong một suất chỉ có một chủ. Sau khi lưu, callback thông báo các client cập nhật. Firewall vẫn bật và chỉ cho phép đúng tiến trình/cổng cần dùng.”
