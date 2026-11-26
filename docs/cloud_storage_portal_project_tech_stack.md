# Cloud Storage Portal — Project Tech Stack

> Dự án cá nhân, chạy local. Mục tiêu của stack: hiện đại, phổ biến, dễ học, đủ cho quy mô nhỏ.
> Không ghi số phiên bản cố định; khi khởi tạo dự án, dùng bản **LTS/stable mới nhất** và ghi lại vào file này.

---

## 1. Tổng quan

| Lớp           | Công nghệ                                          |
| ------------- | -------------------------------------------------- |
| Cấu trúc repo | Monorepo                                           |
| Backend       | Java (LTS) + **Spring Boot** (nền tảng Jakarta EE) |
| Database      | PostgreSQL                                         |
| Frontend      | React + TypeScript + Vite                          |
| UI            | shadcn/ui + Tailwind CSS                           |
| Chạy local    | Docker Compose (PostgreSQL), script chạy dev       |

## 2. Lưu ý về "J2EE"

"J2EE" là tên cũ; từ khi chuyển sang Eclipse Foundation, nền tảng này gọi là **Jakarta EE** (namespace `jakarta.*`). Có hai hướng:

| Lựa chọn                                      | Ưu điểm                                                                                              | Nhược điểm                                                                            |
| --------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| **Spring Boot** (khuyến nghị)                 | Phổ biến nhất, tài liệu và cộng đồng lớn, tích hợp OAuth2 Client, Security, JPA sẵn, khởi động nhanh | Không phải "Jakarta EE thuần" (dù dùng các spec Jakarta như Servlet, JPA, Validation) |
| Jakarta EE thuần (Quarkus / Payara / WildFly) | Sát chuẩn spec, hợp nếu muốn học riêng Jakarta EE                                                    | Ít tài liệu cho tích hợp OAuth đa provider, nhiều cấu hình hơn                        |

→ **✅ Đã quyết định:** dùng **Spring Boot** (ghi nhận ngày 23/11/2026). Các spec Jakarta (Servlet, JPA, Validation…) vẫn được dùng bên dưới.

## 3. Monorepo

```
cloud-storage-portal/
├── apps/
│   ├── backend/            # Spring Boot (Maven hoặc Gradle)
│   └── frontend/           # React + TS + Vite
├── packages/               # (tùy chọn) types/API client dùng chung
│   ├── ui/                  # shadcn/ui primitives shared by frontend apps
│   ├── contracts/           # Shared TypeScript interfaces and API types
│   ├── api-client/          # Shared fetch client and API error handling
│   └── i18n/                # Shared localization dictionaries
├── docs/                   # goals, tech stack, plan, specs, ADR
├── infra/
│   └── docker-compose.yml  # PostgreSQL (+ pgAdmin tùy chọn)
├── scripts/                # script chạy dev, seed, kiểm tra môi trường
├── .editorconfig
└── README.md
```

- Công cụ quản lý workspace phía JS: **pnpm workspaces** (đủ dùng; chỉ thêm Turborepo/Nx nếu thấy cần).
- Backend build bằng **Gradle hoặc Maven** nằm trong `apps/backend`, không cần trộn với pnpm.
- Sinh API client cho frontend từ **OpenAPI** (springdoc-openapi → openapi-typescript hoặc orval) để đồng bộ kiểu dữ liệu.
- Frontend apps dùng chung shadcn/ui primitives, contracts, API client và i18n từ các workspace package ở `packages/`.

## 4. Backend

| Mục            | Lựa chọn                                                                    | Ghi chú                                                      |
| -------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Ngôn ngữ       | Java LTS mới nhất                                                           | Dùng record, sealed types cho model                          |
| Framework      | Spring Boot (Web MVC)                                                       | Cân nhắc virtual threads cho I/O song song                   |
| Bảo mật        | Spring Security                                                             | Login local một người dùng, session cookie hoặc JWT ngắn hạn |
| OAuth provider | Tự viết `ProviderAdapter` + Spring `RestClient`/`WebClient`                 | Dùng SDK chính thức chỉ khi giúp ích rõ ràng                 |
| Persistence    | Spring Data JPA + Hibernate                                                 | Chỉ lưu user, tài khoản cloud, token (mã hóa), cài đặt       |
| Migration      | Flyway                                                                      | Migration versioned, chạy tự động khi khởi động              |
| Validation     | Jakarta Validation                                                          |                                                              |
| API docs       | springdoc-openapi                                                           | Nguồn sinh client TypeScript                                 |
| Mã hóa token   | AES-GCM (JCA) hoặc Spring Security Crypto / Tink                            | Khóa lấy từ biến môi trường                                  |
| Test           | JUnit 5, Mockito, **Testcontainers** (PostgreSQL), WireMock (mock provider) |                                                              |
| Chất lượng     | Spotless/Checkstyle, (tùy chọn) SpotBugs                                    |                                                              |

