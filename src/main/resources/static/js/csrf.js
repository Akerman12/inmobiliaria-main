(() => {
  const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
  const originalFetch = window.fetch.bind(window);
  let tokenPromise;

  function getToken() {
    if (!tokenPromise) {
      tokenPromise = originalFetch('/api/csrf', { credentials: 'same-origin' })
        .then(response => {
          if (!response.ok) throw new Error('No se pudo obtener el token de seguridad.');
          return response.json();
        })
        .then(data => data.token)
        .catch(error => {
          tokenPromise = null;
          throw error;
        });
    }
    return tokenPromise;
  }

  window.fetch = async (input, init = {}) => {
    const requestUrl = input instanceof Request ? input.url : input;
    const url = new URL(requestUrl, window.location.href);
    const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    if (url.origin !== window.location.origin || safeMethods.has(method)) {
      return originalFetch(input, init);
    }

    const token = await getToken();
    const headers = new Headers(input instanceof Request ? input.headers : undefined);
    new Headers(init.headers).forEach((value, name) => headers.set(name, value));
    headers.set('X-XSRF-TOKEN', token);

    return originalFetch(input, {
      ...init,
      headers,
      credentials: init.credentials || 'same-origin'
    });
  };

  document.addEventListener('submit', async event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    const method = (form.method || 'GET').toUpperCase();
    const action = new URL(form.action, window.location.href);
    if (safeMethods.has(method) || action.origin !== window.location.origin) return;

    event.preventDefault();
    try {
      const token = await getToken();
      let field = form.querySelector('input[name="_csrf"]');
      if (!field) {
        field = document.createElement('input');
        field.type = 'hidden';
        field.name = '_csrf';
        form.appendChild(field);
      }
      field.value = token;
      HTMLFormElement.prototype.submit.call(form);
    } catch (error) {
      console.error('No se pudo enviar el formulario por un error de seguridad.', error);
    }
  }, true);
})();
