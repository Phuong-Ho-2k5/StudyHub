import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, registerUser } from '../api/authApi'
import { AuthLayout } from '../components/AuthLayout'
import { PasswordField } from '../components/PasswordField'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate(values) {
  const errors = {}
  const email = values.email.trim()

  if (!values.name.trim()) errors.name = 'Vui lòng nhập tên của bạn.'
  if (!email) errors.email = 'Vui lòng nhập email.'
  else if (!EMAIL_PATTERN.test(email)) errors.email = 'Email chưa đúng định dạng.'

  if (!values.password) errors.password = 'Vui lòng nhập mật khẩu.'
  else if (values.password.length < 8 || values.password.length > 100) {
    errors.password = 'Mật khẩu cần có từ 8 đến 100 ký tự.'
  }

  if (!values.confirmPassword) errors.confirmPassword = 'Vui lòng nhập lại mật khẩu.'
  else if (values.confirmPassword !== values.password) {
    errors.confirmPassword = 'Mật khẩu nhập lại chưa khớp.'
  }

  return errors
}

export function RegisterPage() {
  const navigate = useNavigate()
  const [values, setValues] = useState({ name: '', email: '', password: '', confirmPassword: '' })
  const [errors, setErrors] = useState({})
  const [requestError, setRequestError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  function updateField(event) {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    setErrors((current) => ({ ...current, [name]: undefined }))
    setRequestError('')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (isSubmitting) return

    const nextErrors = validate(values)
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors)
      return
    }

    const normalizedEmail = values.email.trim().toLowerCase()
    setIsSubmitting(true)
    setRequestError('')

    try {
      await registerUser({
        name: values.name.trim(),
        email: normalizedEmail,
        password: values.password,
      })

      navigate('/login', {
        replace: true,
        state: {
          email: normalizedEmail,
          message: 'Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.',
        },
      })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setRequestError('Email này đã được sử dụng.')
      } else if (error instanceof ApiError && error.status === 400) {
        setRequestError('Thông tin đăng ký chưa hợp lệ. Vui lòng kiểm tra lại.')
      } else if (error instanceof ApiError) {
        setRequestError(error.message)
      } else {
        setRequestError('Tạo tài khoản chưa thành công. Vui lòng thử lại.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Bắt đầu với StudyHub"
      title="Tạo tài khoản"
      description="Chỉ cần vài thông tin cơ bản để bắt đầu."
    >
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {requestError && <p className="form-message error" role="alert">{requestError}</p>}

        <div className="field">
          <label className="field-label" htmlFor="register-name">Tên của bạn</label>
          <input
            id="register-name"
            name="name"
            value={values.name}
            onChange={updateField}
            placeholder="Nguyễn Văn An"
            autoComplete="name"
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? 'register-name-error' : undefined}
            disabled={isSubmitting}
            autoFocus
          />
          {errors.name && <p className="field-error" id="register-name-error">{errors.name}</p>}
        </div>

        <div className="field">
          <label className="field-label" htmlFor="register-email">Email</label>
          <input
            id="register-email"
            name="email"
            type="email"
            value={values.email}
            onChange={updateField}
            placeholder="ban@example.com"
            autoComplete="email"
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'register-email-error' : undefined}
            disabled={isSubmitting}
          />
          {errors.email && <p className="field-error" id="register-email-error">{errors.email}</p>}
        </div>

        <PasswordField
          id="register-password"
          label="Mật khẩu"
          value={values.password}
          onChange={(event) => updateField({ target: { name: 'password', value: event.target.value } })}
          error={errors.password}
          autoComplete="new-password"
          disabled={isSubmitting}
        />

        <PasswordField
          id="register-confirm-password"
          label="Nhập lại mật khẩu"
          value={values.confirmPassword}
          onChange={(event) => updateField({ target: { name: 'confirmPassword', value: event.target.value } })}
          error={errors.confirmPassword}
          autoComplete="new-password"
          disabled={isSubmitting}
        />

        <button className="primary-button" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}
        </button>
      </form>

      <p className="form-switch">
        Đã có tài khoản? <Link className="text-link" to="/login">Đăng nhập</Link>
      </p>
    </AuthLayout>
  )
}
