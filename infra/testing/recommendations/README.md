# Đề xuất Concept nên học tiếp

`GET /api/courses/{courseId}/recommendations/next-concepts?limit=5`, Bearer JWT.

`limit` là số nguyên 1–10, mặc định 5. Chỉ owner của Course được đọc kết quả. Backend lấy User từ JWT và tự đọc dữ liệu, không nhận confidence/evidence từ client. Không cần FastAPI, OpenAI hoặc API key; request không thay đổi dữ liệu học tập.

Response gồm `courseId`, `asOf` (UTC), `algorithmVersion`, `status`, `recommendations`, `learningEvidence`, `limitations`. Phiên bản thuật toán hiện tại là `next-concepts-v1`.

| Status | Ý nghĩa |
| --- | --- |
| `READY` | Có Concept sẵn sàng để học/ôn |
| `NO_CONCEPTS` | Course chưa có Concept |
| `ALL_MASTERED` | Graph hợp lệ và tất cả Concept có confidence >=80 |
| `BLOCKED_BY_GRAPH` | Không có kết quả vì graph có chu trình hoặc cạnh không hợp lệ |

Mỗi recommendation có `rank`, `conceptId`, `name`, `confidence`, `action`, `reasonCodes`, `unlocksCount`, `learningEvidenceIds`. `action` là `REVIEW` khi confidence <50, còn lại `CONTINUE`.

## Quy tắc lựa chọn

Concept có confidence >=80 được coi là đủ kiến thức; `status` không quyết định readiness. Chỉ đề xuất Concept dưới 80 mà toàn bộ prerequisite trực tiếp và gián tiếp đều đạt 80. Concept không có prerequisite được xét ngay.

Thứ tự: nhóm confidence <50 trước nhóm 50–79, rồi `unlocksCount` giảm dần, confidence tăng dần, ID tăng dần. `unlocksCount` đếm duy nhất các hậu duệ chưa thành thạo trên nhánh hợp lệ; con số này mô tả lợi ích nền tảng, không có nghĩa tất cả hậu duệ sẽ học được ngay sau một bước.

Node trong chu trình và tất cả node phụ thuộc chu trình bị chặn. Cạnh sai Course chặn dependent và các node phụ thuộc nó, không tiết lộ ID/tên prerequisite ngoài scope. Nhánh độc lập hợp lệ vẫn trả kết quả với limitation `CYCLIC_PREREQUISITES` hoặc `INVALID_PREREQUISITE_GRAPH`.

Graph được đọc đầy đủ trong giới hạn 2.000 Concept và 10.000 cạnh. Vượt giới hạn trả 422, không dùng graph bị cắt. Readiness dùng Kahn/topological traversal; tính số hậu duệ dùng BFS có visited, không đệ quy. Worst-case tổng thời gian O(V*(V+E)), bộ nhớ làm việc O(V+E).

## Evidence

Mỗi item tham chiếu fact `concept:{id}`, type `CONCEPT_CONFIDENCE`, chứa confidence, ngưỡng readiness, prerequisite đã thỏa và unlocksCount. Facts Concept chỉ được tạo cho các item thực sự trả về sau limit.

Response còn có tối đa 5 facts `attempt:{id}` gần nhất, type `QUIZ_ATTEMPT_SUMMARY`, và `study:summary`, type `STUDY_SUMMARY`. Attempts sắp theo thời gian rồi ID giảm dần; chỉ lấy đúng User/Course. Study summary tính các phiên đã kết thúc, thời lượng giữ đơn vị giây. Chưa có hoạt động trả `NO_QUIZ_ATTEMPTS`/`NO_COMPLETED_STUDY_SESSIONS`, không cản recommendation.

QuizAttempt hiện chỉ có điểm tổng hợp, không có lịch sử đúng/sai theo Concept. Vì vậy attempts và study summary là evidence cấp Course, không tham gia điểm ranking hoặc bị gắn vào một item như bằng chứng trả lời sai Concept đó. Confidence đã được cập nhật khi submit Quiz nên không cộng điểm lần nữa.

Snapshot dùng transaction read-only `REPEATABLE_READ`. `asOf` là thời điểm bắt đầu đọc, không phải thời điểm người dùng học gần nhất. Tests H2 kiểm tra dữ liệu/API; chưa chứng minh concurrent snapshot trên PostgreSQL.

Mã lỗi: 400 limit không hợp lệ; 401 JWT thiếu/sai; 404 Course không tồn tại/ngoài scope; 422 graph vượt giới hạn, message `Course graph exceeds recommendation limits`. Lỗi dùng envelope hiện có: `code`, `message`, `path`, `timestamp`, và `fieldErrors` nếu có.

