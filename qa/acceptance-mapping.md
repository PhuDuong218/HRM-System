# Đối chiếu nghiệm thu nhiệm vụ Xuân Anh

| Task | Chức năng | Kết luận QA hiện tại |
|---|---|---|
| FE-001 | Layout, route, phiên, kết nối | Build/đăng nhập ADMIN và route smoke có bằng chứng; role matrix toàn bộ chưa đủ |
| FE-002 | Nhân viên/hồ sơ | GET scope/privacy một số actor PASS; responsive danh sách PASS; CRUD form và toàn bộ validation chưa nghiệm thu |
| FE-003 | Phòng ban/trưởng phòng/chức vụ | Route smoke PASS; chưa xác nhận đầy đủ create/update/end-term/primary action |
| FE-004 | Phân công/điều chuyển | Route smoke; assignment concurrency test hiện có PASS, không thay thế workflow UI |
| FE-005 | Trình độ/bằng cấp | Route smoke; chưa nghiệm thu đầy đủ primary/date validation/CRUD |
| FE-006 | Loại nghỉ/quỹ phép/đơn phép | Route smoke và một số regression; chưa chạy trọn toàn bộ business edge case |
| FE-007 | Nghỉ việc/duyệt/chấm dứt | Route smoke; approval lock concurrency test hiện có PASS, job và rehire cần ca riêng |
| FE-008 | Lý do/audit | API audit trái quyền bị chặn trong probe; chưa đối chiếu đầy đủ before/after cho mọi thao tác |
| FE-009 | Tích hợp/bàn giao | Chưa đạt: suite đỏ và lỗi tài chính/bảo mật liên module |

accounts, allowances, decisions, payroll, payslips, reports là 6 placeholder trong bản frontend kiểm thử. Ghi NOT-IMPLEMENTED, không gọi là runtime bug và không tự nhận là nhiệm vụ Xuân Anh phải viết thay nhóm. Cần nhóm xác nhận người phụ trách và deadline trước nghiệm thu chung.
