document.addEventListener('DOMContentLoaded', async () => {
  const lista = document.getElementById('listaPropiedades');
  const totalDisponibles = document.getElementById('totalDisponibles');
  const totalVisitas = document.getElementById('totalVisitasProgramadas');
  const totalFavoritos = document.getElementById('totalFavoritos');
  const listaVisitas = document.getElementById('listaVisitasCliente');
  const listaFavoritos = document.getElementById('listaFavoritosCliente');
  const modalVisita = document.getElementById('modalVisita');
  const formVisita = document.getElementById('formSolicitudVisita');
  const fechaVisita = document.getElementById('fechaHoraVisita');
  const estadoSolicitud = document.getElementById('estadoSolicitudVisita');
  const buscarPropiedades = document.getElementById('buscarPropiedadesCliente');
  const filtroOperacion = document.getElementById('filtroOperacionCliente');
  const paneles = document.querySelectorAll('.client-panel');
  const enlacesPanel = document.querySelectorAll('[data-client-panel]');
  let propiedadesActuales = [];
  let propiedadSeleccionada = null;

  function actualizarTotalFavoritos(ids = window.BelarFavoritos.ids()) {
    totalFavoritos.textContent = String(ids.length);
  }

  function mostrarPanel(id) {
    paneles.forEach(panel => {
      const visible = panel.id === id;
      panel.hidden = !visible;
      panel.classList.toggle('is-visible', visible);
    });
    enlacesPanel.forEach(enlace => enlace.classList.toggle('active', enlace.dataset.clientPanel === id));
  }

  enlacesPanel.forEach(enlace => enlace.addEventListener('click', event => {
    event.preventDefault();
    mostrarPanel(enlace.dataset.clientPanel);
    history.replaceState(null, '', `#${enlace.dataset.clientPanel}`);
  }));
  document.querySelectorAll('[data-open-client]').forEach(button => button.addEventListener('click', () => {
    const id = button.dataset.openClient;
    mostrarPanel(id);
    history.replaceState(null, '', `#${id}`);
  }));

  const panelInicial = document.getElementById(window.location.hash.slice(1))?.classList.contains('client-panel')
    ? window.location.hash.slice(1)
    : 'panel-dashboard';
  mostrarPanel(panelInicial);

  document.addEventListener('favoritos:cambio', event => {
    actualizarTotalFavoritos(event.detail.ids);
    renderizarFavoritos();
  });
  actualizarTotalFavoritos();
  buscarPropiedades.addEventListener('input', renderizarListaFiltrada);
  filtroOperacion.addEventListener('change', renderizarListaFiltrada);

  fechaVisita.min = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);

  document.getElementById('cancelarSolicitudVisita').addEventListener('click', () => {
    modalVisita.close();
  });

  formVisita.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!propiedadSeleccionada || !fechaVisita.value) return;

    const submitButton = formVisita.querySelector('[type="submit"]');
    submitButton.disabled = true;
    estadoSolicitud.textContent = '';

    try {
      const respuesta = await fetch('/api/cliente/visitas', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          propiedadId: propiedadSeleccionada.id,
          fechaHora: fechaVisita.value,
          mensaje: formVisita.elements.namedItem('mensaje').value
        })
      });

      if (!respuesta.ok) {
        const error = await respuesta.json().catch(() => ({}));
        throw new Error(error.detail || 'No se pudo enviar la solicitud.');
      }

      modalVisita.close();
      formVisita.reset();
      await cargarVisitas();
    } catch (error) {
      estadoSolicitud.textContent = error.message;
    } finally {
      submitButton.disabled = false;
    }
  });

  async function cargarPropiedades() {
    try {
      const res = await fetch('/api/cliente/propiedades', { credentials: 'same-origin' });
      if (!res.ok) throw new Error('No autorizado');
      propiedadesActuales = await res.json();
      renderizarListaFiltrada();
      renderizarFavoritos();
    } catch (error) {
      console.warn('No hay propiedades para cliente', error);
      lista.innerHTML = '<div class="muted">No hay propiedades disponibles por el momento.</div>';
      totalDisponibles.textContent = '0';
    }
  }

  async function cargarVisitas() {
    try {
      const respuesta = await fetch('/api/cliente/visitas', { credentials: 'same-origin' });
      if (!respuesta.ok) throw new Error('No se pudieron cargar las visitas');
      const visitas = await respuesta.json();
      const activas = visitas.filter(visita => ['SOLICITADA', 'CONFIRMADA'].includes(visita.estado)).length;
      totalVisitas.textContent = String(activas);
      listaVisitas.replaceChildren();

      if (visitas.length === 0) {
        listaVisitas.innerHTML = '<p class="muted">Aún no has solicitado visitas.</p>';
        return;
      }

      visitas.forEach((visita) => {
        const tarjeta = document.createElement('article');
        tarjeta.className = 'visit-request';
        const encabezado = document.createElement('div');
        encabezado.className = 'visit-request-top';
        const detalles = document.createElement('div');
        const titulo = document.createElement('h3');
        titulo.textContent = visita.propiedadTitulo || 'Propiedad';
        const fecha = document.createElement('p');
        fecha.textContent = new Intl.DateTimeFormat('es-CO', {
          dateStyle: 'full',
          timeStyle: 'short'
        }).format(new Date(visita.fechaHora));
        detalles.append(titulo, fecha);

        const estado = document.createElement('span');
        estado.className = `visit-request-status ${String(visita.estado || '').toLowerCase()}`;
        estado.textContent = etiquetaEstado(visita.estado);
        encabezado.append(detalles, estado);
        tarjeta.appendChild(encabezado);

        if (visita.mensaje) {
          const mensaje = document.createElement('p');
          mensaje.textContent = visita.mensaje;
          tarjeta.appendChild(mensaje);
        }
        listaVisitas.appendChild(tarjeta);
      });
    } catch (error) {
      console.error(error);
      totalVisitas.textContent = '0';
      listaVisitas.innerHTML = '<p class="muted">No se pudieron cargar tus visitas.</p>';
    }
  }

  function etiquetaEstado(estado) {
    return ({
      SOLICITADA: 'Pendiente de confirmación',
      CONFIRMADA: 'Confirmada',
      RECHAZADA: 'No confirmada',
      COMPLETADA: 'Realizada'
    })[estado] || 'Pendiente';
  }

  function renderizarListaFiltrada() {
    const termino = buscarPropiedades.value.trim().toLocaleLowerCase('es-CO');
    const operacion = filtroOperacion.value;
    const filtradas = propiedadesActuales.filter(propiedad => {
      const texto = [propiedad.titulo, propiedad.ciudad, propiedad.barrio, propiedad.direccion]
        .join(' ').toLocaleLowerCase('es-CO');
      return texto.includes(termino) && (operacion === 'TODAS' || propiedad.operacion === operacion);
    });
    renderizar(filtradas, lista, 'No hay propiedades que coincidan con los filtros.');
  }

  function renderizarFavoritos() {
    const ids = new Set(window.BelarFavoritos.ids().map(String));
    const favoritos = propiedadesActuales.filter(propiedad => ids.has(String(propiedad.id)));
    renderizar(favoritos, listaFavoritos, 'Aún no tienes favoritos. Explora el catálogo y guarda las propiedades que te interesan.');
  }

  function escaparHtml(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, caracter => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[caracter]);
  }

  function renderizar(propiedades, destino, mensajeVacio = 'No hay propiedades disponibles por el momento.') {
    if (destino === lista) totalDisponibles.textContent = String(propiedadesActuales.length || 0);
    destino.replaceChildren();

    if (!propiedades || propiedades.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'portal-empty';
      vacio.textContent = destino === lista && propiedadesActuales.length === 0
        ? 'No hay propiedades disponibles por el momento.'
        : mensajeVacio;
      destino.appendChild(vacio);
      return;
    }

    propiedades.forEach(p => {
      const id = escaparHtml(p.id);
      const estaGuardada = window.BelarFavoritos.estaGuardada(p.id);
      const imagen = escaparHtml(window.safeMediaUrl(p.imagenes?.[0] || '/img/default.jpg'));
      const card = document.createElement('article');
      card.className = 'propiedad';
      card.innerHTML = `
        <img class="client-property-image" src="${imagen}" alt="${escaparHtml(p.titulo || 'Propiedad')}" onerror="this.src='/img/default.jpg'">
        <div class="client-property-content">
          <div class="client-property-top"><span class="tag">${p.operacion === 'arriendo' ? 'Arriendo' : 'Venta'}</span><span class="muted">${escaparHtml(p.ciudad || 'Ciudad por confirmar')}</span></div>
          <h3>${escaparHtml(p.titulo || 'Propiedad sin título')}</h3>
          <p>${escaparHtml([p.barrio, p.direccion].filter(Boolean).join(' · ') || 'Dirección por confirmar')}</p>
          <span class="client-property-price">$${Number(p.precio || 0).toLocaleString('es-CO')}</span>
          <div class="client-property-facts">
            <span>${Number(p.habitaciones) || 0} habitaciones</span><span>${Number(p.wc) || 0} baños</span><span>${Number(p.metrosCuadrados) || 0} m²</span>
          </div>
          <div class="client-property-actions">
            <button class="btn btn-primary" data-action="visita" data-id="${id}" ${!p.propietarioId || (p.estadoComercial || '').toUpperCase() === 'EN_PROCESO' ? 'disabled title="La propiedad no está disponible para agendar"' : ''}>
              <span class="material-symbols-rounded">event</span>Solicitar visita
            </button>
            <button class="btn btn-outline" data-favorite-toggle="${id}" aria-pressed="${estaGuardada}">
              <span class="material-symbols-rounded" aria-hidden="true">${estaGuardada ? 'favorite' : 'favorite_border'}</span>${estaGuardada ? 'Guardada' : 'Guardar'}
            </button>
            <a class="btn btn-outline" href="/propiedad.html?id=${encodeURIComponent(p.id)}" aria-label="Ver detalle de ${escaparHtml(p.titulo || 'propiedad')}">
              <span class="material-symbols-rounded">open_in_new</span>Detalle
            </a>
          </div>
        </div>
      `;

      card.querySelector('[data-action="visita"]')?.addEventListener('click', () => {
        propiedadSeleccionada = p;
        document.getElementById('tituloPropiedadVisita').textContent = p.titulo || 'Propiedad seleccionada';
        estadoSolicitud.textContent = '';
        formVisita.reset();
        modalVisita.showModal();
      });
      destino.appendChild(card);
    });
  }

  window.BelarFavoritos.ready.then(() => {
    actualizarTotalFavoritos();
    cargarPropiedades();
    cargarVisitas();
    renderizarFavoritos();
  });
});
