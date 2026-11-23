# Cloud Storage Portal — Project Specs

> Đặc tả kỹ thuật, rủi ro, bảo mật, việc cần làm và các thông tin hữu ích.
> Lưu ý: chính sách và giới hạn của Google/Dropbox/Microsoft thay đổi theo thời gian. Các mục đánh dấu **[Kiểm tra]** cần đối chiếu với tài liệu chính thức hiện hành khi bắt đầu triển khai.

---

## 1. Kiến trúc tổng quan

```
┌────────────┐   HTTP/JSON   ┌───────────────────────────┐        ┌──────────────┐
│  React SPA │ ────────────► │  Spring Boot Backend      │ ─────► │ Google Drive │
│ (localhost │ ◄──────────── │  ┌─────────────────────┐  │ ─────► │ Dropbox      │
│   :5173)   │   (stream)    │  │ Provider Abstraction│  │ ─────► │ OneDrive     │
└────────────┘               │  └─────────────────────┘  │        └──────────────┘
                             │  Auth · Token Vault · API │
                             └─────────────┬─────────────┘
                                           │
                                     ┌─────▼─────┐
                                     │PostgreSQL │  (user, tài khoản cloud, token mã hóa)
                                     └───────────┘
```

**Quyết định kiến trúc chính**

| Vấn đề | Quyết định cho MVP | Lý do |
|---|---|---|
| Nội dung file đi qua đâu? | **Backend làm proxy/stream** | Token không bao giờ lộ ra trình duyệt; xử lý thống nhất; local nên băng thông không phải vấn đề |
| Lưu file hoặc index? | **Không** | Giữ nhỏ, tránh đồng bộ |
| Token ở đâu? | Chỉ ở backend, mã hóa trong DB | Frontend không nhận token của provider |
| Giao tiếp với frontend | REST + OpenAPI | Sinh client TypeScript |
| Xác thực portal | Một người dùng local, session cookie `HttpOnly` | Đơn giản, an toàn khi cùng origin qua proxy dev |

## 2. Provider Abstraction

### 2.1 Interface đề xuất (khái niệm)

```java
public interface CloudProvider {
    ProviderType type();
    Capabilities capabilities();

    // Kết nối
    URI buildAuthorizationUrl(String state, String codeChallenge);
    TokenSet exchangeCode(String code, String codeVerifier);
    TokenSet refresh(TokenSet current);
    void revoke(TokenSet tokens);

    // Tài khoản
    AccountInfo getAccountInfo(TokenSet tokens);
    Optional<StorageQuota> getQuota(TokenSet tokens);

    // File
    Page<FileItem> list(TokenSet t, String folderId, String pageToken);
    Page<FileItem> search(TokenSet t, SearchQuery q, String pageToken);
    FileItem getMetadata(TokenSet t, String fileId);
    DownloadStream download(TokenSet t, String fileId);
    FileItem upload(TokenSet t, String parentId, String name, InputStream data, long size);
    FileItem createFolder(TokenSet t, String parentId, String name);
    FileItem rename(TokenSet t, String fileId, String newName);
    void delete(TokenSet t, String fileId);
}
```

### 2.2 Mô hình dữ liệu thống nhất

| Trường `FileItem` | Ý nghĩa |
|---|---|
| `id` | ID gốc của provider (Dropbox dùng `id:...`; path chỉ lưu ở trường hiển thị) |
| `displayPath` | Đường dẫn để hiển thị/breadcrumb, không dùng làm khóa |
| `globalId` | `{accountId}:{id}`, duy nhất trong portal |
| `accountId`, `provider` | Thuộc tài khoản/provider nào |
| `name`, `type` (`FILE`/`FOLDER`), `mimeType` | |
| `size` (nullable), `modifiedAt` | Một số loại file/provider không có size |
| `parentId` | Nullable; mọi provider chỉ có tối đa 1 parent |
| `isShortcut`, `shortcutTargetId` | Item là shortcut (Drive, OneDrive `remoteItem`) và ID đích |
| `isNativeDoc` | File Google Docs/Sheets… cần export thay vì tải trực tiếp |
| `previewable`, `thumbnailUrl` | Hỗ trợ preview |

