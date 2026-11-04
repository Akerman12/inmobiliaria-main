document.addEventListener('DOMContentLoaded', () => {
  const loginTab = document.getElementById('login-tab');
  const registerTab = document.getElementById('register-tab');
  const loginPanel = document.getElementById('login-panel');
  const registerPanel = document.getElementById('register-panel');
  const message = document.getElementById('account-message');
  const registerForm = document.getElementById('register-form');
  const accountRole = document.getElementById('account-role');
  const registerName = document.getElementById('register-name');
  const registerPhone = document.getElementById('register-phone');
  const params = new URLSearchParams(window.location.search);
  const loginPassword = document.getElementById('login-password');
  const toggleLoginPassword = document.getElementById('toggleLoginPassword');

  toggleLoginPassword.addEventListener('click', () => {
    const mostrar = loginPassword.type === 'password';
    loginPassword.type = mostrar ? 'text' : 'password';
    toggleLoginPassword.setAttribute('aria-pressed', String(mostrar));
    toggleLoginPassword.setAttribute('aria-label', mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña');
    toggleLoginPassword.querySelector('.material-symbols-rounded').textContent =
      mostrar ? 'visibility_off' : 'visibility';
  });

  function showMessage(text, isError = false) {
    message.textContent = text;
    message.classList.toggle('error', isError);
    message.hidden = false;
  }

  function setMode(mode) {
    const registering = mode === 'register';
    loginTab.setAttribute('aria-selected', String(!registering));
    registerTab.setAttribute('aria-selected', String(registering));
    loginPanel.hidden = registering;
    registerPanel.hidden = !registering;
    message.hidden = true;
  }

  loginTab.addEventListener('click', () => setMode('login'));
  registerTab.addEventListener('click', () => setMode('register'));

  document.querySelectorAll('[data-role]').forEach(button => {
    button.addEventListener('click', () => {
      accountRole.value = button.dataset.role;
      document.querySelectorAll('[data-role]').forEach(option => {
        option.setAttribute('aria-pressed', String(option === button));
      });
    });
  });

  if (params.get('error') === 'role') {
    setMode('login');
    showMessage('Esta cuenta no puede ingresar desde el acceso público. Usa la página correspondiente a tu rol.', true);
  } else if (params.get('error') === 'true') {
    setMode('login');
    showMessage('Correo o contraseña incorrectos.', true);
  } else if (params.get('registered') === 'true') {
    setMode('login');
    showMessage('Tu cuenta fue creada. Ingresa para continuar.');
  } else if (params.get('logout') === 'true') {
    setMode('login');
    showMessage('Has cerrado sesión.');
  } else if (params.get('mode') === 'register') {
    setMode('register');
  }

  registerForm.addEventListener('submit', async event => {
    event.preventDefault();
    message.hidden = true;
    const nombre = registerName.value.trim();
    const celular = registerPhone.value.trim();
    const digitosCelular = celular.replace(/\D/g, '').length;

    if (nombre.length < 2 || nombre.length > 100 || !/^[\p{L}][\p{L} '\u2019.-]*$/u.test(nombre)
        || Array.from(nombre.matchAll(/\p{L}/gu)).length < 2) {
      showMessage('Ingresa un nombre válido, con al menos dos letras y sin números.', true);
      registerName.focus();
      return;
    }

    if (!/^\+?[0-9][0-9\s().-]*$/.test(celular) || digitosCelular < 7 || digitosCelular > 15) {
      showMessage('Ingresa un celular válido con entre 7 y 15 dígitos.', true);
      registerPhone.focus();
      return;
    }

    const submit = registerForm.querySelector('[type="submit"]');
    submit.disabled = true;
    const datos = Object.fromEntries(new FormData(registerForm).entries());

    try {
      const response = await fetch('/api/cuenta/registro', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(datos)
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.message || result.detail || 'No se pudo crear la cuenta.');

      document.getElementById('login-email').value = result.email || datos.email;
      document.getElementById('login-password').focus();
      setMode('login');
      showMessage('Cuenta creada. Ingresa con tu correo y contraseña.');
      registerForm.reset();
    } catch (error) {
      console.error('Error al crear la cuenta:', error);
      showMessage(error.message || 'No se pudo crear la cuenta. Inténtalo nuevamente.', true);
    } finally {
      submit.disabled = false;
    }
  });
});
