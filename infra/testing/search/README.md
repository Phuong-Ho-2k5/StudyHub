# Kiểm chứng tìm kiếm ngữ nghĩa

`collection/` là bộ yêu cầu Postman v3 lưu cùng mã nguồn. Chuẩn bị môi trường cục bộ có `baseUrl`, `accessToken`, `courseId`, `emptyCourseId`, `foreignCourseId`, `query`, `topK`. Hai khóa học đầu thuộc người dùng hiện tại: một có chỉ mục đã công bố, một không có chỉ mục phù hợp; khóa học cuối thuộc người khác. Không lưu token thật trong Git.

```powershell
postman collection lint infra/testing/search/collection
postman collection run infra/testing/search/collection --environment <duong-dan-moi-truong-cuc-bo>
```

Các yêu cầu kiểm tra thành công, chỉ mục rỗng, K không hợp lệ và quyền Course. Bộ yêu cầu được kiểm tra cấu trúc bằng lệnh lint; hành vi API được kiểm chứng riêng bằng kiểm thử HTTP tích hợp PostgreSQL. Không tự khẳng định đã chạy bộ yêu cầu vào backend của người dùng.

`relevance-fixtures.json` chứa 10 đoạn tiếng Việt và 10 câu hỏi có nhãn nguồn đúng. Kiểm thử tùy chọn `ai-service/tests/test_search_relevance.py` dùng E5 thật để đo tỷ lệ có nguồn đúng trong 5 kết quả đầu, yêu cầu ít nhất 8/10. Bộ mẫu ngày 27/10/2026 đạt 10/10. Đây là kiểm tra mô hình trên tập nhỏ; thứ hạng SQL, quyền và phiên bản được kiểm chứng độc lập trong `DocumentChunkRepositoryIT` và `DocumentIngestionApiIT`.

Từ thư mục `ai-service`:

```powershell
$env:RUN_EMBEDDING_TESTS = '1'
.\.venv\Scripts\python.exe -m pytest tests/test_search_relevance.py -q -s
Remove-Item Env:RUN_EMBEDDING_TESTS
```

Nhật ký mô phỏng Postman và kết quả chạy kiểm thử lưu dưới `logs/`, không đưa vào Git. Dịch vụ AI giả lập chứng minh HTTP/lỗi, không chứng minh chất lượng ngữ nghĩa; bộ kiểm thử tìm kiếm này không kiểm tra API sinh câu trả lời bằng LLM.
