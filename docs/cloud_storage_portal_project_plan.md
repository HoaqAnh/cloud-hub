# Cloud Storage Portal — Project Plan

> Triển khai theo Scrum (làm một mình, phiên bản gọn). Nguyên tắc: **mỗi sprint kết thúc bằng một bản demo chạy được**, và demo đầu tiên xuất hiện ở cuối Sprint 1.

---

## 1. Quy trình Scrum cho dự án cá nhân

| Hạng mục | Cách áp dụng |
|---|---|
| Vai trò | Bạn đảm nhiệm cả Product Owner, Scrum Master, Developer |
| Độ dài sprint | 2 tuần (có thể rút còn 1 tuần nếu thời gian rảnh ít và đều) |
| Product Backlog | GitHub Projects / Trello; mỗi mục là một user story |
| Sprint Planning (≈ 1 giờ) | Chọn story theo năng lực thực tế (velocity), đặt Sprint Goal bằng 1 câu |
| Daily (≈ 5 phút, viết ra) | Hôm qua làm gì / hôm nay làm gì / vướng gì |
| Sprint Review | Tự quay video hoặc chạy demo theo kịch bản; ghi lại kết quả |
| Retrospective (≈ 30 phút) | Giữ / Bỏ / Thử; ghi vào `docs/retro/` |
| Time-box | Hết sprint mà story chưa Done thì đưa lại backlog, không kéo dài sprint |

### Định nghĩa user story
`Là chủ sở hữu portal, tôi muốn <việc> để <lợi ích>.` kèm tiêu chí chấp nhận (acceptance criteria) dạng Given/When/Then.

### Definition of Ready (story sẵn sàng đưa vào sprint)
- Có tiêu chí chấp nhận rõ ràng.
- Nhỏ đủ để xong trong ≤ 2–3 ngày.
- Đã biết phụ thuộc (ví dụ cần app OAuth của provider nào).

### Definition of Done
- Chạy được end-to-end trên local với tài khoản thật/test.
- Có test cho logic chính (unit/integration, provider được mock).
- Lint, type-check, build đều pass.
- Không log token hoặc dữ liệu nhạy cảm.
- Xử lý trạng thái loading / lỗi / rỗng ở UI.
- Cập nhật tài liệu (README hoặc specs) nếu có thay đổi hành vi hoặc cấu hình.

## 2. Lộ trình tổng quan

```
Sprint 0  Nền móng          → môi trường chạy được, spike OAuth
Sprint 1  ★ DEMO 1          → login + kết nối Google Drive + xem file gốc
Sprint 2  ★ DEMO 2          → duyệt đầy đủ + download + upload (Drive)
Sprint 3  ★ DEMO 3 (v0.1)   → CRUD file, preview, quota, tìm kiếm (Drive hoàn chỉnh)
Sprint 4  ★ DEMO 4          → Dropbox + root ảo thống nhất (kiểm chứng abstraction)
Sprint 5  ★ DEMO 5          → OneDrive + tìm kiếm/lọc xuyên provider
Sprint 6  ★ MVP v1.0        → hardening, bảo mật, trải nghiệm lỗi, tài liệu
```

Ước lượng: **~13–15 tuần** với sprint 2 tuần. Đây là ước lượng ban đầu, cần hiệu chỉnh sau Sprint 1 dựa trên velocity thực tế.

## 3. Chi tiết từng phase / sprint

### Sprint 0 — Nền móng (3–5 ngày, không tính là sprint đầy đủ)
**Sprint Goal:** Một lệnh dựng được môi trường; đã xác nhận OAuth khả thi.

- [x] Khởi tạo monorepo, cấu trúc thư mục, `.editorconfig`, `.env.example`.
- [x] Docker Compose cho PostgreSQL; Flyway migration đầu tiên.
- [x] Skeleton backend (Spring Boot) + endpoint health check.
- [x] Skeleton frontend (Vite + React + TS + Tailwind + shadcn/ui), gọi được health check qua proxy.
- [x] Sinh OpenAPI → TypeScript client (hoặc quyết định tạm viết tay).
- [x] Lint/format/test chạy được cả hai phía.
- [x] **Spike OAuth:** tạo app OAuth cho Google trước (Dropbox, OneDrive tạo sớm nhất có thể); thử redirect `localhost`; ghi lại scope tối thiểu, loại tài khoản hỗ trợ, thời hạn refresh token ở chế độ test.
- [x] Chốt các quyết định mở trong `project_tech_stack.md` (mục 9), ghi ADR ngắn.
- [x] Tạo backlog ban đầu và board Scrum.

