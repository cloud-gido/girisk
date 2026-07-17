export function getToken(): string | null {
  return localStorage.getItem('risk_token');
}

export function setAuth(token: string, username: string, role: string, displayName: string) {
  localStorage.setItem('risk_token', token);
  localStorage.setItem('risk_user', JSON.stringify({ username, role, displayName }));
}

export function clearAuth() {
  localStorage.removeItem('risk_token');
  localStorage.removeItem('risk_user');
}

export function getUser(): { username: string; role: string; displayName: string } | null {
  const raw = localStorage.getItem('risk_user');
  return raw ? JSON.parse(raw) : null;
}

export function isLoggedIn(): boolean {
  return !!getToken();
}
