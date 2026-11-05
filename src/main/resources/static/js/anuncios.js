// ─── Estado global ────────────────────────────────────────────────────────────
let _todasPropiedades = [];
let _listaFiltrada    = [];
let _paginaActual     = 1;
let _mapaCatalogo     = null;
let _marcadoresMapa   = null;
let _vistaCatalogo    = 'lista';
const POR_PAGINA      = 6;
const PALABRAS_COMUNES_BUSQUEDA = new Set([
  'de', 'del', 'la', 'las', 'el', 'los', 'en', 'con', 'y', 'o', 'un', 'una', 'unos', 'unas', 'por', 'para'
]);

function normalizarTextoFiltro(valor) {
  return String(valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es-CO')
    .trim();
}

// ─── Init ─────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  const contenedor = document.getElementById('lista-anuncios');
  if (!contenedor) return;

  const esIndex = window.location.pathname === '/' ||
                  window.location.pathname.endsWith('index.html') ||
                  window.location.pathname === '';
  const esFavoritos = document.body.dataset.page === 'favoritos';
  if (!esIndex && !esFavoritos) _vincularVistasCatalogo();

  try {
    await window.BelarFavoritos.ready;
    const res = await fetch('/api/propiedades/visibles');
    if (!res.ok) throw new Error('Error al cargar propiedades');
    const propiedades = await res.json();

    if (propiedades.length === 0) {
      contenedor.innerHTML = `
        <div class="empty-state" style="grid-column:1/-1;">
          <span class="material-symbols-rounded">home</span>
          <h3>Sin propiedades disponibles</h3>
          <p>En este momento no hay propiedades publicadas. Vuelve pronto.</p>
        </div>`;
      return;
    }

    // Más reciente primero
    propiedades.reverse();

    if (esIndex) {
      renderizarPropiedades(propiedades.slice(0, 3), contenedor);
      return;
    }

    if (esFavoritos) {
      const pintarFavoritos = () => {
        const idsGuardados = new Set(window.BelarFavoritos.ids());
        const favoritas = propiedades.filter(p => idsGuardados.has(String(p.id)));
        if (favoritas.length) {
          renderizarPropiedades(favoritas, contenedor);
          return;
        }

        const hayFavoritosGuardados = idsGuardados.size > 0;
        contenedor.innerHTML = `
          <div class="empty-state" style="grid-column:1/-1;">
            <span class="material-symbols-rounded">${hayFavoritosGuardados ? 'home' : 'favorite_border'}</span>
            <h3>${hayFavoritosGuardados ? 'Tus favoritos ya no están disponibles' : 'Aún no tienes favoritos'}</h3>
            <p>${hayFavoritosGuardados ? 'Las propiedades que guardaste ya no están publicadas.' : 'Guarda las propiedades que te interesan y aparecerán aquí.'}</p>
          </div>`;
      };

      pintarFavoritos();
      document.addEventListener('favoritos:cambio', pintarFavoritos);
      return;
    }

        // Página de catálogo: activar filtros
    _todasPropiedades = propiedades;
    _listaFiltrada    = [...propiedades];
    _vincularFiltros();

    const paramsUrl = new URLSearchParams(window.location.search);
    const busquedaInicial  = paramsUrl.get('busqueda');
    const ciudadInicial    = paramsUrl.get('ciudad');
    const operacionInicial = paramsUrl.get('operacion');
    const tipoInicial      = paramsUrl.get('tipo');

    if (ciudadInicial) {
      const selectCiudad = document.getElementById('fCiudad');
      let opcion = Array.from(selectCiudad.options)
        .find(o => normalizarTextoFiltro(o.value) === normalizarTextoFiltro(ciudadInicial));
      if (!opcion) {
        opcion = document.createElement('option');
        opcion.value = ciudadInicial;
        opcion.textContent = ciudadInicial;
        selectCiudad.appendChild(opcion);
      }
      if (opcion) selectCiudad.value = opcion.value;
    }

    if (busquedaInicial) {
      document.getElementById('fBusqueda').value = busquedaInicial;
    }

    if (operacionInicial) {
      const selectOperacion = document.getElementById('fOperacion');
      const opcion = Array.from(selectOperacion.options)
        .find(o => normalizarTextoFiltro(o.value) === normalizarTextoFiltro(operacionInicial));
      if (opcion) selectOperacion.value = opcion.value;
    }

    if (tipoInicial) {
      const selectTipo = document.getElementById('fTipo');
      const opcion = Array.from(selectTipo.options)
        .find(o => normalizarTextoFiltro(o.value) === normalizarTextoFiltro(tipoInicial));
      if (opcion) selectTipo.value = opcion.value;
    }

    if (busquedaInicial || ciudadInicial || operacionInicial || tipoInicial) _aplicarFiltros();
    else _mostrarPagina(1);

  } catch (err) {
    console.error('Error cargando anuncios:', err);
    contenedor.innerHTML = `
      <div class="empty-state" style="grid-column:1/-1;">
        <span class="material-symbols-rounded">error</span>
        <h3>Error al cargar</h3>
        <p>No se pudieron obtener las propiedades. Verifica tu conexión.</p>
      </div>`;
  }
});

