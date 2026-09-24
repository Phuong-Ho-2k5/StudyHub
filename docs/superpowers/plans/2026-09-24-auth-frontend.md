# StudyHub Authentication Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a desktop React web app that registers users, logs them in against the existing Spring Boot API, persists a valid JWT, protects a waiting page, and logs users out.

**Architecture:** A small Vite/React single-page app uses React Router for public and protected routes. A focused API client owns HTTP error normalization, while an authentication context owns token parsing and persistence. Plain CSS provides the approved “study corner” desktop design without a UI framework.

**Tech Stack:** React, Vite, React Router, browser Fetch API, plain CSS, ESLint

**Spec:** `docs/superpowers/specs/2026-09-24-auth-frontend-design.md`

## Global Constraints

- Implement desktop web layouts only; do not add mobile-specific breakpoints or responsive variants.
- Do not install Vitest, React Testing Library, or any other automated frontend test dependency.
- Use plain CSS and keep production dependencies limited to React, React DOM, and React Router.
- Integrate only `POST /api/auth/register` and `POST /api/auth/login`.
- Do not add Workspace, Course, Document, Quiz, progress, AI screens, or mocked business data.
- Store the JWT under the single key `studyhub_access_token` in `localStorage`.
- Use the JWT only to read `sub` and `exp` for display/session expiry; do not describe client decoding as authorization or signature validation.
- Preserve all unrelated backend and untracked user files in the working tree.

## Review Focus

- The API is unreachable: the form stops loading and shows a retryable Vietnamese network message.
- The backend returns a non-JSON error: the API client still returns a safe fallback message.
- A stored token is malformed or expired: it is removed and `/welcome` redirects to `/login`.
- A user submits twice quickly: controls stay disabled until the first request settles.
- Registration receives leading/trailing spaces or uppercase email: the request trims the name and normalizes the email.

Automated tests are deliberately excluded by the approved spec. Each item above is included in the manual verification checklist owned by the task that implements it.

---

## File Map

- `frontend/package.json`: scripts and runtime dependencies.
- `frontend/vite.config.js`: React plugin and local `/api` proxy to Spring Boot.
- `frontend/eslint.config.js`: lint rules generated for the React app.
- `frontend/index.html`: Vite document shell and StudyHub metadata.
- `frontend/.env.example`: optional production API base URL.
- `frontend/src/main.jsx`: browser entry point.
- `frontend/src/App.jsx`: application routes and authenticated-route boundary.
- `frontend/src/styles.css`: approved desktop visual system and all page styles.
- `frontend/src/api/authApi.js`: register/login requests and normalized `ApiError` failures.
- `frontend/src/auth/token.js`: safe JWT payload parsing and expiry checks.
- `frontend/src/auth/AuthContext.jsx`: token persistence and session actions.
- `frontend/src/auth/ProtectedRoute.jsx`: guest redirect for protected content.
- `frontend/src/components/AuthLayout.jsx`: shared split-screen authentication shell.
- `frontend/src/components/PasswordField.jsx`: reusable password input with visibility control.
- `frontend/src/pages/LoginPage.jsx`: login form and request-state behavior.
- `frontend/src/pages/RegisterPage.jsx`: registration form and request-state behavior.
- `frontend/src/pages/WelcomePage.jsx`: authenticated waiting page and logout.
- `frontend/README.md`: local run and environment instructions.

### Task 1: Scaffold the React application and desktop visual foundation

**Files:**
- Replace: `frontend/README.md`
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/eslint.config.js`
- Create: `frontend/index.html`
- Create: `frontend/.env.example`
- Create: `frontend/src/main.jsx`
- Create: `frontend/src/App.jsx`
- Create: `frontend/src/styles.css`

**Interfaces:**
- Consumes: Spring Boot running at `http://localhost:8080` during local development.
- Produces: `npm run dev`, `npm run lint`, and `npm run build`; CSS utility classes used by later pages.

- [ ] **Step 1: Generate the Vite React package metadata**

Run from the repository root:

```powershell
Set-Location frontend
npm create vite@latest . -- --template react --force
npm install react-router-dom
```

