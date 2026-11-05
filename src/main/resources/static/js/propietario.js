document.addEventListener('DOMContentLoaded', async () => {
  const listaPropiedades = document.getElementById('listaPropiedades');
  const totalPropiedades = document.getElementById('totalPropiedades');
  const disponibles = document.getElementById('disponibles');
  const enProceso = document.getElementById('enProceso');
  const visitasTotales = document.getElementById('visitasTotales');
  const panelLinks = document.querySelectorAll('.nav-panel-link');
  const panels = document.querySelectorAll('.owner-panel');
  const fechaDashboard = document.getElementById('fechaDashboard');
  const listaVisitas = document.getElementById('listaVisitas');
  const visitasTotal = document.getElementById('visitasTotal');
  const buscarPropiedades = document.getElementById('buscarPropiedades');
  const filtroEstadoPropiedades = document.getElementById('filtroEstadoPropiedades');
  const formPublicarPropiedad = document.getElementById('formPublicarPropiedad');
  const mensajePublicacion = document.getElementById('mensajePublicacion');
  let propiedadesActuales = [];
  const listaConsultas = document.getElementById('listaConsultas');
  const consultasTotal = document.getElementById('consultasTotal');
  const consultasPendientes = document.getElementById('consultasPendientes');
  const consultasAtendidas = document.getElementById('consultasAtendidas');
  const consultasNavBadge = document.getElementById('consultasNavBadge');
  const buscarConsultas = document.getElementById('buscarConsultas');
  const filtroConsultas = document.getElementById('filtroConsultas');
  const propiedadesCount = document.getElementById('propiedadesCount');
  let consultasActuales = [];
  let propiedadesConsultas = [];

  fechaDashboard.textContent = new Intl.DateTimeFormat('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  }).format(new Date());

  function mostrarPanel(id) {
    panels.forEach((panel) => {
      panel.classList.toggle('is-visible', panel.id === id);
    });

    panelLinks.forEach((link) => {
      const isActive = link.dataset.panel === id;
      link.classList.toggle('active', isActive);
    });
  }

  document.querySelectorAll('.nav-panel-link').forEach((link) => {
    link.addEventListener('click', (event) => {
      const panelId = link.dataset.panel;
      if (!panelId) return;
      event.preventDefault();
      mostrarPanel(panelId);
      history.replaceState(null, '', `#${panelId}`);
    });
  });

  document.querySelectorAll('[data-open-panel]').forEach((button) => {
    button.addEventListener('click', () => {
      const panelId = button.dataset.openPanel;
      mostrarPanel(panelId);
      history.replaceState(null, '', `#${panelId}`);
    });
  });

  formPublicarPropiedad.addEventListener('submit', publicarPropiedad);

  buscarConsultas.addEventListener('input', renderizarConsultas);
  filtroConsultas.addEventListener('change', renderizarConsultas);
  buscarPropiedades.addEventListener('input', renderizarListaPropiedades);
  filtroEstadoPropiedades.addEventListener('change', renderizarListaPropiedades);

  if (window.location.hash) {
    const hash = window.location.hash.replace('#', '');
    if (document.getElementById(hash)) {
      mostrarPanel(hash);
    }
  }

  async function cargarPropiedades() {
    let propiedades = [];
    try {
      const respuesta = await fetch('/api/propietario/propiedades');
      if (!respuesta.ok) {
        throw new Error('No autorizado');
      }

      propiedades = await respuesta.json();
      renderizarPropiedades(propiedades);
    } catch (error) {
      console.warn('No se pudo cargar la API del propietario; mostrando estado vacío.', error);
      renderizarPropiedades([]);
    }

    await cargarConsultas(propiedades);
    await cargarVisitas();
  }

  async function publicarPropiedad(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const id = form.elements.namedItem('id').value.trim();
    const imagenes = form.elements.namedItem('imagenes').files;
    if (!id && imagenes.length === 0) {
      mostrarMensajePublicacion('Adjunta al menos una imagen para crear el anuncio.', 'error');
      return;
    }
    if (imagenes.length > 4) {
      mostrarMensajePublicacion('Puedes adjuntar hasta cuatro imágenes.', 'error');
      return;
    }

    const boton = form.querySelector('[type="submit"]');
    const textoOriginal = boton.innerHTML;
    boton.disabled = true;
    boton.textContent = id ? 'Enviando cambios...' : 'Enviando a revisión...';
    mostrarMensajePublicacion('', '');
    const datos = {
      titulo: form.elements.namedItem('titulo').value.trim(),
      operacion: form.elements.namedItem('operacion').value,
      tipo: form.elements.namedItem('tipo').value,
      precio: Number(form.elements.namedItem('precio').value),
      metrosCuadrados: Number(form.elements.namedItem('metrosCuadrados').value),
      habitaciones: Number(form.elements.namedItem('habitaciones').value),
      wc: Number(form.elements.namedItem('wc').value),
      estacionamiento: Number(form.elements.namedItem('estacionamiento').value),
      ciudad: form.elements.namedItem('ciudad').value.trim(),
      barrio: form.elements.namedItem('barrio').value.trim(),
      direccion: form.elements.namedItem('direccion').value.trim(),
      descripcion: form.elements.namedItem('descripcion').value.trim()
    };
    const contenido = new FormData();
    contenido.append('propiedad', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
    Array.from(imagenes).forEach(imagen => contenido.append('imagenes', imagen));

    try {
      const response = await fetch(id
        ? `/api/propietario/propiedades/${encodeURIComponent(id)}`
        : '/api/propietario/propiedades', {
        method: id ? 'PUT' : 'POST',
        credentials: 'same-origin',
        body: contenido
      });
      if (!response.ok) {
        const detalle = await response.json().catch(() => ({}));
        throw new Error(detalle.detail || detalle.message || 'No se pudo enviar la propiedad.');
      }

      form.reset();
      form.elements.namedItem('id').value = '';
      form.elements.namedItem('imagenes').required = true;
      document.getElementById('tituloFormularioPropiedad').textContent = 'Publicar una propiedad';
      mostrarMensajePublicacion(
        'Anuncio enviado. Quedará en revisión antes de aparecer en el catálogo.',
        'success'
      );
      await cargarPropiedades();
      mostrarPanel('panel-propiedades');
      history.replaceState(null, '', '#panel-propiedades');
    } catch (error) {
      mostrarMensajePublicacion(error.message || 'No se pudo enviar el anuncio.', 'error');
    } finally {
      boton.innerHTML = textoOriginal;
      boton.disabled = false;
    }
  }

  function mostrarMensajePublicacion(texto, tipo) {
    mensajePublicacion.textContent = texto;
    mensajePublicacion.className = `portal-message ${tipo}`;
    mensajePublicacion.hidden = !texto;
  }

  function prepararEdicion(propiedad) {
    const form = formPublicarPropiedad;
    form.elements.namedItem('id').value = propiedad.id;
    ['titulo', 'operacion', 'tipo', 'precio', 'metrosCuadrados', 'habitaciones',
      'wc', 'estacionamiento', 'ciudad', 'barrio', 'direccion', 'descripcion']
      .forEach(campo => {
        form.elements.namedItem(campo).value = propiedad[campo] ?? '';
      });
    form.elements.namedItem('imagenes').required = false;
    document.getElementById('tituloFormularioPropiedad').textContent = 'Actualizar propiedad';
    mostrarMensajePublicacion('Al guardar, los cambios se enviarán nuevamente a revisión.', 'success');
    mostrarPanel('panel-publicar');
    history.replaceState(null, '', '#panel-publicar');
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  async function cargarVisitas() {
    try {
      const respuesta = await fetch('/api/propietario/visitas');
      if (!respuesta.ok) throw new Error('No se pudieron cargar las visitas');
      const visitas = await respuesta.json();
      visitasTotal.textContent = `${visitas.length} ${visitas.length === 1 ? 'solicitud' : 'solicitudes'}`;
      listaVisitas.replaceChildren();

      if (visitas.length === 0) {
        listaVisitas.innerHTML = '<div class="owner-empty">Todavía no tienes solicitudes de visita para tus propiedades.</div>';
        return;
      }

      visitas.forEach((visita) => {
        const articulo = document.createElement('article');
        articulo.className = 'consultation-item';
        const encabezado = document.createElement('div');
        encabezado.className = 'consultation-heading';
        const datos = document.createElement('div');
        const titulo = document.createElement('h3');
        titulo.className = 'consultation-name';
        titulo.textContent = visita.propiedadTitulo || 'Propiedad';
        const cliente = document.createElement('p');
        cliente.className = 'consultation-property';
        cliente.textContent = visita.clienteNombre || 'Cliente';
        const fecha = document.createElement('p');
        fecha.className = 'consultation-date';
        fecha.textContent = new Intl.DateTimeFormat('es-CO', {
          dateStyle: 'full',
          timeStyle: 'short'
        }).format(new Date(visita.fechaHora));
        datos.append(titulo, cliente, fecha);

        const estado = document.createElement('span');
        const estadoNormalizado = String(visita.estado || '').toUpperCase();
        estado.className = 'consultation-status';
        if (estadoNormalizado === 'CONFIRMADA') estado.classList.add('confirmed');
        if (estadoNormalizado === 'RECHAZADA') estado.classList.add('rejected');
        estado.textContent = etiquetaEstadoVisita(estadoNormalizado);
        encabezado.append(datos, estado);
        articulo.appendChild(encabezado);

        if (visita.clienteEmail) {
          const correo = document.createElement('a');
          correo.className = 'consultation-action';
          correo.href = `mailto:${visita.clienteEmail}`;
          correo.innerHTML = '<span class="material-symbols-rounded">mail</span>Contactar cliente';
          articulo.appendChild(correo);
        }
        if (visita.mensaje) {
          const mensaje = document.createElement('p');
          mensaje.className = 'consultation-message';
          mensaje.textContent = visita.mensaje;
          articulo.appendChild(mensaje);
        }

        const acciones = document.createElement('div');
        acciones.className = 'visit-management-actions';
        if (estadoNormalizado === 'SOLICITADA') {
          acciones.append(
            crearAccionVisita(visita.id, 'CONFIRMADA', 'check', 'Confirmar', 'confirm-visit'),
            crearAccionVisita(visita.id, 'RECHAZADA', 'close', 'Rechazar', 'reject-visit')
          );
        } else if (estadoNormalizado === 'CONFIRMADA') {
          acciones.append(crearAccionVisita(visita.id, 'COMPLETADA', 'task_alt', 'Marcar realizada', 'confirm-visit'));
        }
        if (acciones.childElementCount > 0) articulo.appendChild(acciones);
        listaVisitas.appendChild(articulo);
      });
    } catch (error) {
      console.error('Error cargando visitas:', error);
      visitasTotal.textContent = 'Error';
      listaVisitas.innerHTML = '<div class="owner-empty">No se pudieron cargar las visitas. Recarga la página para intentarlo de nuevo.</div>';
    }
  }

  function crearAccionVisita(id, estado, icono, texto, clase) {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = clase;
    boton.innerHTML = `<span class="material-symbols-rounded">${icono}</span>${texto}`;
    boton.addEventListener('click', async () => {
      boton.disabled = true;
      try {
        const respuesta = await fetch(`/api/propietario/visitas/${encodeURIComponent(id)}/estado`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ estado })
        });
        if (!respuesta.ok) throw new Error('No se pudo actualizar la solicitud');
        await cargarVisitas();
      } catch (error) {
        console.error(error);
        alert('No se pudo actualizar la solicitud de visita.');
        boton.disabled = false;
      }
    });
    return boton;
  }

  function etiquetaEstadoVisita(estado) {
    return ({
      SOLICITADA: 'Pendiente',
      CONFIRMADA: 'Confirmada',
      RECHAZADA: 'Rechazada',
      COMPLETADA: 'Realizada'
    })[estado] || 'Pendiente';
  }

  async function cargarConsultas(propiedades) {
    try {
      const respuesta = await fetch('/api/propietario/consultas');
      if (!respuesta.ok) throw new Error('No se pudieron cargar las consultas');
      consultasActuales = await respuesta.json();
      propiedadesConsultas = propiedades;
      consultasTotal.textContent = String(consultasActuales.length);
      consultasPendientes.textContent = String(consultasActuales.filter(consulta => !consulta.atendido).length);
      consultasAtendidas.textContent = String(consultasActuales.filter(consulta => consulta.atendido).length);
      consultasNavBadge.textContent = consultasPendientes.textContent;
      consultasNavBadge.hidden = consultasPendientes.textContent === '0';
      renderizarConsultas();
    } catch (error) {
      console.error('Error cargando consultas:', error);
      consultasTotal.textContent = 'Error';
      consultasPendientes.textContent = '–';
      consultasAtendidas.textContent = '–';
      consultasNavBadge.hidden = true;
      listaConsultas.innerHTML = '<div class="owner-empty">No se pudieron cargar las consultas. Recarga la página para intentarlo de nuevo.</div>';
    }
  }

  function renderizarConsultas() {
    const texto = buscarConsultas.value.trim().toLocaleLowerCase('es-CO');
    const estadoFiltro = filtroConsultas.value;
    const filtradas = consultasActuales.filter((consulta) => {
      const propiedad = propiedadesConsultas.find(item => item.id === consulta.casaInteres);
      const coincideTexto = [consulta.nombre, consulta.correo, consulta.mensaje, propiedad?.titulo]
        .some(valor => String(valor || '').toLocaleLowerCase('es-CO').includes(texto));
      const coincideEstado = estadoFiltro === 'TODAS'
        || (estadoFiltro === 'ATENDIDA' ? consulta.atendido : !consulta.atendido);
      return coincideTexto && coincideEstado;
    });

    listaConsultas.replaceChildren();
    if (filtradas.length === 0) {
      const vacio = document.createElement('div');
      vacio.className = 'owner-empty';
      vacio.textContent = consultasActuales.length === 0
        ? 'Todavía no hay mensajes asociados a tus propiedades.'
        : 'No hay consultas que coincidan con estos filtros.';
      listaConsultas.appendChild(vacio);
      return;
    }

    filtradas.forEach((consulta) => {
      const propiedad = propiedadesConsultas.find(item => item.id === consulta.casaInteres);
      const articulo = document.createElement('article');
      articulo.className = 'consultation-item';
      const encabezado = document.createElement('div');
      encabezado.className = 'consultation-heading';
      const datos = document.createElement('div');
      const nombre = document.createElement('h3');
      nombre.className = 'consultation-name';
      nombre.textContent = consulta.nombre || 'Visitante';
      const inmueble = document.createElement('p');
      inmueble.className = 'consultation-property';
      inmueble.textContent = propiedad?.titulo || 'Propiedad asociada';
      const fecha = document.createElement('p');
      fecha.className = 'consultation-date';
      fecha.textContent = consulta.fechaCreacion
        ? new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(consulta.fechaCreacion))
        : 'Fecha no disponible';
      datos.append(nombre, inmueble, fecha);
      const persona = document.createElement('div');
      persona.className = 'consultation-person';
      const avatar = document.createElement('span');
      avatar.className = 'consultation-avatar';
      avatar.setAttribute('aria-hidden', 'true');
      avatar.textContent = String(consulta.nombre || 'V').trim().split(/\s+/).slice(0, 2).map(parte => parte[0]).join('').toUpperCase();
      persona.append(avatar, datos);

      const estado = document.createElement('span');
      estado.className = `consultation-status${consulta.atendido ? ' attended' : ''}`;
      estado.textContent = consulta.atendido ? 'Atendida' : 'Pendiente';
      encabezado.append(persona, estado);

      const mensaje = document.createElement('p');
      mensaje.className = 'consultation-message';
      mensaje.textContent = consulta.mensaje || 'El visitante no dejó un mensaje adicional.';
      articulo.append(encabezado, mensaje);

      const acciones = document.createElement('div');
      acciones.className = 'consultation-actions';
      if (consulta.correo) {
        const correo = document.createElement('a');
        correo.className = 'consultation-action';
        correo.href = `mailto:${consulta.correo}`;
        correo.innerHTML = '<span class="material-symbols-rounded">mail</span>Responder por correo';
        acciones.appendChild(correo);
      }
      if (consulta.celular) {
        const telefono = document.createElement('a');
        telefono.className = 'consultation-action';
        telefono.href = `tel:${consulta.celular.replace(/[^\d+]/g, '')}`;
        telefono.innerHTML = '<span class="material-symbols-rounded">call</span>Llamar';
        acciones.appendChild(telefono);
      }
      if (acciones.childElementCount > 0) articulo.appendChild(acciones);
      listaConsultas.appendChild(articulo);
    });
  }

  function renderizarPropiedades(propiedades) {
    propiedadesActuales = propiedades || [];
    const propiedadesRecientes = document.getElementById('propiedadesRecientes');

    if (propiedadesActuales.length === 0) {
      listaPropiedades.innerHTML = '<div class="owner-empty">Aún no tienes inmuebles asignados a esta cuenta.</div>';
      totalPropiedades.textContent = '0';
      disponibles.textContent = '0';
      enProceso.textContent = '0';
      document.getElementById('pendientesRevision').textContent = '0';
      visitasTotales.textContent = '0';
      document.getElementById('resumenDisponibles').textContent = '0';
      document.getElementById('resumenEnProceso').textContent = '0';
      document.getElementById('barraDisponibles').style.width = '0%';
      document.getElementById('barraEnProceso').style.width = '0%';
      propiedadesRecientes.innerHTML = '<div class="owner-empty">Aún no tienes inmuebles asignados a esta cuenta.</div>';
      propiedadesCount.textContent = '0 inmuebles';
      return;
    }

    const total = propiedades.length;
    propiedadesCount.textContent = `${total} ${total === 1 ? 'inmueble' : 'inmuebles'}`;
    const pendientesCount = propiedades.filter(p => !p.visible).length;
    const disponiblesCount = propiedades.filter(p => p.visible && (p.estadoComercial || 'DISPONIBLE').toUpperCase() === 'DISPONIBLE').length;
    const enProcesoCount = propiedades.filter(p => p.visible && (p.estadoComercial || 'DISPONIBLE').toUpperCase() === 'EN_PROCESO').length;
    const visitas = propiedades.reduce((sum, p) => sum + (Number(p.visitas) || 0), 0);

    totalPropiedades.textContent = total;
    disponibles.textContent = disponiblesCount;
    enProceso.textContent = enProcesoCount;
    document.getElementById('pendientesRevision').textContent = pendientesCount;
    visitasTotales.textContent = visitas;
    document.getElementById('resumenDisponibles').textContent = disponiblesCount;
    document.getElementById('resumenEnProceso').textContent = enProcesoCount;
    document.getElementById('barraDisponibles').style.width = `${(disponiblesCount / total) * 100}%`;
    document.getElementById('barraEnProceso').style.width = `${(enProcesoCount / total) * 100}%`;

    propiedadesRecientes.innerHTML = propiedades.slice(0, 4).map((propiedad) => {
      const estaEnProceso = (propiedad.estadoComercial || 'DISPONIBLE').toUpperCase() === 'EN_PROCESO';
      const estadoTexto = !propiedad.visible ? 'En revisión' : (estaEnProceso ? 'En proceso' : 'Disponible');
      const imagen = propiedad.imagenes && propiedad.imagenes.length > 0
        ? propiedad.imagenes[0]
        : '/img/default.jpg';

      return `
        <article class="recent-property">
          <img class="recent-property-image" src="${escaparHtml(window.safeMediaUrl(imagen))}" alt="" onerror="this.src='/img/default.jpg'">
          <div>
            <div class="recent-property-name">${escaparHtml(propiedad.titulo || 'Propiedad sin título')}</div>
            <div class="recent-property-meta">${escaparHtml(propiedad.ciudad || 'Ubicación no especificada')} · ${propiedad.operacion === 'arriendo' ? 'Arriendo' : 'Venta'}</div>
          </div>
          <div style="display:flex;align-items:center;gap:8px;">
            <span class="owner-status ${!propiedad.visible || estaEnProceso ? 'in-process' : 'available'}">${estadoTexto}</span>
            <a class="owner-text-link" href="/propiedad.html?id=${encodeURIComponent(propiedad.id)}" aria-label="Ver ${escaparHtml(propiedad.titulo || 'propiedad')}">
              <span class="material-symbols-rounded">open_in_new</span>
            </a>
          </div>
        </article>
      `;
    }).join('');

    renderizarListaPropiedades();
  }

  function renderizarListaPropiedades() {
    const texto = buscarPropiedades.value.trim().toLocaleLowerCase('es-CO');
    const filtroEstado = filtroEstadoPropiedades.value;
    const filtradas = propiedadesActuales.filter((propiedad) => {
      const estado = (propiedad.estadoComercial || 'DISPONIBLE').toUpperCase();
      const enRevision = !propiedad.visible;
      const coincideTexto = [propiedad.titulo, propiedad.ciudad, propiedad.barrio]
        .some(valor => String(valor || '').toLocaleLowerCase('es-CO').includes(texto));
      const coincideEstado = filtroEstado === 'TODAS'
        || (filtroEstado === 'REVISION' ? enRevision : !enRevision && estado === filtroEstado);
      return coincideTexto && coincideEstado;
    });

    listaPropiedades.replaceChildren();
    if (filtradas.length === 0) {
      const vacio = document.createElement('div');
      vacio.className = 'owner-empty';
      vacio.textContent = propiedadesActuales.length === 0
        ? 'Aún no tienes inmuebles asignados a esta cuenta.'
        : 'No hay propiedades que coincidan con la búsqueda.';
      listaPropiedades.appendChild(vacio);
      return;
    }

    filtradas.forEach((propiedad) => {
      const estadoActual = (propiedad.estadoComercial || 'DISPONIBLE').toUpperCase();
      const enRevision = !propiedad.visible;
      const imagen = propiedad.imagenes && propiedad.imagenes.length > 0
        ? propiedad.imagenes[0]
        : '/img/default.jpg';
      const tarjeta = document.createElement('article');
      tarjeta.className = 'owner-property-card';
      tarjeta.innerHTML = `
        <div class="owner-property-image-wrap">
          <img class="owner-property-image" src="${escaparHtml(window.safeMediaUrl(imagen))}" alt="${escaparHtml(propiedad.titulo || 'Propiedad')}" onerror="this.src='/img/default.jpg'">
          <span class="owner-property-operation">${propiedad.operacion === 'arriendo' ? 'Arriendo' : 'Venta'}</span>
        </div>
        <div class="owner-property-content">
          <h3 class="owner-property-title">${escaparHtml(propiedad.titulo || 'Propiedad sin título')}</h3>
          <p class="owner-property-location"><span class="material-symbols-rounded">location_on</span>${escaparHtml([propiedad.barrio, propiedad.ciudad].filter(Boolean).join(', ') || 'Ubicación no especificada')}</p>
          <div class="owner-property-facts">
            <span><span class="material-symbols-rounded">bed</span>${Number(propiedad.habitaciones) || 0} hab.</span>
            <span><span class="material-symbols-rounded">square_foot</span>${Number(propiedad.metrosCuadrados) || 0} m²</span>
            <span title="Solicitudes de visita"><span class="material-symbols-rounded">event</span>${Number(propiedad.visitas) || 0} solic.</span>
          </div>
          <div class="owner-property-state">
            ${enRevision
              ? '<span class="owner-review-status"><span class="material-symbols-rounded">hourglass_top</span>Pendiente de revisión</span>'
              : `<label for="estado-${escaparHtml(propiedad.id)}">Estado comercial</label>
            <select id="estado-${escaparHtml(propiedad.id)}" data-property-status="${escaparHtml(propiedad.id)}">
              <option value="DISPONIBLE" ${estadoActual === 'DISPONIBLE' ? 'selected' : ''}>Disponible</option>
              <option value="EN_PROCESO" ${estadoActual === 'EN_PROCESO' ? 'selected' : ''}>En proceso</option>
            </select>`}
          </div>
          <div class="owner-property-footer">
            <span class="owner-property-price">$${Number(propiedad.precio || 0).toLocaleString('es-CO')}</span>
            <button type="button" class="btn btn-outline btn-sm" data-edit-property="${escaparHtml(propiedad.id)}">
              <span class="material-symbols-rounded">edit</span>Editar
            </button>
            <a class="btn btn-outline btn-sm" href="/propiedad.html?id=${encodeURIComponent(propiedad.id)}">
              <span class="material-symbols-rounded">open_in_new</span>Ver ficha
            </a>
          </div>
        </div>
      `;
      const selector = tarjeta.querySelector('[data-property-status]');
      if (selector) selector.addEventListener('change', () => actualizarEstadoPropiedad(propiedad, selector.value));
      tarjeta.querySelector('[data-edit-property]').addEventListener('click', () => prepararEdicion(propiedad));
      listaPropiedades.appendChild(tarjeta);
    });
  }

  async function actualizarEstadoPropiedad(propiedad, nuevoEstado) {
    const estadoAnterior = propiedad.estadoComercial || 'DISPONIBLE';
    try {
      const respuesta = await fetch(`/api/propietario/propiedades/${encodeURIComponent(propiedad.id)}/estado`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ estadoComercial: nuevoEstado })
      });
      if (!respuesta.ok) throw new Error('No autorizado');
      propiedad.estadoComercial = nuevoEstado;
      renderizarPropiedades(propiedadesActuales);
    } catch (error) {
      console.warn('No se pudo actualizar el estado.', error);
      propiedad.estadoComercial = estadoAnterior;
      alert('No se pudo cambiar el estado comercial.');
      renderizarListaPropiedades();
    }
  }

  function escaparHtml(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, caracter => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    })[caracter]);
  }

  cargarPropiedades();
});
