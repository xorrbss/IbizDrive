# Frontend Auth Module

## Purpose

Frontend authentication boundary for the Next.js app. It adapts the backend `/api/auth/*` session contract into TanStack Query state, login/logout UI, and route guards for explorer/admin layouts.

## Entry Points

- `frontend/src/types/auth.AuthRole`
- `frontend/src/types/auth.AuthSession`
- `frontend/src/hooks/useAuth`
- `frontend/src/components/auth/AuthGate`
- `frontend/src/components/auth/LoginClient`
- `frontend/src/app/admin/page.AdminRootPage`

## Invariants

- HttpOnly session cookie and `GET /api/auth/me` are the session truth source.
- Session/user data is not duplicated into Zustand.
- Login/logout mutations fetch CSRF immediately before the POST.
- `next` redirect targets must be root-relative and must not allow protocol-relative URLs.
- `/files/*` requires authentication.
- `/admin/*` is an UX guard based on `/me.roles`; only `ADMIN` and `AUDITOR` pass. Backend admin endpoints remain the security boundary.
- `usePermission` stub must not be used for admin route decisions.

## Depends On

- `frontend/src/lib/api`
- `frontend/src/lib/queryKeys`
- `@tanstack/react-query`
- Next.js App Router navigation hooks
- Backend auth contract in docs/02 §7.4 and docs/03 §2

## Change Impact

- Changing `/api/auth/me` shape requires updating `AuthSession`, `useAuth`, login tests, and route guard tests.
- Changing role names requires updating docs/03 §3, `AuthRole`, `AuthGate` callers, and backend role mapping.
- Adding password-change enforcement requires replacing the `mustChangePassword` blocked state with a real route/API contract.
