document.addEventListener('DOMContentLoaded', () => {
  const perfilForm = document.querySelector('#formPerfilCliente, #formPerfilPropietario');
  const passwordForm = document.querySelector('#formPasswordCliente, #formPasswordPropietario');
  const message = document.querySelector('#mensajePerfilCliente, #mensajePerfilPropietario');
  if (!perfilForm || !passwordForm || !message) return;

  const submit = async (form, url, method, body, defaultMessage) => {
    const button = form.querySelector('[type="submit"]');
    button.disabled = true;
    try {
      const response = await fetch(url, {
        method,
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.detail || error.message || defaultMessage);
      }
      return response.status === 204 ? null : response.json();
    } finally {
      button.disabled = false;
    }
  };

  function showMessage(text, type) {
    message.textContent = text;
    message.className = `portal-message ${type}`;
    message.hidden = false;
  }

  async function loadProfile() {
    try {
      const response = await fetch('/api/cuenta/perfil', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('No se pudieron cargar los datos de tu perfil.');
      const profile = await response.json();
      perfilForm.elements.namedItem('nombre').value = profile.nombre || '';
      perfilForm.elements.namedItem('email').value = profile.email || '';
      perfilForm.elements.namedItem('celular').value = profile.celular || '';
      const greeting = document.getElementById('saludoCliente')
        || document.getElementById('saludoPropietario');
      if (greeting && profile.nombre) greeting.textContent = `Hola, ${profile.nombre.trim().split(/\s+/)[0]}`;
    } catch (error) {
      showMessage(error.message, 'error');
    }
  }

  perfilForm.addEventListener('submit', async event => {
    event.preventDefault();
    message.hidden = true;
    try {
      const profile = await submit(perfilForm, '/api/cuenta/perfil', 'PUT', {
        nombre: perfilForm.elements.namedItem('nombre').value,
        celular: perfilForm.elements.namedItem('celular').value
      }, 'No se pudieron guardar los cambios.');
      const greeting = document.getElementById('saludoCliente')
        || document.getElementById('saludoPropietario');
      if (greeting && profile?.nombre) greeting.textContent = `Hola, ${profile.nombre.trim().split(/\s+/)[0]}`;
      showMessage('Tus datos se actualizaron correctamente.', 'success');
    } catch (error) {
      showMessage(error.message, 'error');
    }
  });

  passwordForm.addEventListener('submit', async event => {
    event.preventDefault();
    message.hidden = true;
    const newPassword = passwordForm.elements.namedItem('passwordNueva').value;
    const confirmation = passwordForm.elements.namedItem('confirmarPassword').value;
    if (newPassword !== confirmation) {
      showMessage('La confirmación no coincide con la nueva contraseña.', 'error');
      return;
    }

    try {
      await submit(passwordForm, '/api/cuenta/password', 'PATCH', {
        passwordActual: passwordForm.elements.namedItem('passwordActual').value,
        passwordNueva: newPassword
      }, 'No se pudo actualizar la contraseña.');
      passwordForm.reset();
      showMessage('Tu contraseña se actualizó correctamente.', 'success');
    } catch (error) {
      showMessage(error.message, 'error');
    }
  });

  loadProfile();
});
