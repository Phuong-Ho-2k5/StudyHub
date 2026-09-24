# StudyHub authentication frontend design

## Goal

Build the first usable React frontend for the current StudyHub milestone. The frontend covers registration, login, session persistence, protected navigation, logout, and a small post-login waiting page. It must use the two authentication endpoints that already exist and must not pretend that Workspace, Course, or AI features are available yet.

## Scope

### Included

- Register with name, email, password, and password confirmation.
- Log in with email and password.
- Persist the JWT in `localStorage` across page refreshes.
- Reject malformed or expired tokens on the client.
- Protect the post-login route and redirect guests to login.
- Log out and remove the stored token.
- Show a restrained waiting page after login.
- Responsive layouts and accessible form states.

### Excluded

- Password recovery, social login, email verification, and refresh tokens.
- Workspace, Course, Document, Quiz, progress, and AI screens.
- Mock business data or navigation to unfinished modules.
- A current-user request, because `/api/auth/me` does not exist yet.

## Technical approach

Use Vite with React JavaScript and React Router. Keep dependencies small and style the application with plain CSS.

The application contains three routes:

- `/login`: public login page.
- `/register`: public registration page.
- `/welcome`: protected post-login page.

An authentication context owns the token, derives the email and expiration time from its JWT payload, and exposes login and logout operations. A protected-route component redirects unauthenticated users to `/login`. A small API client reads `VITE_API_URL`, sends JSON requests, and converts backend and network failures into a consistent error object.

The token remains the backend's source of truth. The client only decodes its payload to display the subject email and check expiration; it does not treat client-side decoding as signature verification or authorization.

## API integration

### Registration

`POST /api/auth/register`

Request:

```json
{
  "name": "Nguyen Van An",
  "email": "an@example.com",
  "password": "password123"
}
```

On success, navigate to login, prefill the normalized email, and display a success message. A `409` response displays that the email already exists.

### Login

`POST /api/auth/login`

Request:

```json
{
  "email": "an@example.com",
  "password": "password123"
}
```

On success, store `accessToken` and navigate to `/welcome`. A `401` response displays a neutral invalid-credentials message.

## Visual design

Use a quiet “study corner” visual direction rather than a generic SaaS template:

- Ink blue for primary text and actions.
- Warm cream for the page background.
- Muted terracotta for small accents.
- Moderate corner radii and very light shadows.
- No gradients, glassmorphism, oversized marketing copy, or grids of decorative cards.

On desktop, the authentication pages use a balanced split layout. One side contains the StudyHub identity and a few subtle study-related details; the other contains the form. On small screens, the identity area becomes a compact header above the form.

The welcome page reuses the same visual language. It shows a brief greeting using the email from the JWT, states honestly that learning spaces are coming next, and provides a logout action.

## Form behavior and accessibility

- Use visible labels rather than placeholder-only fields.
- Validate email format, password length of 8–100 characters, required name, and matching password confirmation.
- Trim the name and normalize email casing before requests.
- Support Enter submission and an explicit show/hide password control.
- Disable repeated submission and display a clear loading label.
- Put field errors next to their fields and request errors in an announced status region.
- Provide visible keyboard focus and sufficient color contrast.
- Respect `prefers-reduced-motion`.

## Error handling

- `400`: show field-level validation details when the response exposes them; otherwise show a general invalid-data message.
- `401`: show “Email hoặc mật khẩu không đúng” on login.
- `409`: show “Email này đã được sử dụng” on registration.
- Network failure: explain that the backend could not be reached and invite the user to retry.
- Unexpected response: show a concise fallback without exposing implementation details.
- Missing, malformed, or expired stored token: clear it and redirect to login.

## Testing and verification

Use Vitest and React Testing Library for focused behavior tests:

- Registration validation and successful redirect with prefilled email.
- Login failure and successful token persistence.
- Protected-route redirect for guests.
- Access to `/welcome` with a valid non-expired token.
- Cleanup of expired or malformed tokens.
- Logout cleanup and redirect.

Before completion, run the automated tests, lint checks, and a production build. Manually inspect authentication and welcome pages at desktop and mobile widths.

## Future extension points

When the backend adds `/api/auth/me`, the authentication context can load the full user profile without changing page routing. Future Workspace and Course routes can sit behind the same protected-route boundary and replace the waiting-page content incrementally.
