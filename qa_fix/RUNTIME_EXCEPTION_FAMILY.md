# Rà soát RuntimeException nghiệp vụ — CHƯA ÁP sửa module lương

| Vị trí | Guard/loại lỗi | HTTP đề xuất | Trạng thái |
|---|---|---|---|
| PayrollPeriodService.createPeriod: khoảng ngày đảo | Dữ liệu đầu vào không hợp lệ |400| QA-07 có diff đề xuất |
| PayrollPeriodService.createPeriod: mã đã tồn tại | Xung đột mã |409| QA-06 có diff đề xuất |
| PayrollBatchService.lockBatch: chưa tính | Sai trạng thái nghiệp vụ |409| QA-09 có diff đề xuất |
| PayrollBatchService.publishBatch: chưa DA_CHOT | Sai thứ tự công bố |409| QA-08 có diff đề xuất |
| PayrollPeriodService.get/update: không tìm thấy | Không tồn tại |404| Cùng họ, CHƯA SỬA |
| PayrollPeriodService.updatePeriod: đảo ngày/mã trùng | Dữ liệu/xung đột |400/409| Cùng họ, CHƯA SỬA |
| PayrollBatchService.createDraftBatch: kỳ không có/nháp đã có | Không tồn tại/xung đột |404/409| Cùng họ, CHƯA SỬA |
| PayrollBatchService.lockBatch: trạng thái khác NHAP | State-machine |409| Cùng họ, CHƯA SỬA |
| PayrollBatchService.publishBatch: đã công bố | State-machine/idempotency |409 hoặc kết quả idempotent theo đặc tả| CHƯA SỬA |
| PayrollBatchService.createAdjustment: không có batch/không chốt/đã có nháp | Lookup/state-machine |404/409| CHƯA SỬA |
| PayrollExecutionService.calculateBatch: không có batch/kỳ hoặc đã chốt/công bố | Lookup/state-machine |404/409| CHƯA SỬA |

Không bắt mọi RuntimeException thành400/409 ở GlobalExceptionHandler vì sẽ che lỗi lập trình/DB ngoài dự kiến. Đổi exception tại guard nghiệp vụ xác định, giữ rollback transaction. Chưa sửa handler chung, công thức lương hay state-machine.
