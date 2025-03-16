# QLNK Backend

## Giới thiệu

QLNK (Quản Lý Nông Nghiệp Kết Nối) là hệ thống quản lý cây trồng theo thời gian thực, giúp theo dõi và điều khiển các thông số môi trường như nhiệt độ, độ ẩm, ánh sáng thông qua các cảm biến IoT. Hệ thống sử dụng **Spring Boot với WebFlux** để xử lý dữ liệu theo mô hình **Reactive Programming**, đảm bảo khả năng mở rộng và hiệu suất cao.

Hệ thống có các thành phần chính:

1. **Cảm biến IoT**: Gửi dữ liệu đo được lên hệ thống thông qua giao thức **MQTT**.
2. **MQTT Broker**: Đóng vai trò trung gian, tiếp nhận dữ liệu từ cảm biến và truyền đến backend.
3. **Backend (Spring Boot WebFlux)**:
   - Nhận dữ liệu từ MQTT, xử lý và lưu vào **MongoDB**.
   - Cung cấp API để frontend có thể truy xuất dữ liệu.
   - Sử dụng **WebSocket** để cập nhật dữ liệu theo thời gian thực cho frontend.
   - Quản lý xác thực người dùng bằng **JWT**, hỗ trợ **refresh token** với Redis.
4. **Frontend**: Nhận dữ liệu theo thời gian thực qua WebSocket và hiển thị thông tin trực quan.

Hệ thống được xây dựng theo mô hình **event-driven architecture**, trong đó các sự kiện từ MQTT được xử lý một cách bất đồng bộ, giúp giảm tải cho server và cải thiện tốc độ phản hồi.

---

## Yêu cầu hệ thống

- Java 21
- Maven
- MongoDB
- Redis

## Cài đặt

### 1. Clone repository

```sh
git clone https://github.com/Brookvita3/DADN_BE_WebFlux
cd QLNK-backend
```

### 2. Cấu hình biến môi trường

Trước khi chạy ứng dụng, hãy đặt các biến môi trường sau:

```sh
EXPIRATION_TIME="thoi gian het han access token (milisecond)"
MONGODB_URL="url database cua ban"
REFRESH_EXPIRATION_TIME=thoi gian het han refresh token (milisecond)
SECRET_KEY="key cho access token"
REFRESH_SECRET_KEY="key cho refresh token"
```

### 3. Cài đặt Redis (cho Windows)

Redis không có bản chính thức trên Windows, nhưng bạn có thể sử dụng WSL hoặc tải Redis thông qua Docker.

#### Cách 1: Cài đặt Redis bằng WSL (Windows Subsystem for Linux)

1. Mở **PowerShell** với quyền Admin và chạy:

```sh
wsl --install
```

2. Sau khi cài xong WSL, mở terminal WSL (Ubuntu) và chạy:

```sh
 sudo apt update
 sudo apt install redis-server -y
```

3. Khởi động redis

```sh
sudo systemctl start redis
```

4. Kiểm tra Redis đang chạy:

```bash
redis-cli ping
```

Nếu nhận được phản hồi PONG, Redis đã chạy thành công.

#### Cách 2: Chạy Redis bằng Docker

1. Cài đặt Docker Desktop: [tải tại đây](https://www.docker.com/products/docker-desktop/)
2. Chạy Redis container:

```sh
docker run --name redis -d -p 6379:6379 redis
```

3. Kiểm tra Redis:

```sh
docker exec -it redis redis-cli ping
```

Nếu nhận được PONG, Redis đã hoạt động.

## API documentation

API documentation có thể tải về tại [đây](https://github.com/Brookvita3/DADN_BE_WebFlux/blob/main/DA_CNPM.postman_collection.json) và import vào Postman.