## 5. Database

- **PostgreSQL** chạy bằng Docker Compose, volume cục bộ.
- Chỉ lưu: `users`, `cloud_accounts` (token mã hóa), `oauth_states` (tạm thời), `app_settings`.
- **Không lưu nội dung file** và không có bảng index file trong MVP.

## 6. Frontend

| Mục            | Lựa chọn                                                       | Ghi chú                                                     |
| -------------- | -------------------------------------------------------------- | ----------------------------------------------------------- |
| Build tool     | Vite                                                           |                                                             |
| Framework      | React + TypeScript (strict mode)                               |                                                             |
| UI components  | **shadcn/ui** (Radix UI bên dưới)                              | Copy component vào repo, dễ tùy biến                        |
| Styling        | **Tailwind CSS**                                               |                                                             |
| Icon           | lucide-react                                                   | Đi kèm shadcn/ui                                            |
| Routing        | React Router hoặc TanStack Router                              |                                                             |
| Server state   | **TanStack Query**                                             | Cache, phân trang, retry, trạng thái lỗi theo từng provider |
| Client state   | Zustand (chỉ khi cần)                                          | Tránh dùng sớm                                              |
| Form           | React Hook Form + Zod                                          |                                                             |
| Bảng/danh sách | TanStack Table; (tùy chọn) TanStack Virtual cho thư mục lớn    |                                                             |
| Preview        | `<img>` cho ảnh; `react-pdf` hoặc `<iframe>`/`<embed>` cho PDF | Chọn ở Sprint 3                                             |
| Test           | Vitest + React Testing Library; (tùy chọn) Playwright cho E2E  |                                                             |
| Chất lượng     | ESLint, Prettier, `tsc --noEmit`                               |                                                             |

## 7. Công cụ phát triển & quy trình

| Mục                     | Lựa chọn                                                         |
| ----------------------- | ---------------------------------------------------------------- |
| Quản lý source          | Git, Conventional Commits, nhánh ngắn hạn theo feature           |
| Quản lý Scrum           | GitHub Projects (hoặc Jira/Trello) cho backlog, sprint board     |
| CI (tùy chọn, local)    | GitHub Actions chạy lint + test, hoặc git hooks (husky/lefthook) |
| Quản lý bí mật          | File `.env.local` (không commit) + `.env.example`                |
| Tài liệu quyết định     | ADR ngắn trong `docs/adr/`                                       |
| Kiểm tra OAuth thủ công | Tài khoản test của từng provider                                 |

## 8. Cổng & cấu hình local (đề xuất)

| Thành phần            | Địa chỉ                                               |
| --------------------- | ----------------------------------------------------- |
| Frontend (Vite)       | `http://localhost:5173`                               |
| Backend (Spring Boot) | `http://localhost:8080`                               |
| PostgreSQL            | `localhost:5432`                                      |
| OAuth redirect URI    | `http://localhost:8080/api/oauth/{provider}/callback` |

> Dùng `localhost` nhất quán (không trộn `127.0.0.1`), vì redirect URI phải khớp chính xác với cấu hình ở provider.

## 9. Quyết định mở (cần chốt ở Sprint 0)

1. ~~Spring Boot hay Jakarta EE thuần?~~ → **Đã chốt: Spring Boot.**
2. ~~Gradle hay Maven?~~ → **Đã chốt: Maven.**
3. ~~Session cookie (đơn giản, an toàn khi cùng origin qua proxy Vite) hay JWT?~~ → **Đã chốt: JWT.**
4. ~~Dùng SDK chính thức của provider hay gọi REST trực tiếp?~~ → **Đã chốt: SDK chính thức của provider.**
5. ~~Router: React Router hay TanStack Router?~~ → **Đã chốt: TanStack Router.**
