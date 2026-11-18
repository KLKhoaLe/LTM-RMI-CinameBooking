# Nghiệp vụ và phạm vi

## Luồng người dùng

Đăng ký/đăng nhập → chọn ngày và phim → chọn suất → chọn ghế → xem tổng tiền → xác nhận → nhận mã vé → xem/hủy trong Vé của tôi.

Không thu tiền thật. Nút xác nhận là chốt đơn mô phỏng, không có trạng thái chờ thanh toán. Mã vé là định danh duy nhất, không phải QR hoặc chứng từ được rạp thật chấp nhận.

## Quy tắc do nhóm chọn

1. Tài khoản: username 3–24 ký tự chữ không dấu/số/_, không phân biệt hoa thường; mật khẩu 8–72 ký tự; họ tên 1–60 ký tự. Phiên có hạn 8 giờ; server khởi động lại yêu cầu đăng nhập lại.
2. Một rạp, ba phòng P01–P03, mỗi phòng A–F và cột 1–8 (48 ghế). Mọi ghế trong một suất cùng giá để tránh thêm nghiệp vụ giá.
3. Suất bắt đầu lúc 09:00, 13:00, 17:00, 21:00; các phim mẫu dài 95–125 phút, không chồng lịch trong cùng phòng.
4. Thứ Hai–Sáu: 75.000 đ/ghế; cuối tuần: 90.000 đ/ghế. Server tính giá, không nhận tổng tiền do client gửi.
5. Một đơn 1–6 ghế, không cho danh sách ghế trùng hoặc ghế ngoài phòng.
6. Khóa ghế theo cặp (suất chiếu, mã ghế). A1 của suất 13:00 không khóa A1 của suất 17:00.
7. Chọn ghế chỉ diễn ra trên client, KHÔNG giữ ghế. Chỉ server xác nhận đặt thành công mới đổi thành đã đặt.
8. Đơn nhiều ghế: hoặc đặt toàn bộ, hoặc không đặt ghế nào nếu xung đột. Không chấp nhận đặt một phần.
9. Ngừng đặt khi còn 5 phút trước suất chiếu; đúng thời điểm biên cũng từ chối. Thời gian có thẩm quyền là server, múi giờ Việt Nam.
10. Chỉ chủ vé được xem vé riêng và hủy. Hủy toàn bộ đơn khi còn hơn 30 phút trước giờ chiếu; đúng 30 phút từ chối. Ghế được mở lại sau khi hủy thành công. Không tính phí/hoàn tiền vì không thu tiền thật.
11. Client gửi requestId UUID; gửi lại cùng người dùng + requestId + nội dung trả lại đơn cũ, kể cả đơn đã hủy. Tái sử dụng requestId với nội dung khác bị từ chối.
12. Dữ liệu được lưu trước khi báo thành công. Lỗi ghi file: không cập nhật snapshot trong RAM, báo giao dịch thất bại. Mất phản hồi mạng không đồng nghĩa giao dịch thất bại: kiểm tra Vé của tôi trước khi tạo yêu cầu mới.
13. Callback chỉ báo “suất này thay đổi”; client đọc lại sơ đồ ghế chính thức. Có polling dự phòng mỗi 2 giây để bù callback trễ/mất.
14. Phân loại độ tuổi chỉ là thông tin hiển thị của dữ liệu hư cấu; bản này không xác minh tuổi người dùng hoặc giấy tờ.

## Tham khảo nghiệp vụ thực tế

CGV mô tả luồng tài khoản → phim/rạp/suất/ghế → xác nhận thanh toán → mã vé, và thời điểm ngừng bán online trước suất chiếu. CGV có cơ chế giữ ghế khi thanh toán, cùng điều kiện hoàn vé riêng. Bản học tập CHỦ ĐỘNG bỏ giữ ghế/thanh toán và dùng quy tắc hủy trước 30 phút do nhóm tự chọn; không mô phỏng đầy đủ chính sách CGV.

Nguồn chính thức: https://www.cgv.vn/default/faq/ (đối chiếu ngày 17/11/2026).

## Không triển khai

Thanh toán, voucher, đồ ăn, email/SMS, QR check-in, nhiều rạp, màn hình quản trị, giữ ghế có thời hạn, kiểm tra tuổi, quản lý hoàn tiền. Dữ liệu phim và lịch được tạo bởi SeedData để tập trung vào lập trình mạng.
