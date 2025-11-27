window.__API_BASE__ = 'https://api.cms-community.com';
window.__COGNITO_DOMAIN__ = 'https://cms-community.auth.ap-northeast-1.amazoncognito.com';
window.__COGNITO_USER_CLIENT_ID__ = '4mstq5ghpsrmoo4m73fm6fj99h';
window.__COGNITO_REDIRECT_URI__ = 'https://user.cms-community.com';

(function ensureDomReadyHelper() {
  if (window.__runWhenDocumentReady) {
    return;
  }
  window.__runWhenDocumentReady = function (callback) {
    if (typeof callback !== 'function') {
      return;
    }
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  };
})();

(function loadCommonAuth() {
  if (document.querySelector('script[data-common-auth-loaded="true"]')) {
    return;
  }
  const version = window.__ASSET_VERSION__ || 'v0';
  const script = document.createElement('script');
  script.src = `common-auth.js?v=${version}`;
  script.defer = true;
  script.dataset.commonAuthLoaded = 'true';
  document.head.appendChild(script);
})();

if (!window.__cmsAuthReady__) {
  window.__cmsAuthReady__ = new Promise((resolve) => {
    if (window.CMSAuth) {
      resolve(window.CMSAuth);
      return;
    }
    window.__cmsAuthResolve = resolve;
    document.addEventListener(
      'cms:user-changed',
      () => {
        if (window.__cmsAuthResolve) {
          window.__cmsAuthResolve(window.CMSAuth || null);
          window.__cmsAuthResolve = null;
        }
      },
      { once: true }
    );
  });
}
