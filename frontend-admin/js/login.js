const API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const loginForm = document.getElementById('loginForm');
const feedbackEl = document.getElementById('loginFeedback');

async function fetchJson(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });
  if (!response.ok) {
    const text = await response.text();
    const err = new Error(text || '요청이 실패했습니다.');
    err.status = response.status;
    throw err;
  }
  if (response.status === 204) return null;
  return response.json();
}

function setStatus(message, status = '') {
  if (!feedbackEl) return;
  feedbackEl.textContent = message || '';
  feedbackEl.dataset.status = status;
}

async function checkSession() {
  try {
    const me = await fetchJson('/api/auth/me');
    if (me && me.role === 'ADMIN') {
      window.location.href = 'dashboard.html';
    }
  } catch (err) {
    if (err.status !== 401) {
      console.warn('세션 확인 실패', err);
    }
  }
}

if (loginForm) {
  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const formData = new FormData(loginForm);
    const payload = {
      username: formData.get('username'),
      password: formData.get('password')
    };
    setStatus('로그인 중입니다...');
    try {
      const user = await fetchJson('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      if (!user || user.role !== 'ADMIN') {
        setStatus('관리자 권한이 없습니다.', 'error');
        return;
      }
      setStatus('로그인에 성공했습니다.', 'success');
      loginForm.reset();
      window.location.href = 'dashboard.html';
    } catch (err) {
      setStatus(err.message || '로그인에 실패했습니다.', 'error');
    }
  });
}

checkSession();