**Kết quả:** `docker compose up` + chạy backend/frontend → thấy trang trống kết nối được backend.

---

### Sprint 1 — ★ DEMO 1: "Đăng nhập, kết nối Google Drive, xem file"
**Sprint Goal:** Tôi đăng nhập portal, kết nối Google Drive và thấy danh sách file ở thư mục gốc.

- [x] Đăng nhập/đăng xuất local (một người dùng, mật khẩu băm bcrypt/argon2; thông tin khởi tạo từ biến môi trường hoặc seed).
- [x] Layout ứng dụng (sidebar, header), trang Login, route được bảo vệ.
- [x] Thiết kế `CloudProvider` interface + model thống nhất `FileItem`, `AccountInfo`, `Capabilities`.
- [x] OAuth 2.0 Google: `state` + PKCE, callback, lưu token **mã hóa** vào DB.
- [x] `GoogleDriveProvider`: lấy thông tin tài khoản, liệt kê thư mục gốc.
- [x] Trang Dashboard: danh sách tài khoản + trạng thái kết nối.
- [x] Trang Explorer tối giản: liệt kê file gốc (list view).
- [x] Tự động refresh access token.

**Kịch bản demo:** Login → Connect Google Drive → thấy tài khoản trên Dashboard → mở Explorer thấy file thật.

---

### Sprint 2 — ★ DEMO 2: "Duyệt đầy đủ, tải xuống, tải lên"
**Sprint Goal:** Tôi duyệt cây thư mục Drive thoải mái, tải file xuống và tải file lên.

- [x] Điều hướng thư mục, breadcrumb, phân trang/tải thêm (page token); xử lý shortcut (hiển thị + phân giải sang đích).
- [x] Xác minh còn thiếu cho Drive: phân trang, upload resumable (cập nhật capability matrix).
- [x] Grid view và list view; hiển thị metadata cơ bản.
- [x] Download: backend stream qua proxy (không buffer toàn bộ file vào RAM); xử lý file Google Docs/Sheets (export sang PDF/Office).
- [x] Upload: giao diện chọn file + thanh tiến trình; upload resumable cho Drive.
- [x] Xử lý lỗi cơ bản: 401 (hết hạn), 403, 404, 429 (rate limit), mất mạng.

**Kịch bản demo:** Vào thư mục lồng nhau → tải một file PDF → tải lên một ảnh → thấy nó xuất hiện.

---

### Sprint 3 — ★ DEMO 3 (v0.1): "Drive hoàn chỉnh"
**Sprint Goal:** Mọi tính năng MVP hoạt động trọn vẹn với Google Drive.

- [x] Tạo thư mục, đổi tên, xóa **vào thùng rác** (hộp thoại xác nhận; xóa vĩnh viễn nằm trong backlog).
- [x] Preview ảnh và PDF: stream `inline` qua endpoint content; thumbnail grid dùng `thumbnailLink` qua backend.
- [x] Hiển thị dung lượng (quota) trên Dashboard.
- [x] Tìm kiếm theo tên (server-side của Drive), lọc theo loại, sắp xếp theo tên/kích thước/ngày sửa.
- [x] Ngắt kết nối: thu hồi token ở Google + xóa dữ liệu local.
- [x] Bộ test adapter với mock (WireMock) cho các thao tác chính.

**Kịch bản demo:** Chạy toàn bộ checklist tiêu chí MVP với Drive. **Đây là điểm mốc "có sản phẩm dùng được".**

---

### Sprint 4 — ★ DEMO 4: "Dropbox và root ảo thống nhất"
**Sprint Goal:** Hai provider cùng xuất hiện trong một giao diện; abstraction được kiểm chứng.