### 2.3 Capability matrix (cập nhật 23/11/2026)

Ký hiệu: ✅ đã xác minh · ⚠️ cần kiểm thử thực tế · ⏳ chưa xác minh (làm ở sprint ghi trong ngoặc).

| Khả năng | Google Drive | Dropbox | OneDrive (tài khoản cá nhân) |
|---|---|---|---|
| Cách định danh file | ✅ ID: `files/{fileId}` | ✅ Path (`/folder/file`) hoặc ID (`id:...`) | ✅ ID (`/drive/items/{item-id}`) hoặc path (`/drive/root:/path/to/file`) |
| **ID dùng trong portal (quyết định)** | ID gốc | `id:...` (path chỉ để hiển thị/breadcrumb) | ID (path chỉ để hiển thị/breadcrumb) |
| Phân trang | ⏳ `pageToken` (S1) | ⏳ `cursor` (S4) | ⏳ `@odata.nextLink` (S5) |
| Tìm kiếm theo tên phía server | ⏳ (S3) | ⏳ (S4) | ⏳ (S5) |
| Quota | ⏳ (S3) | ⏳ (S4) | ⏳ (S5) |
| Upload file lớn (session/resumable) | ⏳ (S2) | ⏳ upload session (S4) | ⏳ upload session (S5) |
| Xóa → thùng rác | ✅ `files.update(trashed=true)` | ✅ `files/delete_v2` (khôi phục bằng `files/restore`) | ✅ `DELETE /items/{id}` (vào Recycle Bin) |
| Xóa vĩnh viễn | ✅ `files.delete` | ✅ `files/permanently_delete`, **chỉ gói Business/Enterprise** → không dùng | ⚠️ `POST /drives/{drive-id}/items/{item-id}/permanentDelete`: tài liệu liệt kê hỗ trợ tài khoản cá nhân, nhưng có trả lời trên Microsoft Q&A cho rằng đây là lỗi tài liệu và API chỉ áp dụng cho OneDrive for Business/SharePoint (S5: thử thực tế) |
| Thumbnail | ✅ `thumbnailLink` (ngắn hạn) | ✅ `files/get_thumbnail_v2` | ✅ `/items/{id}/thumbnails` |
| Preview tài liệu nhúng của provider | ✅ `webViewLink` (cần phiên đăng nhập Google đúng tài khoản trong trình duyệt) | ✅ `files/get_preview` (chuyển tài liệu sang PDF/HTML; ⚠️ kiểm tra loại file hỗ trợ ở S4) | ❌ `driveItem: preview` **không hỗ trợ tài khoản cá nhân** (Microsoft Learn: chỉ OneDrive for Business/SharePoint) |
| **Preview ảnh/PDF trong portal (quyết định)** | Stream qua backend, `inline` | Stream qua backend, `inline` | Stream qua backend, `inline` |
| Cấu trúc cha | ✅ Chỉ 1 parent; muốn xuất hiện ở nơi khác phải tạo **shortcut** (`application/vnd.google-apps.shortcut`) | ✅ 1 đường dẫn cha | ✅ 1 parent; có khái niệm shortcut/`remoteItem` cho thư mục chia sẻ |
| File "native" cần export | Có (Docs/Sheets/Slides) | Hầu như không | Hầu như không |

**Quyết định rút ra từ việc xác minh**