Keep the generated ESLint setup. Remove generated demo assets and component styles once the StudyHub files below replace them. Do not add testing packages.

- [ ] **Step 2: Configure routing support and the backend development proxy**

Set `frontend/vite.config.js` to:

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
})
```

Create `frontend/.env.example`:

```dotenv
VITE_API_URL=
```

An empty value keeps browser requests relative so the local proxy and a future same-origin deployment both work.

- [ ] **Step 3: Create the application entry point and initial route shell**

Set `frontend/src/main.jsx` to render `App` inside `BrowserRouter`. Set `frontend/src/App.jsx` to define `/login`, `/register`, and `/welcome`, redirect `/` to `/login`, and redirect unknown paths to `/login`. Import `styles.css` once from `main.jsx`.

Use these imports so later tasks connect without renaming:

```jsx
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { WelcomePage } from './pages/WelcomePage'
```

- [ ] **Step 4: Establish the visual tokens and desktop frame**

In `styles.css`, define these root tokens and build the shared styles needed by `AuthLayout`, both forms, and `WelcomePage`:

```css
:root {
    font-family: Inter, "Segoe UI", sans-serif;
    color: #17253d;
    background: #f2ede3;
    --ink: #17253d;
    --ink-soft: #536176;
    --paper: #fffdf8;
    --cream: #f2ede3;
    --line: #d9d3c8;
    --accent: #b55f45;
    --accent-dark: #934832;
    --danger: #a33c35;
    --success: #356b53;
}
```

Use a desktop split frame with a fixed readable form width around `430px`, a page minimum width around `960px`, moderate `12px` radii, and subtle borders/shadows. Include visible `:focus-visible`, disabled, loading, error, success, password-toggle, text-link, and welcome-page states. Do not add gradients, glass effects, floating blobs, or mobile media queries.

- [ ] **Step 5: Write local setup documentation**

Replace `frontend/README.md` with commands for `npm install`, `npm run dev`, `npm run lint`, and `npm run build`. State that local development proxies `/api` to `http://localhost:8080`, and that `VITE_API_URL` may be set for a separately hosted production API.

- [ ] **Step 6: Install, lint, build, and commit the foundation**

Run:

```powershell
npm install
npm run lint
npm run build
```

Expected: dependencies install successfully, ESLint reports no errors, and Vite writes production assets to `frontend/dist`.

Commit only frontend foundation files:

```powershell
git add frontend
git commit -m "build: initialize StudyHub React frontend"
```

### Task 2: Implement the API client and authentication session core

**Files:**
- Create: `frontend/src/api/authApi.js`
- Create: `frontend/src/auth/token.js`
- Create: `frontend/src/auth/AuthContext.jsx`
- Create: `frontend/src/auth/ProtectedRoute.jsx`
- Modify: `frontend/src/main.jsx`

**Interfaces:**
- Consumes: `POST /api/auth/register`, `POST /api/auth/login`, browser `localStorage`.
- Produces: `registerUser({ name, email, password })`, `loginUser({ email, password })`, `readToken(token)`, `AuthProvider`, `useAuth()`, and `ProtectedRoute`.

- [ ] **Step 1: Implement safe request and error normalization**

Create `authApi.js` with an exported `ApiError extends Error` containing `status` and `details`. Implement an internal `request(path, options)` that uses:

```js
const API_URL = import.meta.env.VITE_API_URL?.replace(/\/$/, '') ?? ''
```

Send `Content-Type: application/json`. Read the response body with `response.text()`, parse JSON inside `try/catch`, and fall back to an empty object for non-JSON bodies. On fetch rejection, throw `ApiError` with status `0` and the message `Không thể kết nối tới máy chủ. Vui lòng thử lại.` On non-2xx responses, preserve `body.message` when available.

Export:

```js
export function registerUser({ name, email, password })
export function loginUser({ email, password })
```

Both functions return the parsed JSON body.

- [ ] **Step 2: Implement defensive JWT payload reading**

Create `token.js` with:

```js
export const TOKEN_STORAGE_KEY = 'studyhub_access_token'

export function readToken(token) {
    // Return { email, expiresAt } only when the token has three sections,
    // the payload is valid base64url JSON, sub is a string, exp is a number,
    // and exp * 1000 is later than Date.now(); otherwise return null.
}
```

