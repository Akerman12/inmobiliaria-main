(() => {
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[character]);

  const safeMediaUrl = value => {
    try {
      const url = new URL(String(value ?? ''), window.location.origin);
      const sameOrigin = url.origin === window.location.origin;
      if (url.username || url.password) return '/img/default.jpg';
      if ((sameOrigin && ['http:', 'https:'].includes(url.protocol)) || url.protocol === 'https:') {
        return url.href;
      }
    } catch (error) {
      console.warn('Se rechazó una URL de imagen no válida.', error);
    }
    return '/img/default.jpg';
  };

  window.escapeHtmlForHtml = escapeHtml;
  window.safeMediaUrl = safeMediaUrl;
})();
