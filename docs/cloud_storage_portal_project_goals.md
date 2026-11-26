# Cloud Storage Portal — Project Goals

> Dự án cá nhân, phục vụ nhu cầu cá nhân, chạy local (không deploy), triển khai theo Scrum.
> Tài liệu liên quan: `project_tech_stack.md`, `project_plan.md`, `project_specs.md`.

---

## 1. Mục tiêu (Goals)

**Mục tiêu chính:** Xây dựng một web portal chạy local, cho phép quản lý file từ nhiều dịch vụ lưu trữ đám mây (Google Drive, Dropbox, OneDrive) trong **một giao diện thống nhất**, không cần chuyển qua lại giữa các website của từng nhà cung cấp.

**Mục tiêu cụ thể:**

| #   | Mục tiêu                                                   | Ghi chú                                                |
| --- | ---------------------------------------------------------- | ------------------------------------------------------ |
| G1  | Duyệt file/thư mục của nhiều tài khoản cloud trong một nơi | Mỗi tài khoản là một "root" dưới một root ảo           |
| G2  | Kết nối / xem trạng thái / ngắt kết nối nhiều tài khoản    | Hỗ trợ cả nhiều tài khoản cùng một provider            |
| G3  | Thực hiện thao tác file cơ bản với trải nghiệm nhất quán   | Upload, download, tạo thư mục, đổi tên, xóa, xem trước |
| G4  | Tìm kiếm, lọc, sắp xếp file                                | Xem mục 5.3 về phạm vi "unified"                       |
| G5  | Xem dung lượng sử dụng từng tài khoản                      | Tùy provider có hỗ trợ                                 |
| G6  | Kiến trúc provider abstraction sạch                        | Thêm provider mới chỉ cần viết một adapter             |
| G7  | Rèn luyện kỹ năng                                          | Java enterprise, React + TypeScript, OAuth 2.0, Scrum  |

## 2. Lý do (Rationale)

- **Nhu cầu thực tế:** Dữ liệu cá nhân nằm rải rác trên nhiều dịch vụ; việc tìm và quản lý file phải mở nhiều tab, đăng nhập nhiều nơi.
- **Chỉ phục vụ một người dùng (bản thân):** loại bỏ độ phức tạp của multi-user, multi-tenant, hosting, billing, xác minh ứng dụng OAuth công khai.
- **Chạy local:** dữ liệu và token không rời khỏi máy cá nhân, giảm rủi ro bảo mật và chi phí vận hành.
- **Học tập:** dự án có đủ độ rộng (backend, frontend, tích hợp bên thứ ba, bảo mật) để thực hành một stack hiện đại và quy trình Scrum đầy đủ ở quy mô nhỏ.
- **Thiết kế để mở rộng:** provider abstraction giúp kiểm chứng kỹ năng thiết kế kiến trúc, không chỉ viết CRUD.

## 3. Tiêu chí thành công (Success Criteria)

### 3.1 Tiêu chí chức năng (MVP hoàn thành khi)

1. Đăng nhập / đăng xuất portal thành công.
2. Kết nối được Google Drive, Dropbox và OneDrive qua OAuth 2.0.
3. Duyệt file/thư mục của các tài khoản đã kết nối, có breadcrumb, grid/list view.
4. Tìm kiếm theo tên, lọc theo loại file và provider, sắp xếp theo tên/kích thước/ngày sửa.
5. Upload, download, tạo thư mục, đổi tên, xóa file/thư mục (với hộp thoại xác nhận).
6. Xem trước ảnh và PDF.
7. Xem dung lượng từng tài khoản (nếu provider hỗ trợ).
8. Ngắt kết nối tài khoản: thu hồi token ở provider (nếu có thể) và xóa token đã lưu ở local.

### 3.2 Tiêu chí chất lượng (đo lường được)

| Tiêu chí                              | Ngưỡng mục tiêu                                                               |
| ------------------------------------- | ----------------------------------------------------------------------------- |
| Tải danh sách một thư mục (≤ 200 mục) | ≤ 3 giây trong điều kiện mạng bình thường                                     |
| Một provider lỗi / mất kết nối        | Chỉ hiển thị lỗi tại khu vực của provider đó; phần còn lại vẫn dùng được      |
| Token hết hạn                         | Tự refresh; nếu thất bại, hiển thị trạng thái "cần kết nối lại"               |
| Upload                                | Hỗ trợ file tối thiểu 100 MB (ghi rõ giới hạn thực tế của từng provider)      |
| Bảo mật token                         | Token được mã hóa khi lưu, không xuất hiện trong log                          |
| Khởi động                             | Một lệnh (`docker compose up` + script) là chạy được toàn bộ môi trường       |
| Chất lượng code                       | Có test cho provider adapter (mock) và luồng chính; lint/type-check không lỗi |

