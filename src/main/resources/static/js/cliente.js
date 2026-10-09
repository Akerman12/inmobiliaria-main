document.addEventListener('DOMContentLoaded', async () => {
  const lista = document.getElementById('listaPropiedades');
  const totalDisponibles = document.getElementById('totalDisponibles');
  const totalVisitas = document.getElementById('totalVisitasProgramadas');
  const listaVisitas = document.getElementById('listaVisitasCliente');
  const modalVisita = document.getElementById('modalVisita');
  const formVisita = document.getElementById('formSolicitudVisita');
  const fechaVisita = document.getElementById('fechaHoraVisita');
  const estadoSolicitud = document.getElementById('estadoSolicitudVisita');
  let propiedadSeleccionada = null;

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
      const propiedades = await res.json();
      renderizar(propiedades);
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

  function renderizar(propiedades) {
    totalDisponibles.textContent = String(propiedades.length || 0);
    lista.innerHTML = '';

    if (!propiedades || propiedades.length === 0) {
      lista.innerHTML = '<div class="muted">No hay propiedades disponibles por el momento.</div>';
      return;
    }

    propiedades.forEach((p) => {
      const card = document.createElement('article');
      card.className = 'propiedad';
      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
          <span class="tag">${(p.operacion || 'venta') === 'arriendo' ? 'Arriendo' : 'Venta'}</span>
          <span class="muted">${p.ciudad || 'Sin ciudad'}</span>
        </div>
        <h4 style="margin:0 0 10px;">${p.titulo || 'Propiedad sin título'}</h4>
        <p class="muted" style="margin:0 0 10px;">${p.direccion || 'Dirección por confirmar'}</p>
        <p style="font-size:1.7rem; font-weight:700; margin:0 0 12px;">$${Number(p.precio || 0).toLocaleString('es-CO')}</p>
        <div style="display:flex; justify-content:space-between; color:#475569; font-size:.92rem; margin-bottom:14px;">
          <span>${p.habitaciones || 0} hab</span>
          <span>${p.wc || 0} baños</span>
          <span>${p.estacionamiento || 0} parqueadero</span>
        </div>
          <div style="display:flex; gap:10px;">
            <button class="btn btn-primary" data-action="visita" data-id="${p.id}" ${!p.propietarioId || (p.estadoComercial || '').toUpperCase() === 'EN_PROCESO' ? 'disabled title="La propiedad no está disponible para agendar"' : ''}>Solicitar visita</button>
          <button class="btn btn-outline" data-action="guardar" data-id="${p.id}">Guardar</button>
        </div>
      `;

      card.querySelectorAll('button[data-id]').forEach((button) => {
        button.addEventListener('click', async () => {
          const action = button.dataset.action;
          if (action === 'guardar') {
            alert('Propiedad guardada en favoritos.');
            return;
          }

          propiedadSeleccionada = p;
          document.getElementById('tituloPropiedadVisita').textContent = p.titulo || 'Propiedad seleccionada';
          estadoSolicitud.textContent = '';
          formVisita.reset();
          modalVisita.showModal();
        });
      });

      lista.appendChild(card);
    });
  }

  cargarPropiedades();
  cargarVisitas();
});
