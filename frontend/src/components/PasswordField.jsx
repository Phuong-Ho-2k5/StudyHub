import { useState } from 'react'

export function PasswordField({
  id,
  label,
  value,
  onChange,
  error,
  autoComplete,
  disabled,
}) {
  const [isVisible, setIsVisible] = useState(false)
  const errorId = `${id}-error`

  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>{label}</label>
      <div className="password-wrap">
        <input
          id={id}
          type={isVisible ? 'text' : 'password'}
          value={value}
          onChange={onChange}
          autoComplete={autoComplete}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? errorId : undefined}
          disabled={disabled}
        />
        <button
          className="password-toggle"
          type="button"
          onClick={() => setIsVisible((current) => !current)}
          disabled={disabled}
          aria-label={isVisible ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`}
        >
          {isVisible ? 'Ẩn' : 'Hiện'}
        </button>
      </div>
      {error && <p className="field-error" id={errorId}>{error}</p>}
    </div>
  )
}