// ─── Vincular eventos ─────────────────────────────────────────────────────────
function _vincularFiltros() {
  const btnFiltrar = document.getElementById('btnFiltrar');
  const btnLimpiar = document.getElementById('btnLimpiar');
  if (!btnFiltrar) return;

  btnFiltrar.addEventListener('click', _aplicarFiltros);
  btnLimpiar.addEventListener('click', _limpiarFiltros);

  // Búsqueda en vivo: se aplica sola mientras el usuario escribe
  let debounceTimer;
  document.getElementById('fBusqueda')?.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(_aplicarFiltros, 350);
  });

  ['fOperacion', 'fTipo', 'fCiudad', 'fHabitaciones', 'fBanos', 'fPrecio'].forEach(id => {
    document.getElementById(id)?.addEventListener('change', _aplicarFiltros);
  });
}

// ─── Aplicar filtros ──────────────────────────────────────────────────────────
function _aplicarFiltros() {
  const busqueda     = normalizarTextoFiltro(document.getElementById('fBusqueda')?.value);
  const palabrasBusqueda = busqueda
    .split(/\s+/)
    .filter(palabra => palabra && !PALABRAS_COMUNES_BUSQUEDA.has(palabra));
  const operacion    = document.getElementById('fOperacion')?.value    || '';
  const tipo         = document.getElementById('fTipo')?.value         || '';
  const ciudad       = document.getElementById('fCiudad')?.value       || '';
  const habitaciones = parseInt(document.getElementById('fHabitaciones')?.value) || 0;
  const banos        = parseInt(document.getElementById('fBanos')?.value)         || 0;
  const precioMax    = parseInt(document.getElementById('fPrecio')?.value)        || 0;

  _listaFiltrada = _todasPropiedades.filter(p => {
    const textoPropiedad = normalizarTextoFiltro([
      p.titulo, p.descripcion, p.barrio, p.ciudad, p.direccion, p.tipo, p.operacion
    ].filter(Boolean).join(' '));
    if (palabrasBusqueda.length && !palabrasBusqueda.every(palabra => textoPropiedad.includes(palabra))) return false;
    if (operacion && normalizarTextoFiltro(p.operacion || 'venta') !== normalizarTextoFiltro(operacion)) return false;
    if (tipo && normalizarTextoFiltro(p.tipo || 'otro') !== normalizarTextoFiltro(tipo)) return false;
    if (ciudad && normalizarTextoFiltro(p.ciudad) !== normalizarTextoFiltro(ciudad)) return false;
    if (habitaciones && p.habitaciones  < habitaciones)  return false;
    if (banos        && p.wc            < banos)         return false;
    if (precioMax    && p.precio        > precioMax)     return false;
    return true;
  });

  // Ordenar de menor a mayor desde el valor elegido (más cercanas primero)
  if (habitaciones || banos) {
    _listaFiltrada.sort((a, b) => {
      if (habitaciones) {
        const diff = a.habitaciones - b.habitaciones;
        if (diff !== 0) return diff;
      }
      if (banos) {
        return a.wc - b.wc;
      }
      return 0;
    });
  }

  _renderizarChips({ busqueda, operacion, tipo, ciudad, habitaciones, banos, precioMax });
  _mostrarPagina(1);
}