## Thử luồng prerequisite

Trên Course thử nghiệm thuộc tài khoản của bạn:

1. Tạo A/B/C qua `POST /api/courses/{courseId}/concepts`, ví dụ body A: `{"name":"A","description":"Nền tảng","confidence":20,"status":"NEW"}`. B có confidence 40, C có confidence 10; ghi lại các ID.
2. `POST /api/concepts/{bId}/prerequisites`, body `{"prerequisiteId":aId}`; tương tự thêm B làm prerequisite của C. Thay `aId` bằng số ID thật trong JSON.
3. Gọi endpoint recommendation: chỉ A sẵn sàng, unlocksCount=2.
4. `PUT /api/concepts/{aId}`, body `{"name":"A","description":"Nền tảng","confidence":80,"status":"NEW"}`. Gọi lại: B sẵn sàng. Nâng B lên 80: C sẵn sàng. Nâng C lên 80: ALL_MASTERED.
5. Tạo D confidence 35 không có prerequisite: D là một lựa chọn độc lập. Khi A c? confidence 20, A đứng trước D vì mở đường cho nhiều Concept hơn.
6. Thêm cạnh A phụ thuộc B để tạo A↔B: A/B/C bị chặn; D vẫn được trả. Nếu xóa D, status là BLOCKED_BY_GRAPH. Chỉ tạo chu trình trên dữ liệu thử nghiệm.

Ví dụ gọi từ PowerShell sau khi có token:

```powershell
$headers = @{ Authorization = "Bearer $accessToken" }
Invoke-RestMethod -Uri "$baseUrl/api/courses/$courseId/recommendations/next-concepts?limit=5" -Headers $headers
```

## Kiểm thử tự động

Từ thư mục `backend/`:

```powershell
.\mvnw.cmd '-Dtest=NextConceptRepositoryTest,NextConceptEngineTest,NextConceptServiceTest,NextConceptApiTest' test
```

Collection v3 có 9 request/assertion cho READY/frontier của chain, evidence references, Course rỗng, tất cả thành thạo, chu trình, limit không hợp lệ, JWT thiếu và Course ngoài quyền. Từ repo root:

```powershell
postman collection lint infra/testing/recommendations/collection
```

Chạy collection trên Spring HTTP thật với H2 disposable và token được test tự tạo, từ `backend/`:

```powershell
$env:RUN_RECOMMENDATION_COLLECTION='1'
try {
    .\mvnw.cmd '-Dtest=NextConceptCollectionTest' test
} finally {
    Remove-Item Env:RUN_RECOMMENDATION_COLLECTION
}
```

Test cần Postman CLI trên PATH. Environment chứa token tạo tạm và xóa khi kết thúc; collection dùng `--no-report-events` để không gửi kết quả ra workspace. Log cục bộ: `logs/next-concepts/collection.log`. Khi không bật biến môi trường, collection test bị skip; không tính skip là đã kiểm thử HTTP bằng Postman.

Nếu đã chuẩn bị các biến fixture theo `NextConceptCollectionTest`, có thể chạy trực tiếp:

```powershell
postman collection run infra/testing/recommendations/collection --environment <temporary.environment.yaml> --no-report-events
```

Chạy hồi quy backend từ `backend/`: `.\mvnw.cmd test`. Các fixture đo giới hạn graph và số SQL nằm trong repository tests; JDBC được đo trực tiếp để tính cả câu SQL không đi qua Hibernate.

Lần kiểm tra cục bộ ngày 06/11/2026: nhóm test tính năng và hồi quy liên quan đạt 38/38, không skip; collection đạt 9/9 assertion. Fixture 2.000 node/10.000 cạnh xếp hạng trong khoảng 69 ms; đọc và xếp hạng 2.000 node độc lập khoảng 16 ms trên H2. Graph dùng 2 câu SQL; graph cộng attempts/study dùng 4 câu SQL, không tính truy vấn kiểm tra ownership, ở cả fixture 1 và 101 node. Đây là số đo cục bộ, không phải cam kết latency trên PostgreSQL/production.

Toàn bộ Maven suite cùng ngày: 363 test, 350 pass, 13 skip, không failure/error. Các skip thuộc test AI simulation chưa có `AI_SIMULATION_URL` và một kiểm tra symlink phụ thuộc môi trường; nhóm recommendation đã chạy đầy đủ, bao gồm collection. PostgreSQL concurrency integration chưa được chạy.
