import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useLearning } from '../context/LearningContext'
import { Icon } from './Icon'

export function AppHeader({ courses = [], currentCourse }) {
  const { user, logout } = useAuth()
  const { unreadCount, notificationError, adminState, checkAdmin, refreshNotifications } = useLearning()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const sidebarRef = useRef(null)
  const isAdmin = location.pathname === '/admin'
  const initials = user.email.slice(0, 2).toUpperCase()
  const links = [
    ['/welcome', 'Tổng quan', 'grid'],
    ['/welcome#courses', 'Không gian học tập', 'folder'],
    ['/welcome#learning-history', 'Tiến độ & kết quả', 'chart'],
    ['/notifications', 'Thông báo', 'bell'],
  ]

  useEffect(() => {
    if (!open) return undefined
    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    sidebarRef.current?.querySelector('a')?.focus()
    const onKeyDown = (event) => {
      if (event.key === 'Escape') setOpen(false)
      if (event.key !== 'Tab') return
      const elements = Array.from(sidebarRef.current.querySelectorAll('a, button:not(:disabled)')).filter((element) => element.getClientRects().length)
      const first = elements[0]
      const last = elements.at(-1)
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    const onResize = () => { if (window.innerWidth > 760) setOpen(false) }
    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('resize', onResize)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('resize', onResize)
      if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true })
    }
  }, [open])

  useEffect(() => {
    if (!location.hash) return undefined
    const frame = window.requestAnimationFrame(() => {
      const target = document.getElementById(location.hash.slice(1))
      if (!target) return
      target.scrollIntoView({ block: 'start' })
      if (target.matches('input')) target.focus({ preventScroll: true })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [location.hash, location.key])

  function active(href) {
    return `${location.pathname}${location.hash}` === href
  }

  return <>
    <a className="skip-link" href="#main-content">Đến nội dung chính</a>
    {open && <button type="button" className="sidebar-overlay" aria-label="Đóng điều hướng" onClick={() => setOpen(false)} />}
    <aside ref={sidebarRef} id="app-sidebar" className={`app-sidebar ${open ? 'is-open' : ''} ${isAdmin ? 'admin-sidebar' : ''}`} aria-label="Điều hướng StudyHub">
      <div className="sidebar-brand-row"><Link className="brand" to="/welcome" onClick={() => setOpen(false)}><span className="brand-mark"><Icon name="book" size={21} /></span><span>StudyHub<span className="brand-caption">Học tập có chủ đích</span></span></Link><button className="icon-button sidebar-close" type="button" aria-label="Đóng điều hướng" onClick={() => setOpen(false)}><Icon name="close" /></button></div>
      <Link className="personal-space" to="/welcome#courses" onClick={() => setOpen(false)}><span className="space-icon"><Icon name="folder" size={18} /></span><span><small>KHÔNG GIAN CỦA BẠN</small><strong>Học tập cá nhân</strong></span><span aria-hidden="true">⌄</span></Link>
      <p className="nav-label">KHÁM PHÁ</p>
      <nav className="app-nav" aria-label="Điều hướng chính">{links.map(([href, label, icon]) => <Link key={href} to={href} className={active(href) ? 'active' : ''} aria-current={active(href) ? 'page' : undefined} onClick={() => setOpen(false)}><Icon name={icon} size={18} /><span>{label}</span>{icon === 'bell' && unreadCount !== null && unreadCount > 0 && <span className="nav-count" aria-label={`${unreadCount} chưa đọc`}>{unreadCount}</span>}</Link>)}
        {adminState === 'allowed' && <Link to="/admin" className={isAdmin ? 'active' : ''} aria-current={isAdmin ? 'page' : undefined} onClick={() => setOpen(false)}><Icon name="shield" size={18} /><span>Quản trị hệ thống</span></Link>}
        {adminState === 'error' && <button type="button" onClick={checkAdmin}>Thử lại kiểm tra quyền</button>}
        {notificationError && <button type="button" onClick={refreshNotifications}>Tải lại số thông báo</button>}
      </nav>
      {(courses.length > 0 || currentCourse) && <div className="sidebar-courses"><p className="nav-label">{currentCourse ? 'KHÓA HỌC HIỆN TẠI' : 'KHÓA HỌC CỦA BẠN'}</p>{(currentCourse ? [currentCourse] : courses.slice(0, 4)).map((course, index) => <Link key={course.id} to={`/courses/${course.id}`} className={currentCourse ? 'selected-course' : ''} onClick={() => setOpen(false)}><span className={`course-dot dot-${index % 3}`} /><span>{course.name}</span></Link>)}</div>}
      <div className="sidebar-bottom"><div className="sidebar-note"><Icon name="leaf" size={21} /><p>Mỗi bước nhỏ hôm nay,<br />một nền tảng vững ngày mai.</p></div><div className="sidebar-account"><span className="account-avatar">{initials}</span><div><strong title={user.email}>{user.email}</strong><span>{isAdmin ? 'Khu vực quản trị' : 'Tài khoản cá nhân'}</span></div><button className="icon-button" type="button" onClick={logout} aria-label="Đăng xuất" title="Đăng xuất"><Icon name="logout" size={18} /></button></div></div>
    </aside>
    <header className="dashboard-header app-header">
      <div className="topbar-context"><button className="icon-button mobile-menu" type="button" onClick={() => setOpen(!open)} aria-label="Mở điều hướng" aria-expanded={open} aria-controls="app-sidebar"><Icon name="menu" /></button><Icon name={isAdmin ? 'shield' : 'folder'} size={17} /><Link to={isAdmin ? '/admin' : '/welcome'}>{isAdmin ? 'Khu vực quản trị' : 'Học tập cá nhân'}</Link>{currentCourse && <><span className="breadcrumb-divider">/</span><span className="breadcrumb-current">{currentCourse.name}</span></>}</div>
      <div className="topbar-actions"><Link className="topbar-search" to="/welcome#course-search" aria-label="Tìm khóa học"><Icon name="search" size={16} /><span>Tìm khóa học…</span></Link><span className="topbar-divider" /><Link className="topbar-bell" to="/notifications" aria-label={unreadCount === null ? 'Thông báo' : `Thông báo, ${unreadCount} chưa đọc`}><Icon name="bell" size={19} />{unreadCount > 0 && <span className="notification-dot" />}</Link><time dateTime={new Date().toLocaleDateString('en-CA')}>{new Date().toLocaleDateString('vi-VN', { day: 'numeric', month: 'long', year: 'numeric' })}</time></div>
    </header>
  </>
}
