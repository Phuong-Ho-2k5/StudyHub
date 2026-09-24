import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, loginUser } from '../api/authApi'
import { useAuth } from '../auth/AuthContext'
import { AuthLayout } from '../components/AuthLayout'
import { PasswordField } from '../components/PasswordField'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate(values) {
  const errors = {}
  const email = values.email.trim()

  if (!email) {
    errors.email = 'Vui lòng nhập email.'
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = 'Email chưa đúng định dạng.'
  }

  if (!values.password) {
    errors.password = 'Vui lòng nhập mật khẩu.'
  } else if (values.password.length < 8 || values.password.length > 100) {
    errors.password = 'Mật khẩu cần có từ 8 đến 100 ký tự.'
  }

  return errors
}

export function LoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { login } = useAuth()
  const [values, setValues] = useState({
    email: location.state?.email ?? '',
    password: '',
  })
  const [errors, setErrors] = useState({})
  const [requestError, setRequestError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage] = useState(location.state?.message ?? '')

  useEffect(() => {
    if (location.state) {
      navigate(location.pathname, { replace: true, state: null })
    }
  }, [location.pathname, location.state, navigate])

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

    setIsSubmitting(true)
    setRequestError('')

    try {
      const response = await loginUser({
        email: values.email.trim().toLowerCase(),
        password: values.password,
      })

      if (typeof response.accessToken !== 'string') {
        throw new Error('Máy chủ trả về phiên đăng nhập không hợp lệ.')
      }

      login(response.accessToken)
      navigate('/welcome', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setRequestError('Email hoặc mật khẩu không đúng.')
      } else if (error instanceof ApiError) {
        setRequestError(error.message)
      } else if (error instanceof Error && error.message === 'Máy chủ trả về phiên đăng nhập không hợp lệ.') {
        setRequestError(error.message)
      } else {
        setRequestError('Đăng nhập chưa thành công. Vui lòng thử lại.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Chào bạn quay lại"
      title="Đăng nhập"
      description="Tiếp tục từ nơi bạn đã dừng lại."
    >
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {successMessage && (
          <p className="form-message success" role="status">{successMessage}</p>
        )}
        {requestError && (
          <p className="form-message error" role="alert">{requestError}</p>
        )}

        <div className="field">
          <label className="field-label" htmlFor="login-email">Email</label>
          <input
            id="login-email"
            name="email"
            type="email"
            value={values.email}
            onChange={updateField}
            placeholder="ban@example.com"
            autoComplete="email"
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'login-email-error' : undefined}
            disabled={isSubmitting}
            autoFocus
          />
          {errors.email && <p className="field-error" id="login-email-error">{errors.email}</p>}
        </div>

        <PasswordField
          id="login-password"
          label="Mật khẩu"
          value={values.password}
          onChange={(event) => updateField({ target: { name: 'password', value: event.target.value } })}
          error={errors.password}
          autoComplete="current-password"
          disabled={isSubmitting}
        />

        <button className="primary-button" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
        </button>
      </form>

      <p className="form-switch">
        Chưa có tài khoản? <Link className="text-link" to="/register">Tạo tài khoản</Link>
      </p>
    </AuthLayout>
  )
}
