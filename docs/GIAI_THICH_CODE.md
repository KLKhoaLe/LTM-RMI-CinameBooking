# Đọc và trình bày code

## Đọc theo thứ tự

| Thứ tự | Lớp | Cần hiểu |
| --- | --- | --- |
| 1 | common/Models.java | Movie, Showtime, Session, SeatMap, Booking là record bất biến, Serializable |
| 2 | common/CinemaService.java | Hợp đồng các hàm gọi từ xa; lỗi mạng khác lỗi nghiệp vụ |
| 3 | server/ServerMain.java | Tạo Registry, export service, đăng ký tên |
| 4 | client/ClientConnection.java | Tìm server, lookup stub, export callback với port 0 |
| 5 | server/CinemaServiceImpl.java | Kiểm tra phiên, book(), cancel(), subscribe() |
| 6 | server/FileStore.java | Lưu snapshot và chỉ cập nhật RAM khi ghi thành công |
| 7 | server/CallbackHub.java | Thông báo ngoài khóa, tránh client chậm làm nghẽn giao dịch |
| 8 | client/CinemaPanel.java | Hiển thị, thao tác UI, SwingWorker và Timer |

Các lớp khác: PasswordHasher xử lý mật khẩu; SeedData sinh dữ liệu; DiscoveryServer chỉ tìm server; Ui/Poster/LoginPanel chứa giao diện; TimeoutSocketFactory giới hạn thời gian kết nối/đọc; RpcFilters hạn chế đối tượng nhận qua serialization.

## Một lần đặt vé đi đâu?

1. CinemaPanel gom session token, showtimeId, danh sách ghế và requestId.
2. Gọi connection.service.book(...). service là stub nhận qua Registry.lookup.
3. RMI truyền tham số qua TCP, server chạy CinemaServiceImpl.book().
4. Server xác thực phiên, kiểm tra mã yêu cầu lặp, suất, thời gian, ghế, tranh chấp; tự tính giá.
5. Server lưu snapshot mới; trả Booking qua RMI.
6. CallbackHub thông báo cho client đang xem suất đó. Client yêu cầu seatMap() mới và cập nhật trên EDT.

Registry giúp tìm dịch vụ, không xử lý từng giao dịch đặt vé. UDP discovery chỉ giúp tìm địa chỉ server, không thay RMI trong nghiệp vụ.

## Vì sao không đặt trùng?

Điểm cần chỉ vào khi thuyết trình: khối `synchronized (store)` trong `CinemaServiceImpl.book`.

Cả chuỗi đọc ghế → kiểm tra xung đột → tạo Booking → lưu snapshot nằm trong cùng khóa. Thread B phải chờ A kết thúc; khi B đọc, ghế A vừa đặt đã xuất hiện. Không đặt khóa ở client vì hai client không chia sẻ bộ nhớ. Không chỉ khóa thao tác lưu, vì cả hai có thể cùng đọc ghế trống trước lúc lưu.

Mọi thao tác đọc snapshot và ghi/hủy/đăng ký cũng tuân theo cùng khóa. Đây là giải pháp dễ giải thích cho một server nhỏ, đổi lại các giao dịch ghi phải lần lượt. Nếu chuyển sang nhiều server, phải dùng cơ chế giao dịch/unique constraint tại DB, khóa Java này không đủ.

## Vì sao không cập nhật RAM trước khi lưu?

FileStore ghi trạng thái mới vào cinema.tmp, flush/sync rồi atomic move thay cinema.bin. Chỉ sau khi thành công mới gán `state = next`. Nếu thao tác lỗi, trạng thái cũ vẫn được giữ. Không hứa bảo vệ trước mọi hỏng phần cứng hoặc hỏng filesystem; nên sao lưu file khi dừng server.

FileLock ngăn hai process server dùng chung thư mục dữ liệu. File hỏng không tự reset; server dừng để tránh âm thầm mất dữ liệu.

## Callback và polling

Client export SeatListener ở port 0: hệ điều hành chọn cổng đang trống. RMI đưa địa chỉ/cổng đó vào stub callback. subscribe() chuyển stub sang server. Server gọi seatsChanged() bằng một kết nối RMI ngược về client.

CallbackHub chạy worker riêng ngoài khóa store; hàng đợi giới hạn 128, quá tải có thể bỏ thông báo. Timeout 2 giây và xóa listener khi lỗi giúp không chờ mãi. Mỗi client đăng ký lại định kỳ; polling 2 giây lấy lại trạng thái nếu callback không đến. Hệ thống ưu tiên đúng dữ liệu thay vì hứa mọi thông báo đều được gửi đúng một lần.

Mất kết nối liên tiếp 3 lần ở màn hình chính đưa người dùng về đăng nhập để tự tìm lại server. Server khởi động lại không giữ phiên đăng nhập. Không tự gửi lại giao dịch đặt vé nền khi chưa biết kết quả.

## UI không làm treo

SwingWorker chạy RMI ở nền, done() cập nhật UI trên Event Dispatch Thread. Callback cũng dùng SwingUtilities.invokeLater. Không chạy socket/đọc ghi mạng trực tiếp trong action listener. Revision giúp bỏ qua snapshot cũ; callback là tín hiệu cần tải lại, không phải dữ liệu ghế được tin cậy trực tiếp.

## Lựa chọn công nghệ

Java 21 + Swing/Java2D + Java RMI + Java serialization + PBKDF2, đều thuộc JDK. Không dùng Spring, ORM, DI hoặc Lombok để người học nhìn thấy rõ luồng điều khiển. Snapshot file thay DB nhằm chạy ngay mà không cài dịch vụ khác; đây là lựa chọn phạm vi bài học, không khuyến nghị cho hệ thống bán vé thật.

Nguồn kỹ thuật:
- RMI architecture: https://docs.oracle.com/en/java/javase/21/docs/specs/rmi/arch.html
- Export object và port: https://docs.oracle.com/en/java/javase/21/docs/api/java.rmi/java/rmi/server/UnicastRemoteObject.html
- SwingWorker: https://docs.oracle.com/javase/tutorial/uiswing/concurrency/worker.html
- Serialization filtering: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.html