1. **ID là khóa chuẩn ở mọi provider.** Path dễ đổi khi rename/move (Dropbox, OneDrive); chỉ dùng path để hiển thị breadcrumb.
2. **MVP chỉ hỗ trợ xóa vào thùng rác.** Xóa vĩnh viễn không đồng đều giữa các provider (Dropbox cá nhân không có, OneDrive cá nhân chưa chắc), đồng thời là thao tác không thể hoàn tác (R11) → đưa vào backlog. Giao diện ghi rõ "chuyển vào thùng rác".
3. **Preview ảnh/PDF đi qua cùng endpoint stream với download** (chỉ khác header `Content-Disposition: inline`), không dùng preview nhúng của từng provider, vì: (a) OneDrive preview không dùng được với tài khoản cá nhân; (b) `webViewLink` của Google phụ thuộc đăng nhập Google trong trình duyệt, dễ sai tài khoản khi dùng đa tài khoản và có thể bị chặn nhúng iframe; (c) cần một cách thống nhất cho cả 3 provider; (d) endpoint stream đã phải có cho download. **Chỉ dùng API thumbnail gốc cho ảnh thu nhỏ ở grid view** (nên lấy qua backend vì `thumbnailLink` ngắn hạn). `files/get_preview` của Dropbox để dành cho backlog (xem tài liệu Office).
4. **Shortcut là loại item riêng.** Thêm `isShortcut` và `shortcutTargetId` vào `FileItem`; hiển thị biểu tượng shortcut, khi mở thì phân giải sang đích (xử lý ở Sprint 2).
5. **Dropbox permanent delete không nằm trong MVP**, nên không cần xử lý phân biệt loại gói tài khoản.

> Ghi chú ngày kiểm tra khi cập nhật bảng. Các ô ⏳ được điền ở sprint tương ứng.

## 3. Mô hình dữ liệu (PostgreSQL)

| Bảng | Trường chính |
|---|---|
| `users` | `id`, `username`, `password_hash`, `created_at` |
| `cloud_accounts` | `id`, `user_id`, `provider`, `external_account_id`, `display_name`, `email`, `access_token_enc`, `refresh_token_enc`, `expires_at`, `scopes`, `status` (`ACTIVE`/`NEEDS_RECONNECT`/`ERROR`), `created_at`, `updated_at` |
| `oauth_states` | `state`, `code_verifier_enc`, `provider`, `user_id`, `expires_at` (xóa sau khi dùng/hết hạn) |
| `app_settings` | cấu hình người dùng (view mặc định, sắp xếp mặc định…) |

Ràng buộc: unique `(provider, external_account_id)` để tránh kết nối trùng một tài khoản.

## 4. API (phác thảo)

```
POST   /api/auth/login | /api/auth/logout        GET /api/auth/me
GET    /api/accounts                              DELETE /api/accounts/{id}
GET    /api/oauth/{provider}/start                GET /api/oauth/{provider}/callback
GET    /api/accounts/{id}/quota
GET    /api/accounts/{id}/files?folderId=&pageToken=&sort=&type=
GET    /api/accounts/{id}/files/{fileId}          (metadata)
GET    /api/accounts/{id}/files/{fileId}/content  (download/preview, stream)
POST   /api/accounts/{id}/files                   (upload, multipart/stream)
POST   /api/accounts/{id}/folders
PATCH  /api/accounts/{id}/files/{fileId}          (rename)
DELETE /api/accounts/{id}/files/{fileId}
GET    /api/search?q=&providers=&type=            (fan-out, kết quả nhóm theo tài khoản)
```

