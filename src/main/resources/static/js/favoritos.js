(() => {
  const STORAGE_KEY = 'belar-favoritos';
  const OWNER_KEY = 'belar-favoritos-cuenta';
  let idsActuales = [];
  let sesionVerificada = false;
  let usuarioAutenticado = false;
  let sincronizacionDisponible = true;

  function mostrarError(mensaje) {
    let aviso = document.querySelector('[data-favorites-error]');
    if (!aviso) {
      aviso = document.createElement('div');
      aviso.className = 'favorites-error';
      aviso.dataset.favoritesError = '';
      aviso.setAttribute('role', 'alert');
      document.body.appendChild(aviso);
    }
    aviso.textContent = mensaje;
  }

  function leerIds() {
    try {
      const guardados = window.localStorage.getItem(STORAGE_KEY);
      if (!guardados) return [];

      const ids = JSON.parse(guardados);
      if (!Array.isArray(ids) || ids.some(id => typeof id !== 'string' && typeof id !== 'number')) {
        throw new TypeError('El formato guardado de favoritos no es válido.');
      }
      return [...new Set(ids.map(String))];
    } catch (error) {
      console.error('No fue posible leer los favoritos guardados.', error);
      mostrarError('No se pudieron cargar los favoritos desde este navegador.');
      return [];
    }
  }

  function actualizarInterfaz(ids) {
    document.querySelectorAll('[data-favorites-count]').forEach(contador => {
      contador.textContent = String(ids.length);
      contador.hidden = ids.length === 0;
      const enlace = contador.closest('a');
      if (enlace) enlace.setAttribute('aria-label', `Mis favoritos: ${ids.length}`);
    });

    document.querySelectorAll('[data-favorite-toggle]').forEach(boton => {
      const guardada = ids.includes(String(boton.dataset.favoriteToggle));
      boton.setAttribute('aria-pressed', String(guardada));
      boton.setAttribute('aria-label', guardada ? 'Quitar de favoritos' : 'Guardar en favoritos');
      boton.title = guardada ? 'Quitar de favoritos' : 'Guardar en favoritos';
      const icono = boton.querySelector('.material-symbols-rounded');
      if (icono) icono.textContent = guardada ? 'favorite' : 'favorite_border';
      const etiqueta = boton.querySelector('[data-favorite-label]');
      if (etiqueta) etiqueta.textContent = guardada ? 'Guardada' : 'Guardar';
    });
  }

  function emitirCambio(ids) {
    idsActuales = ids;
    actualizarInterfaz(ids);
    document.dispatchEvent(new CustomEvent('favoritos:cambio', { detail: { ids } }));
  }

  async function alternar(id) {
    if (!sesionVerificada) {
      throw new Error('No se pudo verificar tu sesión. Recarga la página antes de guardar el favorito.');
    }

    const ids = idsActuales;
    const idPropiedad = String(id);
    const nuevosIds = ids.includes(idPropiedad)
      ? ids.filter(guardado => guardado !== idPropiedad)
      : [...ids, idPropiedad];

    if (usuarioAutenticado) {
      if (!sincronizacionDisponible) {
        throw new Error('No se pueden sincronizar los favoritos en este momento. Inténtalo nuevamente.');
      }

      const respuesta = await fetch('/api/cuenta/favoritos', {
        method: 'PUT',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ids: nuevosIds })
      });
      if (!respuesta.ok) {
        const detalle = await respuesta.json().catch(() => ({}));
        throw new Error(detalle.detail || 'No se pudo sincronizar el favorito con tu cuenta.');
      }
      emitirCambio(await respuesta.json());
    } else {
      try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(nuevosIds));
      } catch (error) {
        console.error('No fue posible guardar los favoritos.', error);
        mostrarError('No se pudo guardar el favorito. Revisa la configuración de almacenamiento del navegador.');
        throw error;
      }
      emitirCambio(nuevosIds);
    }

    const aviso = document.querySelector('[data-favorites-error]');
    if (aviso) aviso.remove();
  }

  document.addEventListener('favoritos:cambio', event => {
    actualizarInterfaz(event.detail.ids);
  });

  document.addEventListener('click', event => {
    const boton = event.target.closest('[data-favorite-toggle]');
    if (!boton) return;

    const botones = [...document.querySelectorAll('[data-favorite-toggle]')];
    botones.forEach(item => { item.disabled = true; });
    alternar(boton.dataset.favoriteToggle)
      .catch(error => {
        console.error('No se pudo actualizar el favorito seleccionado.', error);
        mostrarError(error.message || 'No se pudo actualizar el favorito.');
      })
      .finally(() => botones.forEach(item => { item.disabled = false; }));
  });

  window.addEventListener('storage', event => {
    if (sesionVerificada && !usuarioAutenticado && event.key === STORAGE_KEY) {
      idsActuales = leerIds();
      actualizarInterfaz(idsActuales);
      document.dispatchEvent(new CustomEvent('favoritos:cambio', { detail: { ids: idsActuales } }));
    }
  });

  async function iniciar() {
    const respuestaPerfil = await fetch('/api/cuenta/perfil', { credentials: 'same-origin' });
    if (respuestaPerfil.status === 401) {
      sesionVerificada = true;
      if (window.localStorage.getItem(OWNER_KEY)) {
        window.localStorage.removeItem(STORAGE_KEY);
        window.localStorage.removeItem(OWNER_KEY);
        idsActuales = [];
      } else idsActuales = leerIds();
      actualizarInterfaz(idsActuales);
      document.dispatchEvent(new CustomEvent('favoritos:cambio', { detail: { ids: idsActuales } }));
      return;
    }
    if (!respuestaPerfil.ok) {
      throw new Error('No se pudo verificar la sesión de tu cuenta.');
    }

    sesionVerificada = true;
    usuarioAutenticado = true;
    const perfil = await respuestaPerfil.json();
    if (!perfil.email) throw new Error('No se pudo identificar la cuenta para sincronizar favoritos.');
    const propietarioLocal = window.localStorage.getItem(OWNER_KEY);
    if (propietarioLocal && propietarioLocal !== perfil.email) {
      window.localStorage.removeItem(STORAGE_KEY);
      idsActuales = [];
      emitirCambio(idsActuales);
    }

    const respuestaFavoritos = await fetch('/api/cuenta/favoritos', { credentials: 'same-origin' });
    if (!respuestaFavoritos.ok) {
      sincronizacionDisponible = false;
      throw new Error('No se pudieron cargar los favoritos de tu cuenta.');
    }

    const favoritosServidor = await respuestaFavoritos.json();
    const idsServidor = [...new Set(favoritosServidor.map(String))];
    const idsLocales = propietarioLocal && propietarioLocal !== perfil.email ? [] : leerIds();
    const favoritosCombinados = [...new Set([...idsServidor, ...idsLocales])];
    idsActuales = idsServidor;

    if (favoritosCombinados.length !== idsServidor.length) {
      const respuestaGuardado = await fetch('/api/cuenta/favoritos', {
        method: 'PUT',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ids: favoritosCombinados })
      });
      if (!respuestaGuardado.ok) {
        sincronizacionDisponible = false;
        throw new Error('No se pudieron vincular a tu cuenta los favoritos guardados en este navegador.');
      }
      idsActuales = await respuestaGuardado.json();
    }
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(idsActuales));
    window.localStorage.setItem(OWNER_KEY, perfil.email);
    actualizarInterfaz(idsActuales);
    document.dispatchEvent(new CustomEvent('favoritos:cambio', { detail: { ids: idsActuales } }));
  }

  const ready = iniciar().catch(error => {
    console.error('No fue posible sincronizar favoritos.', error);
    mostrarError(error.message || 'No fue posible sincronizar tus favoritos.');
    actualizarInterfaz(idsActuales);
  });

  window.BelarFavoritos = {
    ids: () => idsActuales,
    estaGuardada: id => idsActuales.includes(String(id)),
    ready
  };

  actualizarInterfaz(idsActuales);
})();
