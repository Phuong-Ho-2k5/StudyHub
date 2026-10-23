import { Link } from 'react-router-dom'
import { Icon } from './Icon'

export function AuthLayout({ eyebrow, title, description, children, className = '' }) {
  return <main className={`auth-page ${className}`.trim()}>
    <header className="auth-header"><Link className="brand" to="/login" aria-label="StudyHub - trang đăng nhập"><span className="brand-mark"><Icon name="book" size={21} /></span><span>StudyHub</span></Link></header>
    <div className="auth-content"><section className="auth-story" aria-label="Giới thiệu StudyHub">
      <div className="story-main"><p className="story-kicker"><span /> KHÔNG GIAN RIÊNG CHO VIỆC HỌC</p><h1>Học sâu hơn.<br />Kết nối điều đã biết.</h1><p>Tài liệu, khái niệm và những câu hỏi của bạn — cùng ở một nơi, cùng một mạch học tập.</p>
        <div className="knowledge-preview" aria-label="Tổ chức kiến thức theo khóa học"><div className="preview-heading"><Icon name="spark" size={17} /><span>MỖI Ý TƯỞNG ĐỀU CÓ NỀN TẢNG</span></div><div className="preview-document"><span className="preview-book"><Icon name="book" size={24} /></span><div><strong>Từ tài liệu đến hiểu biết</strong><span>Xây nền tảng theo cách của bạn</span></div><Icon name="check" size={16} /></div><div className="knowledge-connection"><div><span className="preview-icon"><Icon name="folder" size={18} /></span><strong>Hiểu nền tảng</strong><span>Sắp xếp điều đã học</span></div><Icon name="arrow" size={20} /><div><span className="preview-icon"><Icon name="spark" size={18} /></span><strong>Kết nối kiến thức</strong><span>Vững vàng bước tiếp</span></div></div><p className="preview-caption">Một khóa học, một mạch kiến thức rõ ràng.</p></div>
      </div><div className="story-footer"><Icon name="leaf" size={18} /><span>Không gian cá nhân. Kiến thức có căn cứ.</span></div>
    </section><section className="auth-panel"><div className="form-card"><p className="form-eyebrow">{eyebrow}</p><h2>{title}</h2><p className="form-intro">{description}</p>{children}<div className="auth-trust"><Icon name="shield" size={15} /><span>Không gian học tập dành riêng cho bạn.</span></div></div></section></div>
    <footer className="auth-footer"><span>StudyHub · Học tập có chủ đích</span><span>Được tạo nên cho những bước tiến nhỏ.</span></footer>
  </main>
}
