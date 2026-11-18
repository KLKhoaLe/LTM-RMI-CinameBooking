# Kiểm thử — 17/11/2026

## Kết quả đã chạy

Build ngoại tuyến bằng Java compiler 21: thành công. Maven package ở project gốc và hai project độc lập: thành công trong môi trường kiểm thử. Runtime không có thư viện ngoài JDK.

`java Build.java test`: **34 kiểm tra qua RMI thật đã qua**. Nổi bật:

- Registry lookup, ping, xác thực phiên, đăng ký/đăng nhập, tên đăng nhập không phân biệt hoa thường.
- Chặn username trùng, password sai, danh sách ghế trùng, ghế không tồn tại, hơn 6 ghế.
- Dữ liệu mẫu 6 phim / 84 suất khi khởi tạo.
- 20 yêu cầu cùng lúc qua stub: đúng 1 thành công, 19 SEAT_TAKEN.
- Hai process Java riêng cùng đặt F1: một WIN, một CONFLICT.
- Callback thực sự chạy qua stub RMI, không phải gọi trực tiếp listener.
- Đặt nhiều ghế thất bại không lấy mất những ghế còn trống.
- Giá do server tính, thứ tự ghế chuẩn hóa, retry cùng mã không tạo đơn trùng.
- Không xem/hủy vé người khác; hủy lặp an toàn; request cũ không hồi sinh vé đã hủy.
- Ghế cùng tên ở suất khác không xung đột; ghế hủy có thể được đặt lại.
- Mô phỏng lỗi ghi đĩa: không phát sinh ghế đã đặt trong RAM.
- FileLock chặn server thứ hai dùng cùng dữ liệu.
- Biên đóng bán 5 phút và biên hủy 30 phút.
- Đăng xuất/hết hạn phiên bị từ chối.
- Tài khoản, vé, hủy vé và requestId tồn tại sau khi mở lại kho dữ liệu.
- File lưu không chứa mật khẩu plaintext của tài khoản kiểm thử.

Log chạy kèm tại `test-output.txt`. Mã kiểm thử trong thư mục `tests`. Test tự tạo thư mục dữ liệu tạm riêng, không dùng hoặc xóa dữ liệu demo của bạn.

## Giao diện

Ảnh trong previews được render từ panel Swing thực, dùng dữ liệu đọc qua RMI từ server đang chạy. Đã kiểm tra trực quan màn hình đăng nhập, đăng ký, chọn ghế và Vé của tôi; điều chỉnh phần tổng tiền/xác nhận để luôn ở khu vực riêng. Đây không phải mockup giao diện web.

## Chưa xác nhận trên máy người dùng

- Chưa chạy trực tiếp trên Windows/IntelliJ của nhóm.
- Chưa kiểm thử hai laptop vật lý qua Wi-Fi của trường, profile firewall hoặc VMware của nhóm.
- Kiểm thử mạng tự động chạy trên loopback (bao gồm hai JVM client riêng), không chứng minh mọi mạng LAN đều cho phép broadcast/callback.
- Không benchmark tải lớn; không đánh giá production security, TLS, thanh toán hay host Internet.

Bước xác nhận tiếp theo: chạy hai máy LAN theo DEMO_VA_KET_NOI.md, kiểm tra firewall Java và địa chỉ server. Đây là kiểm tra triển khai, không thay thế kiểm thử chống đặt trùng đã có.