Convert base64url to base64, add required padding, decode with `atob`, and decode UTF-8 bytes with `TextDecoder`. Catch every decoding/parsing error and return `null`.

- [ ] **Step 3: Implement persistent authentication state**

Create `AuthContext.jsx`. On initial render, read `studyhub_access_token`; remove it if `readToken` returns `null`. Expose exactly:

```js
{
    token,
    user: tokenData ? { email: tokenData.email } : null,
    isAuthenticated: Boolean(tokenData),
    login(accessToken),
    logout(),
}
```

`login` must reject an invalid/expired access token by throwing `Error('Máy chủ trả về phiên đăng nhập không hợp lệ.')`; otherwise persist it and update state. `logout` removes the key and clears state. Export `AuthProvider` and `useAuth`.

- [ ] **Step 4: Protect the authenticated route**

Create `ProtectedRoute.jsx`:

```jsx
export function ProtectedRoute({ children }) {
    const { isAuthenticated } = useAuth()
    return isAuthenticated ? children : <Navigate to="/login" replace />
}
```

Wrap `<App />` with `<AuthProvider>` inside the existing `<BrowserRouter>` in `main.jsx`.

- [ ] **Step 5: Exercise session edge cases manually**

In the browser console, set `studyhub_access_token` first to `broken`, then to a syntactically valid JWT whose `exp` is in the past. Refresh `/welcome` after each value.

Expected: each value is deleted and the browser lands on `/login`. Also temporarily stop the backend and call `registerUser` from a form once Task 3 is connected; the final checklist must confirm the network message and released loading state.

- [ ] **Step 6: Lint, build, and commit the auth core**

Run from `frontend`:

```powershell
npm run lint
npm run build
```

Expected: both commands exit with code `0`.

```powershell
git add frontend/src/api frontend/src/auth frontend/src/main.jsx
git commit -m "feat: add frontend authentication session core"
```

### Task 3: Build the shared auth layout and login/register flows

**Files:**
- Create: `frontend/src/components/AuthLayout.jsx`
- Create: `frontend/src/components/PasswordField.jsx`
- Create: `frontend/src/pages/LoginPage.jsx`
- Create: `frontend/src/pages/RegisterPage.jsx`
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `loginUser`, `registerUser`, `ApiError`, and `useAuth().login`.
- Produces: complete `/login` and `/register` routes and shared auth presentation.

- [ ] **Step 1: Build the shared study-corner layout**

`AuthLayout` accepts `eyebrow`, `title`, `description`, and `children`. The identity side contains the StudyHub wordmark, the line `Một chỗ gọn gàng cho việc học mỗi ngày.`, and three restrained text details: `Ghi chú tập trung`, `Tiến độ rõ ràng`, and `Học theo nhịp của bạn`. Keep the copy short and do not advertise unfinished functionality as available.

- [ ] **Step 2: Build the reusable password field**

`PasswordField` accepts `id`, `label`, `value`, `onChange`, `error`, `autoComplete`, and `disabled`. Use local boolean state to switch between `password` and `text`; the control label must switch between `Hiện` and `Ẩn`. Connect error text through `aria-describedby` and `aria-invalid`.

- [ ] **Step 3: Implement login validation and submission**

`LoginPage` owns `{ email, password }`, field errors, request error, and loading state. Validate with a simple browser-appropriate email expression and password length `8–100`. Normalize email using `trim().toLowerCase()` before calling `loginUser`.

On success, require a string `accessToken`, call `useAuth().login(accessToken)`, then navigate to `/welcome` with `replace: true`. Map `401` to `Email hoặc mật khẩu không đúng.` and status `0` to the network message from `ApiError`. Show the success message supplied by registration through router location state, then clear that state after it is displayed.

- [ ] **Step 4: Implement registration validation and submission**

`RegisterPage` owns `{ name, email, password, confirmPassword }`. Require a trimmed name, a valid normalized email, password length `8–100`, and matching confirmation. Send only `{ name: name.trim(), email: email.trim().toLowerCase(), password }`.

