const CMS_AUTH_API_BASE = window.__API_BASE__ || 'https://api.cms-community.com';
const CMS_AUTH_STORAGE_KEY = 'cms:auth:user';

(function () {
  const state = {
    user: null,
    initialized: false,
  };

  function init() {
    if (state.initialized) {
      return;
    }
    state.initialized = true;
    bindHeaderActions();
    hydrateUserFromStorage();
    updateHeaderUI(state.user);
    announceUserChange();
    bindStorageSync();
    refreshUser();
  }

  function bindHeaderActions() {
    const headerLoginButton = document.getElementById('headerLoginButton');
    const headerSignupButton = document.getElementById('headerSignupButton');
    const headerLogoutButton = document.getElementById('headerLogoutButton');
    const userBadge = document.getElementById('userBadge');

    headerLoginButton?.addEventListener('click', () => {
      window.location.href = 'index.html#login';
    });
    headerSignupButton?.addEventListener('click', () => {
      window.location.href = 'index.html#signup';
    });
    headerLogoutButton?.addEventListener('click', async (event) => {
      event.preventDefault();
      try {
        await fetch(`${CMS_AUTH_API_BASE}/api/auth/logout`, {
          method: 'POST',
          credentials: 'include',
        });
      } catch {
        // ignore logout errors to keep UX smooth
      } finally {
        state.user = null;
        updateHeaderUI(null);
        persistUser(null);
        announceUserChange();
      }
    });
    userBadge?.addEventListener('click', () => {
      window.location.href = 'index.html#account';
    });
    userBadge?.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        window.location.href = 'index.html#account';
      }
    });
    document.querySelectorAll('.site-header [data-target]').forEach((button) => {
      button.addEventListener('click', () => {
        const target = button.dataset.target;
        if (!target) return;
        window.location.href = `index.html#${target}`;
      });
    });
  }

  async function refreshUser() {
    try {
      const response = await fetch(`${CMS_AUTH_API_BASE}/api/auth/me`, {
        credentials: 'include',
      });
      if (!response.ok) {
        throw new Error('unauthenticated');
      }
      state.user = await response.json();
    } catch {
      state.user = null;
    }
    persistUser(state.user);
    updateHeaderUI(state.user);
    announceUserChange();
  }

  function updateHeaderUI(user) {
    const loggedIn = Boolean(user);
    const headerAuthButtons = document.getElementById('headerAuthButtons');
    const headerLogoutButton = document.getElementById('headerLogoutButton');
    const userBadge = document.getElementById('userBadge');
    const userNameLabel = document.getElementById('userName');
    const userRoleLabel = document.getElementById('userRole');
    const userAvatar = document.getElementById('userAvatar');

    headerAuthButtons?.classList.toggle('hidden', loggedIn);
    if (headerLogoutButton) {
      headerLogoutButton.hidden = !loggedIn;
    }
    userBadge?.classList.toggle('hidden', !loggedIn);
    if (userNameLabel) {
      userNameLabel.textContent = loggedIn ? user.nickname || user.username || '사용자' : '';
    }
    if (userRoleLabel) {
      userRoleLabel.textContent = loggedIn ? user.role || '회원' : '';
    }
    if (userAvatar) {
      if (loggedIn) {
        const initial = (user.nickname || user.username || 'U').charAt(0).toUpperCase();
        userAvatar.textContent = initial;
        if (user.profilePictureUrl) {
          userAvatar.style.backgroundImage = `url(${user.profilePictureUrl})`;
          userAvatar.classList.add('has-image');
        } else {
          userAvatar.style.backgroundImage = 'none';
          userAvatar.classList.remove('has-image');
        }
      } else {
        userAvatar.textContent = 'U';
        userAvatar.style.backgroundImage = 'none';
        userAvatar.classList.remove('has-image');
      }
    }
  }

  function announceUserChange() {
    const detail = state.user ? { ...state.user } : null;
    document.dispatchEvent(new CustomEvent('cms:user-changed', { detail }));
  }

  let storageAvailable = null;

  function supportsStorage() {
    if (storageAvailable !== null) {
      return storageAvailable;
    }
    try {
      const testKey = '__cms_auth__';
      window.localStorage.setItem(testKey, '1');
      window.localStorage.removeItem(testKey);
      storageAvailable = true;
    } catch {
      storageAvailable = false;
    }
    return storageAvailable;
  }

  function hydrateUserFromStorage() {
    if (!supportsStorage()) {
      return;
    }
    try {
      const cached = window.localStorage.getItem(CMS_AUTH_STORAGE_KEY);
      if (!cached) {
        return;
      }
      state.user = JSON.parse(cached);
    } catch {
      state.user = null;
    }
  }

  function persistUser(user) {
    if (!supportsStorage()) {
      return;
    }
    try {
      if (user) {
        window.localStorage.setItem(CMS_AUTH_STORAGE_KEY, JSON.stringify(user));
      } else {
        window.localStorage.removeItem(CMS_AUTH_STORAGE_KEY);
      }
    } catch {
      // Ignore storage errors (e.g., quota exceeded)
    }
  }

  function bindStorageSync() {
    if (!supportsStorage()) {
      return;
    }
    window.addEventListener('storage', (event) => {
      if (event.key !== CMS_AUTH_STORAGE_KEY) {
        return;
      }
      let nextUser = null;
      if (event.newValue) {
        try {
          nextUser = JSON.parse(event.newValue);
        } catch {
          nextUser = null;
        }
      }
      state.user = nextUser;
      updateHeaderUI(state.user);
      announceUserChange();
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.CMSAuth = {
    getUser: () => state.user,
    refresh: refreshUser,
    onChange: (callback) => {
      if (typeof callback !== 'function') {
        return;
      }
      document.addEventListener('cms:user-changed', (event) => callback(event.detail));
    },
  };
})();