### 3.3 Tiêu chí quy trình (Scrum)

- Mỗi sprint kết thúc bằng một bản **demo chạy được**.
- Mỗi hạng mục "Done" thỏa Definition of Done (xem `project_plan.md`).

## 4. Điều kiện & ràng buộc (Constraints & Assumptions)

**Ràng buộc**

- Chỉ một người dùng (chủ dự án); chạy local, không deploy lên server công khai.
- Làm một mình, thời gian không cố định → dùng sprint ngắn, scope linh hoạt theo sprint.
- Không dùng tính năng tốn phí; mọi dịch vụ bên thứ ba dùng gói miễn phí.
- Không đồng bộ nền, không lưu nội dung file (chỉ lưu metadata kết nối và token).

**Giả định cần xác nhận sớm (spike ở Sprint 0/1)**

- Tạo được app OAuth ở chế độ test/development cho cả 3 provider bằng tài khoản cá nhân.
- Redirect URI dạng `http://localhost:<port>/...` được cả 3 provider chấp nhận.
- Chỉ hỗ trợ **tài khoản cá nhân** ở MVP (đặc biệt với Microsoft); tài khoản công việc/trường học để sau.
- Các chính sách OAuth của provider (scope, thời hạn refresh token ở chế độ test…) cần đối chiếu với tài liệu chính thức hiện hành khi bắt đầu.

**Điều kiện tiên quyết về môi trường**

- Máy có Docker (cho PostgreSQL), JDK LTS, Node.js LTS, pnpm.
- Tài khoản test của mỗi provider (có thể dùng tài khoản thật của bản thân, khuyến nghị có thêm tài khoản phụ để thử thao tác xóa/ghi đè).

## 5. Tính năng cốt lõi (Core Features)

### 5.1 Tài khoản & kết nối cloud

- Đăng nhập/đăng xuất portal (một người dùng local, xem `project_specs.md`).
- Kết nối Google Drive, Dropbox (OAuth 2.0 + PKCE) và OneDrive (Microsoft Identity Platform).
- Xem danh sách tài khoản đã kết nối và trạng thái (hoạt động / cần kết nối lại / lỗi).
- Ngắt kết nối tài khoản.

### 5.2 File Explorer

- Duyệt thư mục, breadcrumb, grid/list view, xem metadata cơ bản.
- Phân trang / tải thêm cho thư mục lớn.

### 5.3 Tìm kiếm, lọc, sắp xếp (phạm vi MVP)

- **Tìm kiếm:** gửi song song tới các provider đang chọn (fan-out), kết quả **nhóm theo provider**, chưa hợp nhất xếp hạng.
- **Lọc:** theo loại file và theo provider.
- **Sắp xếp:** áp dụng trong phạm vi một thư mục / một trang kết quả của một provider. Không có index toàn cục.

### 5.4 Thao tác file

- Upload, download, tạo thư mục, đổi tên, xóa, xem trước ảnh/PDF.

### 5.5 Dashboard

- Danh sách tài khoản, trạng thái kết nối, dung lượng đã dùng / còn lại (nếu hỗ trợ).

### 5.6 Ngoài phạm vi MVP (Backlog tương lai)

Chuyển file giữa các cloud (ưu tiên cao nhất sau MVP vì giá trị thực tế lớn), đồng bộ nền, phát hiện file trùng, tìm kiếm AI, quản lý chia sẻ nâng cao, quản lý phiên bản file, truy cập offline, multi-user/multi-tenant, di chuyển/sao chép file trong cùng tài khoản, chọn nhiều file / thao tác hàng loạt, thùng rác & khôi phục, mục "Shared with me".

## 6. Nguyên tắc dẫn dắt

1. Nhỏ và thực dụng: ưu tiên một luồng chạy được từ đầu đến cuối hơn là nhiều tính năng dang dở.
2. Demo sớm, demo thường xuyên.
3. Provider abstraction sạch quan trọng hơn số lượng tính năng.
4. Bảo mật token và dữ liệu cá nhân là yêu cầu bắt buộc, kể cả khi chạy local.