// ─── Limpiar filtros ──────────────────────────────────────────────────────────
function _limpiarFiltros() {
  ['fBusqueda', 'fOperacion', 'fTipo', 'fCiudad', 'fHabitaciones', 'fBanos', 'fPrecio'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  _listaFiltrada = [..._todasPropiedades];
  _renderizarChips({});
  _mostrarPagina(1);
}

// ─── Chips activos ────────────────────────────────────────────────────────────
function _renderizarChips({ busqueda, operacion, tipo, ciudad, habitaciones, banos, precioMax } = {}) {
  const wrapper = document.getElementById('chipsActivos');
  if (!wrapper) return;
  wrapper.innerHTML = '';

  const agregar = (label, clearFn) => {
    const chip = document.createElement('span');
    chip.className = 'chip-filtro';
    chip.innerHTML = `${window.escapeHtmlForHtml(label)}<button title="Quitar filtro"><span class="material-symbols-rounded">close</span></button>`;
    chip.querySelector('button').addEventListener('click', clearFn);
    wrapper.appendChild(chip);
  };

  if (busqueda) agregar(`"${busqueda}"`, () => {
    document.getElementById('fBusqueda').value = '';
    _aplicarFiltros();
  });
  if (operacion) agregar(operacion === 'arriendo' ? 'Arriendo' : 'Venta', () => {
    document.getElementById('fOperacion').value = '';
    _aplicarFiltros();
  });
  if (tipo) agregar(tipo.charAt(0).toUpperCase() + tipo.slice(1), () => {
    document.getElementById('fTipo').value = '';
    _aplicarFiltros();
  });
  if (ciudad) agregar(ciudad, () => {
    document.getElementById('fCiudad').value = '';
    _aplicarFiltros();
  });
  if (habitaciones) agregar(`${habitaciones}+ hab.`, () => {
    document.getElementById('fHabitaciones').value = '';
    _aplicarFiltros();
  });
  if (banos) agregar(`${banos}+ baños`, () => {
    document.getElementById('fBanos').value = '';
    _aplicarFiltros();
  });
  if (precioMax) {
    const label = `Hasta $${(precioMax / 1_000_000).toLocaleString('es-CO')}M`;
    agregar(label, () => {
      document.getElementById('fPrecio').value = '';
      _aplicarFiltros();
    });
  }
}

// ─── Paginación ───────────────────────────────────────────────────────────────
function _mostrarPagina(pagina) {
  _paginaActual = pagina;
  const total        = _listaFiltrada.length;
  const totalPaginas = Math.max(1, Math.ceil(total / POR_PAGINA));
  if (_paginaActual > totalPaginas) _paginaActual = totalPaginas;

  const inicio = (_paginaActual - 1) * POR_PAGINA;
  const fin    = Math.min(inicio + POR_PAGINA, total);

  renderizarPropiedades(_listaFiltrada.slice(inicio, fin), document.getElementById('lista-anuncios'));
  _actualizarMapa(_listaFiltrada);

  const textoEl = document.getElementById('resultados-texto');
  if (textoEl) {
    textoEl.textContent = total === 0
      ? 'No se encontraron propiedades con esos filtros.'
      : `Mostrando ${inicio + 1}–${fin} de ${total} propiedad${total !== 1 ? 'es' : ''}`;
  }

  _renderizarPaginacion(totalPaginas);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function _renderizarPaginacion(totalPaginas) {
  const pag = document.getElementById('paginacion');
  if (!pag) return;
  pag.innerHTML = '';
  if (totalPaginas <= 1) return;

  if (_paginaActual > 1) {
    const btn = document.createElement('button');
    btn.innerHTML = '<span class="material-symbols-rounded" style="font-size:2rem;">chevron_left</span>';
    btn.onclick = () => _mostrarPagina(_paginaActual - 1);
    pag.appendChild(btn);
  }
  for (let i = 1; i <= totalPaginas; i++) {
    const btn = document.createElement('button');
    btn.textContent = i;
    btn.classList.toggle('active', i === _paginaActual);
    btn.onclick = () => _mostrarPagina(i);
    pag.appendChild(btn);
  }
  if (_paginaActual < totalPaginas) {
    const btn = document.createElement('button');
    btn.innerHTML = '<span class="material-symbols-rounded" style="font-size:2rem;">chevron_right</span>';
    btn.onclick = () => _mostrarPagina(_paginaActual + 1);
    pag.appendChild(btn);
  }
}

function _vincularVistasCatalogo() {
  const lista = document.getElementById('lista-anuncios');
  const vistaMapa = document.getElementById('mapa-propiedades-view');
  const botonLista = document.getElementById('vista-lista');
  const botonMapa = document.getElementById('vista-mapa');
  if (!lista || !vistaMapa || !botonLista || !botonMapa) return;

  const cambiarVista = vista => {
    _vistaCatalogo = vista;
    const mostrarMapa = vista === 'mapa';
    lista.hidden = mostrarMapa;
    vistaMapa.hidden = !mostrarMapa;
    botonLista.setAttribute('aria-pressed', String(!mostrarMapa));
    botonMapa.setAttribute('aria-pressed', String(mostrarMapa));
    if (mostrarMapa) {
      _inicializarMapa();
      _actualizarMapa(_listaFiltrada);
      if (_mapaCatalogo) setTimeout(() => _mapaCatalogo.invalidateSize(), 0);
    }
  };

  botonLista.addEventListener('click', () => cambiarVista('lista'));
  botonMapa.addEventListener('click', () => cambiarVista('mapa'));
  if (new URLSearchParams(window.location.search).get('vista') === 'mapa') {
    cambiarVista('mapa');
  }
}

function _inicializarMapa() {
  if (_mapaCatalogo) return;
  const elementoMapa = document.getElementById('mapa-propiedades');
  if (!elementoMapa || !window.L) {
    console.error('No se pudo inicializar el mapa: Leaflet no está disponible.');
    if (elementoMapa) elementoMapa.hidden = true;
    const estadoVacio = document.getElementById('mapa-propiedades-vacio');
    if (estadoVacio) {
      estadoVacio.hidden = false;
      estadoVacio.querySelector('strong').textContent = 'No se pudo cargar el mapa';
      estadoVacio.querySelectorAll('span')[1].textContent = 'Comprueba tu conexión e inténtalo nuevamente.';
    }
    return;
  }

  _mapaCatalogo = L.map(elementoMapa).setView([10.4, -75.5], 6);
  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}', {
    attribution: 'Tiles &copy; Esri &mdash; Sources: Esri, HERE, Garmin, OpenStreetMap contributors, and the GIS user community',
    maxZoom: 19
  }).addTo(_mapaCatalogo);
  _marcadoresMapa = L.layerGroup().addTo(_mapaCatalogo);
}

