# Bản vá đã áp vào working tree

`ALL_APPLIED.diff` là bản vá tổng từ snapshot trước lượt sửa (không phải từ HEAD). QA-01..05 là các góc nhìn review theo lỗi, có file chồng nhau; **không áp nối tiếp các file QA-xx**. Source hiện tại đã chứa các thay đổi này.

Không bao gồm thay đổi có sẵn của chủ dự án/thành viên khác so với HEAD. SHA256 của bản gốc ở backup_original/SHA256.json. Không chạy git apply/restore tự động.