Map `409` to `Email này đã được sử dụng.` On success, navigate to `/login` with state:

```js
{
    email: normalizedEmail,
    message: 'Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.',
}
```

Disable all fields and the submit button while awaiting either request, and reset loading in `finally` so network and unexpected errors cannot leave the form locked.

- [ ] **Step 5: Connect public-route behavior**

In `App.jsx`, render the two form pages on their routes. When `useAuth().isAuthenticated` is true, `/login` and `/register` must redirect to `/welcome` instead of showing auth forms.

- [ ] **Step 6: Manually verify both forms and review-focus failures**

Run Spring Boot and Vite. Verify:

1. Empty/invalid submissions show adjacent field errors without a request.
2. Registration trims the name, lowercases email, and returns to a prefilled login form.
3. Duplicate registration shows the `409` message.
4. Wrong credentials show the neutral `401` message.
5. Double-clicking submit sends one effective request because the control disables immediately.
6. Stopping Spring Boot produces the Vietnamese network error and re-enables the form.
7. Replace one backend response temporarily through browser request blocking or a local proxy with plain text; the UI shows the fallback rather than crashing.

- [ ] **Step 7: Lint, build, and commit the auth pages**

Run:

```powershell
npm run lint
npm run build
```

Expected: both commands exit with code `0`.

```powershell
git add frontend/src/components frontend/src/pages/LoginPage.jsx frontend/src/pages/RegisterPage.jsx frontend/src/App.jsx frontend/src/styles.css
git commit -m "feat: build registration and login pages"
```

### Task 4: Complete the protected waiting page and final verification

**Files:**
- Create: `frontend/src/pages/WelcomePage.jsx`
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/README.md`

**Interfaces:**
- Consumes: `useAuth().user`, `useAuth().logout`, and `ProtectedRoute`.
- Produces: the finished `/welcome` experience and documented frontend workflow.

- [ ] **Step 1: Implement the authenticated waiting page**

Create a desktop page with a compact StudyHub header, the greeting `Chào bạn,` followed by `user.email`, and this honest milestone copy:

```text
Tài khoản của bạn đã sẵn sàng.
Không gian học tập và khóa học sẽ được bổ sung ở chặng tiếp theo.
```

Add one restrained status panel labeled `Mốc hiện tại` with `Đăng ký và đăng nhập đã hoạt động`. Do not render mock statistics, fake courses, empty charts, or inactive navigation items.

- [ ] **Step 2: Implement logout navigation**

The `Đăng xuất` button calls `logout()` and then navigates to `/login` with `replace: true`. Browser Back must not reveal authenticated content because the route guard reads the cleared session.

- [ ] **Step 3: Finish the route table and metadata**

Ensure `/welcome` renders only inside `ProtectedRoute`, `/` redirects according to auth state, and unknown routes redirect according to auth state. Set the document title to `StudyHub — Góc học tập của bạn` in `index.html`.

- [ ] **Step 4: Run the complete desktop acceptance pass**

At a desktop viewport of at least `1366 × 768`, verify:

1. Direct navigation to `/welcome` as a guest redirects to `/login`.
2. Registration succeeds and carries normalized email plus a success message to login.
3. Login persists a valid token and reaches `/welcome`.
4. Refreshing `/welcome` preserves the session and displays the JWT subject email.
5. Logout removes the token and redirects; Back cannot reopen the page.
6. Malformed and expired tokens are removed on refresh.
7. Keyboard Tab order, focus indicators, labels, show/hide controls, Enter submission, and announced error/status text work.
8. No UI claims Workspace, Course, or AI features are already available.

- [ ] **Step 5: Run final static verification**

From `frontend`:

```powershell
npm run lint
npm run build
```

Expected: both commands exit with code `0`; `frontend/dist/index.html` and hashed assets exist.

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only intentional frontend changes and the user's pre-existing backend/untracked files are present.

- [ ] **Step 6: Commit the completed waiting page**

```powershell
git add frontend/src/pages/WelcomePage.jsx frontend/src/App.jsx frontend/src/styles.css frontend/index.html frontend/README.md
git commit -m "feat: add authenticated StudyHub welcome page"
```