function _actualizarMapa(propiedades) {
  if (_vistaCatalogo !== 'mapa') return;
  _inicializarMapa();
  if (!_mapaCatalogo || !_marcadoresMapa) return;

  _marcadoresMapa.clearLayers();
  const propiedadesConUbicacion = propiedades.filter(propiedad => {
    if (propiedad.latitud === null || propiedad.longitud === null ||
        propiedad.latitud === undefined || propiedad.longitud === undefined ||
        String(propiedad.latitud).trim() === '' || String(propiedad.longitud).trim() === '') {
      return false;
    }

    const latitud = Number(propiedad.latitud);
    const longitud = Number(propiedad.longitud);
    return Number.isFinite(latitud) && latitud >= -90 && latitud <= 90 &&
      Number.isFinite(longitud) && longitud >= -180 && longitud <= 180;
  });
  const elementoMapa = document.getElementById('mapa-propiedades');
  const estadoVacio = document.getElementById('mapa-propiedades-vacio');
  if (!propiedadesConUbicacion.length) {
    elementoMapa.hidden = true;
    estadoVacio.hidden = false;
    return;
  }

  elementoMapa.hidden = false;
  estadoVacio.hidden = true;
  const puntos = [];

  propiedadesConUbicacion.forEach(propiedad => {
    const posicion = [Number(propiedad.latitud), Number(propiedad.longitud)];
    puntos.push(posicion);
    const marcador = L.circleMarker(posicion, {
      radius: 11,
      color: '#ffffff',
      weight: 3,
      opacity: 1,
      fillColor: '#2179ff',
      fillOpacity: 1
    });
    const popup = document.createElement('div');
    const titulo = document.createElement('strong');
    titulo.textContent = propiedad.titulo || 'Propiedad';
    const precio = document.createElement('p');
    precio.textContent = `$${Number(propiedad.precio || 0).toLocaleString('es-CO')}`;
    const enlace = document.createElement('a');
    enlace.href = `/propiedad.html?id=${encodeURIComponent(propiedad.id)}`;
    enlace.textContent = 'Ver propiedad';
    popup.append(titulo, precio, enlace);
    marcador.bindPopup(popup).bindTooltip(titulo.textContent, { direction: 'top', offset: [0, -8] });
    _marcadoresMapa.addLayer(marcador);
  });

  requestAnimationFrame(() => {
    _mapaCatalogo.invalidateSize();
    if (puntos.length === 1) _mapaCatalogo.setView(puntos[0], 14);
    else _mapaCatalogo.fitBounds(L.latLngBounds(puntos), { padding: [40, 40], maxZoom: 13 });
  });
}