Định dạng lỗi thống nhất (RFC 7807 Problem Details), có mã lỗi nội bộ như `PROVIDER_UNAUTHORIZED`, `PROVIDER_RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `QUOTA_EXCEEDED`.

## 5. Bảo mật

> Dù chạy local và chỉ một người dùng, portal giữ token có quyền đọc/ghi/xóa toàn bộ dữ liệu cloud cá nhân. Đây là mục tiêu giá trị cao, cần xử lý nghiêm túc.

### 5.1 Checklist bảo mật

**OAuth**
- [ ] Dùng Authorization Code flow, **PKCE** cho mọi provider hỗ trợ.
- [ ] Tham số `state` ngẫu nhiên, gắn với phiên, dùng một lần, có hạn (vài phút); từ chối callback sai `state`.
- [ ] Redirect URI đăng ký chính xác; không dùng wildcard.
- [ ] Xin **scope tối thiểu** cần thiết; ghi rõ scope từng provider trong tài liệu.
- [ ] Không bao giờ gửi access/refresh token của provider xuống trình duyệt.

**Lưu trữ token & bí mật**
- [ ] Mã hóa access/refresh token trước khi ghi DB (AES-256-GCM, nonce riêng cho từng bản ghi).
- [ ] Khóa mã hóa lấy từ biến môi trường / file ngoài repo; có hướng dẫn tạo khóa; không commit.
- [ ] Client secret của OAuth app chỉ nằm ở `.env.local` (đã `.gitignore`); chỉ commit `.env.example`.
- [ ] Không log token, `Authorization` header, code OAuth, hay nội dung file; che (mask) trong log.
- [ ] Cân nhắc cơ chế xoay khóa (key rotation) ở backlog.

**Ngắt kết nối**
- [ ] Gọi endpoint thu hồi token của provider (nếu có), sau đó xóa token và metadata liên quan ở DB.
- [ ] Nếu thu hồi ở provider thất bại, vẫn xóa local nhưng báo cho người dùng biết cần thu hồi thủ công.

**Xác thực & phiên của portal**
- [ ] Băm mật khẩu bằng Argon2id hoặc bcrypt; không lưu mật khẩu thô.
- [ ] Cookie `HttpOnly`, `SameSite=Lax/Strict`; CSRF protection cho các request thay đổi dữ liệu.
- [ ] Giới hạn số lần đăng nhập sai (rate limit đơn giản).
- [ ] Backend **chỉ bind `localhost`** (không mở ra mạng LAN) trừ khi chủ động thay đổi.

**API & dữ liệu**
- [ ] Kiểm tra quyền sở hữu: `accountId` trong URL phải thuộc người dùng hiện tại.
- [ ] Validate đầu vào (tên file, kích thước, kiểu MIME); chống path traversal khi xử lý tên file.
- [ ] Giới hạn kích thước upload và timeout hợp lý.
- [ ] Preview: đặt header `Content-Type` đúng, `X-Content-Type-Options: nosniff`; không render HTML/SVG không tin cậy trong cùng origin (hiển thị dạng tải xuống hoặc sandbox).
- [ ] Phụ thuộc: bật cảnh báo lỗ hổng (Dependabot/`npm audit`/OWASP dependency-check).

**Xóa dữ liệu**
- [ ] Hộp thoại xác nhận trước khi xóa; ghi rõ xóa vào thùng rác hay vĩnh viễn.
- [ ] Dùng tài khoản/thư mục test khi phát triển các thao tác ghi/xóa để tránh mất dữ liệu thật.

### 5.2 Quyền riêng tư
- Không lưu nội dung file; chỉ lưu metadata tối thiểu (tên hiển thị, email tài khoản, token mã hóa).
- Nếu sau này thêm cache/index metadata, phải cập nhật mục này và có cơ chế xóa.

## 6. Rủi ro & biện pháp giảm thiểu

| # | Rủi ro | Mức độ | Biện pháp |
|---|---|---|---|
| R1 | **Google**: scope duyệt toàn bộ Drive thuộc nhóm nhạy cảm/hạn chế; scope hẹp (`drive.file`) không duyệt được file cũ | Cao | Dự án cá nhân dùng app ở chế độ test với chính tài khoản của mình làm test user; xác nhận scope cần dùng ở Sprint 0 **[Kiểm tra]** |
| R2 | **Google ở chế độ test**: refresh token có thể hết hạn sau thời gian ngắn, buộc kết nối lại | Trung bình | Thiết kế luồng "Kết nối lại" mượt; kiểm tra chính sách hiện hành; cân nhắc các lựa chọn phù hợp cho dùng cá nhân **[Kiểm tra]** |
| R3 | **Microsoft**: tài khoản cá nhân khác tài khoản công việc/trường học; cấu hình app registration dễ sai (loại tài khoản, redirect) | Trung bình | MVP chỉ hỗ trợ tài khoản cá nhân; ghi cấu hình app registration vào README |
| R4 | **Dropbox**: chọn quyền "App folder" hay "Full Dropbox" ảnh hưởng đến khả năng duyệt | Trung bình | Chọn Full Dropbox cho mục đích cá nhân; ghi rõ trong tài liệu |
| R5 | Khác biệt API (path vs ID, phân trang, giới hạn upload, rate limit) làm abstraction bị rò rỉ | Cao | Làm provider thứ hai (Dropbox) sớm ở Sprint 4 để kiểm chứng; capability matrix; ADR khi sửa interface |
| R6 | Tìm kiếm/sắp xếp "unified" khó đúng vì không có index | Trung bình | Phạm vi MVP: fan-out + nhóm theo provider; sort trong từng provider (xem mục 7) |
| R7 | Upload/download file lớn: tốn RAM, timeout, đứt kết nối | Trung bình | Streaming, upload session/resumable, giới hạn kích thước ghi rõ |
| R8 | File Google Docs/Sheets không có kích thước và không tải trực tiếp được | Thấp | `isNativeDoc` + export sang PDF/Office |
| R14 | OneDrive cá nhân không hỗ trợ `driveItem: preview`; `permanentDelete` có tài liệu mâu thuẫn | Trung bình | Preview qua stream backend; MVP chỉ xóa vào thùng rác; thử `permanentDelete` thực tế ở Sprint 5 nếu muốn đưa vào backlog |
| R15 | `webViewLink`/preview gốc phụ thuộc phiên trình duyệt hoặc loại tài khoản | Thấp | Không dùng cho MVP (xem quyết định 3 ở mục 2.3) |
| R9 | Rate limit của provider khi fan-out hoặc duyệt thư mục lớn | Trung bình | Retry với exponential backoff, tôn trọng `Retry-After`, giới hạn song song |
| R10 | Rò rỉ token do log/commit nhầm | Cao | Mask log, `.gitignore`, pre-commit quét bí mật (gitleaks) |
| R11 | Mất dữ liệu thật do bug ở thao tác xóa/ghi đè khi dev | Cao | Tài khoản test; xác nhận trước khi xóa; ưu tiên xóa vào thùng rác |
| R12 | Bỏ dở dự án / scope creep khi làm một mình | Trung bình | Scrum, sprint nhỏ, demo sớm, backlog kỷ luật (xem `project_plan.md`) |
| R13 | Thay đổi chính sách/API của provider | Thấp–TB | Cô lập trong adapter; test với mock; ghi ngày kiểm tra trong capability matrix |

## 7. Quyết định về hành vi sản phẩm

| Chủ đề | Quyết định cho MVP |
|---|---|
| Root | Root ảo liệt kê các tài khoản đã kết nối; mở một tài khoản để vào thư mục gốc của nó |
| Tìm kiếm | Fan-out song song; kết quả nhóm theo tài khoản; có timeout và trạng thái lỗi riêng từng nhóm |
| Sắp xếp / lọc | Áp dụng trong phạm vi một thư mục hoặc một nhóm kết quả; tải trang tiếp theo thì giữ nguyên tiêu chí. Ưu tiên sort phía server nếu provider hỗ trợ, nếu không thì sort trên trang đã tải và ghi chú giới hạn ở UI |
| Xóa | **Chỉ vào thùng rác** ở cả 3 provider; luôn có hộp thoại xác nhận ghi rõ "chuyển vào thùng rác"; xóa vĩnh viễn nằm trong backlog |
| Preview | Ảnh/PDF stream `inline` qua backend; thumbnail dùng API thumbnail gốc của provider |
| Shortcut | Hiển thị biểu tượng shortcut; mở thì phân giải sang item đích |
| Đặt tên trùng khi upload | Hỏi người dùng (ghi đè / đổi tên) hoặc để provider tự đổi tên; chốt ở Sprint 2 |
| Nhiều tài khoản cùng provider | Có hỗ trợ (unique theo `external_account_id`) |
| Tài khoản Microsoft | Chỉ tài khoản cá nhân ở MVP |
| Provider lỗi | Hiển thị lỗi cục bộ, không làm hỏng toàn trang |
| Đăng nhập portal | Một người dùng; thông tin khởi tạo qua biến môi trường/seed; không có đăng ký |

## 8. Yêu cầu phi chức năng

| Nhóm | Yêu cầu |
|---|---|
| Hiệu năng | Liệt kê thư mục ≤ 200 mục trong ≤ 3 giây; danh sách lớn dùng phân trang/ảo hóa |
| Độ tin cậy | Retry có kiểm soát; lỗi một provider không ảnh hưởng provider khác |
| Khả năng quan sát | Log có cấu trúc, có correlation ID, **không chứa dữ liệu nhạy cảm**; health check |
| Khả năng bảo trì | Provider mới = một adapter + test; ADR cho quyết định kiến trúc |
| Khả năng dùng | Responsive cơ bản; trạng thái loading/lỗi/rỗng đầy đủ; hỗ trợ bàn phím ở mức cơ bản (shadcn/Radix đã hỗ trợ tốt) |
| Kiểm thử | Unit + integration (Testcontainers, WireMock); E2E cho luồng chính (tùy chọn) |

## 9. Việc cần làm (Todo tổng hợp)

### Chuẩn bị (trước Sprint 0)
- [ ] Chốt Spring Boot vs Jakarta EE thuần; Gradle vs Maven.
- [ ] Cài Docker, JDK LTS, Node LTS, pnpm.
- [ ] Tạo repo Git, board Scrum, backlog ban đầu từ `project_goals.md`.

### OAuth apps (làm sớm, có thể mất thời gian chờ/cấu hình)
- [ ] Google Cloud project → bật Drive API → OAuth consent screen (chế độ test, thêm tài khoản của bạn làm test user) → tạo OAuth client (Web).
- [ ] Dropbox App Console → tạo app → chọn quyền truy cập → cấu hình redirect URI → bật các permission cần thiết.
- [ ] Microsoft Entra (Azure) App registration → hỗ trợ tài khoản cá nhân → redirect URI → API permissions (Graph: file, offline access) → client secret nếu cần.
- [ ] Ghi tất cả vào README (hướng dẫn tái tạo từ đầu) và `.env.example`.

### Kỹ thuật cốt lõi
- [ ] Thiết kế interface `CloudProvider` + model thống nhất (ADR).
- [ ] Token vault (mã hóa/giải mã, refresh, trạng thái tài khoản).
- [ ] Chuẩn hóa lỗi provider → mã lỗi nội bộ.
- [ ] Cơ chế retry/backoff, giới hạn song song cho fan-out.
- [ ] Bộ test với mock provider (WireMock) + dữ liệu mẫu.

### Hoàn thiện
- [ ] Quét bí mật trước mỗi commit (gitleaks hoặc tương đương).
- [ ] Rà soát checklist bảo mật (mục 5) trước khi gắn thẻ v1.0.
- [ ] Viết README và hướng dẫn xử lý sự cố (token hết hạn, redirect URI lệch…).

## 10. Câu hỏi mở (Open Questions)

1. Có muốn hỗ trợ tài khoản Microsoft công việc/trường học ở giai đoạn sau không? (Khi đó `driveItem: preview` mới dùng được.)
2. Upload file lớn: giới hạn kích thước mục tiêu cho MVP là bao nhiêu (đề xuất ≥ 100 MB)?
3. Có cần hiển thị thumbnail trong grid view ngay từ MVP, hay dùng icon theo loại file?
4. Xử lý trùng tên khi upload: ghi đè, đổi tên tự động, hay hỏi người dùng?
5. Dùng session cookie hay JWT cho xác thực portal?
6. Có muốn đưa "chuyển file giữa các cloud" lên ngay sau Sprint 6 không?

## 11. Thuật ngữ & tham khảo hữu ích

| Thuật ngữ | Nghĩa |
|---|---|
| PKCE | Proof Key for Code Exchange, tăng bảo mật Authorization Code flow |
| Fan-out | Gửi cùng một truy vấn tới nhiều provider song song rồi gom kết quả |
| ADR | Architecture Decision Record, ghi lại quyết định kiến trúc và lý do |
| DoD / DoR | Definition of Done / Definition of Ready |
| Capability matrix | Bảng so sánh khả năng từng provider |

**Tài liệu chính thức cần đọc khi bắt đầu** **[Kiểm tra]**: Google Drive API (scopes, quota, upload), Dropbox HTTP API (files, upload sessions, OAuth), Microsoft Graph (driveItem, upload sessions) và Microsoft Identity Platform (OAuth, app registration), OAuth 2.0 Security Best Current Practice (RFC 9700) và RFC 7636 (PKCE), OWASP ASVS / Cheat Sheet Series.