- [x] Rà soát và điều chỉnh interface sau khi có provider thứ hai (ghi lại những gì phải sửa → đầu vào cho retro). Điều chỉnh: controller dispatch theo `ProviderType`, account và root Explorer có huy hiệu provider, thao tác được đọc từ capability matrix.
- [x] OAuth Dropbox (PKCE) + `DropboxProvider` (xử lý khác biệt path vs ID, cursor phân trang, quota).
- [x] Root ảo: mỗi tài khoản là một "thư mục gốc" trong Explorer, có huy hiệu provider.
- [x] Đưa toàn bộ thao tác file của Sprint 2–3 qua Dropbox (ID `id:` làm khóa chuẩn, path chỉ để hiển thị); thumbnail ảnh qua `files/get_thumbnail_v2` (PDF không được Dropbox hỗ trợ, dùng biểu tượng file; preview PDF vẫn stream qua backend).
- [x] Xác minh còn thiếu cho Dropbox: cursor, upload session, quota, tìm kiếm (điền ✅ hoặc trạng thái hỗ trợ trong capability matrix).
- [x] Capability matrix được hiện thực hóa trong code (UI ẩn/vô hiệu thao tác không hỗ trợ).

**Kịch bản demo:** Hai tài khoản trong một Explorer; upload/rename/delete ở cả hai.

---

### Sprint 5 — ★ DEMO 5: "OneDrive và tìm kiếm xuyên provider"
**Sprint Goal:** Cả ba provider hoạt động, tìm kiếm chạy trên nhiều tài khoản.

- [x] OAuth Microsoft Identity Platform (tài khoản cá nhân) + `OneDriveProvider` (Microsoft Graph).
- [x] Tìm kiếm fan-out song song, kết quả nhóm theo provider, timeout và lỗi theo từng provider.
- [x] Lọc theo provider/loại file; hiển thị trạng thái "đang tìm / lỗi / không có kết quả" cho từng nhóm.
- [x] Hỗ trợ nhiều tài khoản cùng một provider (nếu chưa làm).
- [ ] Xác minh còn thiếu cho OneDrive (tài khoản cá nhân): `@odata.nextLink`, upload session, quota, tìm kiếm; thử thực tế `permanentDelete` để ghi nhận vào backlog; thumbnail qua `/items/{id}/thumbnails`.

**Kịch bản demo:** Gõ một từ khóa → thấy kết quả từ cả 3 provider, tắt một provider vẫn dùng được các provider còn lại.

---

### Sprint 6 — ★ MVP v1.0: "Hardening và hoàn thiện"
**Sprint Goal:** MVP đạt đủ tiêu chí thành công trong `project_goals.md`.

- [ ] Rà soát bảo mật theo checklist trong `project_specs.md`.
- [ ] Trải nghiệm lỗi: token hết hạn → "Kết nối lại"; rate limit → thử lại có backoff; hết dung lượng khi upload.
- [ ] Hiệu năng: kiểm tra thư mục lớn, ảo hóa danh sách nếu cần.
- [ ] Bổ sung test E2E cho luồng chính (tùy chọn Playwright).
- [ ] README: hướng dẫn cài đặt, tạo app OAuth cho từng provider, cách chạy.
- [ ] Dọn nợ kỹ thuật, tổng kết dự án, cập nhật backlog tương lai.

## 4. Backlog tương lai (sau MVP, theo thứ tự đề xuất)

1. Chuyển/sao chép file giữa các cloud (giá trị thực tế cao nhất).
1b. Xóa vĩnh viễn (nếu provider hỗ trợ ổn định) và xem tài liệu Office qua `files/get_preview` của Dropbox.
2. Di chuyển/sao chép trong cùng tài khoản; chọn nhiều file, thao tác hàng loạt; kéo thả.
3. Thùng rác & khôi phục; "Shared with me".
4. Tìm kiếm tốt hơn (index metadata cục bộ).
5. Provider mới (Box, S3/MinIO…).

## 5. Quản lý rủi ro lịch trình

| Rủi ro | Biện pháp |
|---|---|
| Tích hợp OAuth/provider tốn thời gian hơn dự kiến | Spike sớm ở Sprint 0; dùng Google làm provider đầu tiên |
| Làm một mình, thời gian không đều | Sprint Goal nhỏ, cắt scope thay vì kéo dài sprint |
| Phạm vi trượt (scope creep) | Mọi ý tưởng mới vào backlog, không chèn giữa sprint |
| Bỏ dở giữa chừng | Demo sớm, mỗi sprint đều có bản dùng được; Sprint 3 là mốc tối thiểu đã có giá trị |

## 6. Kịch bản demo mẫu (dùng chung mỗi sprint)

1. Khởi động môi trường từ trạng thái sạch.
2. Chạy qua các story của sprint theo thứ tự.
3. Ghi lại: cái gì chạy tốt, cái gì chưa, cái gì cần đưa vào sprint sau.