// ─── Render de cards ──────────────────────────────────────────────────────────
function renderizarPropiedades(lista, contenedor) {
  if (lista.length === 0) {
    contenedor.innerHTML = `
      <div class="empty-state" style="grid-column:1/-1;">
        <span class="material-symbols-rounded">search_off</span>
        <h3>Sin resultados</h3>
        <p>Intenta con otros filtros o amplía tu búsqueda.</p>
      </div>`;
    return;
  }

  const idsFavoritos = new Set(window.BelarFavoritos.ids());
  contenedor.innerHTML = lista.map(p => {
    const imagenUrl = window.escapeHtmlForHtml(
      window.safeMediaUrl(Array.isArray(p.imagenes) && p.imagenes.length > 0 ? p.imagenes[0] : '/img/default.jpg')
    );
    const id = encodeURIComponent(String(p.id ?? ''));
    const titulo = window.escapeHtmlForHtml(p.titulo);
    const ciudad = window.escapeHtmlForHtml(p.ciudad);
    const barrio = window.escapeHtmlForHtml(p.barrio);
    const descripcion = window.escapeHtmlForHtml(p.descripcion);
    const precio = Number(p.precio);
    const numero = value => Number.isFinite(Number(value)) ? Number(value) : 0;
    return `
      <div class="property-card">
        <div class="card-banner">
          <div class="img-holder" style="--width:4;--height:3;">
            <img loading="lazy" src="${imagenUrl}" alt="${titulo}" class="img-cover" onerror="this.src='/img/default.jpg'">
          </div>
          <span class="tag card-tag ${(p.operacion || 'venta') === 'arriendo' ? 'green' : ''}">${(p.operacion || 'venta') === 'arriendo' ? 'En Arriendo' : 'En Venta'}</span>
          <button type="button" class="property-favorite-toggle" data-favorite-toggle="${window.escapeHtmlForHtml(p.id)}"
            aria-pressed="${idsFavoritos.has(String(p.id))}"
            aria-label="${idsFavoritos.has(String(p.id)) ? 'Quitar de favoritos' : 'Guardar en favoritos'}"
            title="${idsFavoritos.has(String(p.id)) ? 'Quitar de favoritos' : 'Guardar en favoritos'}">
            <span class="material-symbols-rounded" aria-hidden="true">${idsFavoritos.has(String(p.id)) ? 'favorite' : 'favorite_border'}</span>
          </button>
        </div>
        <div class="card-body">
          <h3 class="card-title title-small">
            <a href="/propiedad.html?id=${id}">${titulo}</a>
          </h3>
          ${p.ciudad ? `
          <div style="display:flex;align-items:center;gap:4px;color:var(--neutral-60);font-size:var(--fs-label-medium);margin-bottom:8px;">
            <span class="material-symbols-rounded" style="font-size:1.6rem;color:var(--primary-100);">location_on</span>
            ${[barrio, ciudad].filter(Boolean).join(', ')}
          </div>` : ''}
          <p class="card-desc">${descripcion}</p>
          <p class="card-price">${Number.isFinite(precio) ? `$${precio.toLocaleString('es-CO')}` : ''}</p>
          <div class="card-features">
            <div class="card-feature-item">
              <span class="material-symbols-rounded">bed</span>
              <span>${numero(p.habitaciones)} hab.</span>
            </div>
            <div class="card-feature-item">
              <span class="material-symbols-rounded">bathroom</span>
              <span>${numero(p.wc)} baños</span>
            </div>
            <div class="card-feature-item">
              <span class="material-symbols-rounded">directions_car</span>
              <span>${numero(p.estacionamiento)} parq.</span>
            </div>
            ${numero(p.metrosCuadrados) ? `
            <div class="card-feature-item">
              <span class="material-symbols-rounded">square_foot</span>
              <span>${numero(p.metrosCuadrados)} m²</span>
            </div>` : ''}
          </div>
          <a href="/propiedad.html?id=${id}" class="btn btn-fill" style="width:100%;justify-content:center;">
            <span class="material-symbols-rounded">visibility</span>
            Ver propiedad
          </a>
        </div>
      </div>`;
  }).join('');
}