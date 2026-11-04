document.addEventListener('DOMContentLoaded', async () => {
  const contenido = document.getElementById('contenido-propiedad');
  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');

  if (!id) {
    contenido.innerHTML = `
      <div class="container section">
        <div class="empty-state">
          <span class="material-symbols-rounded">error</span>
          <h3>Propiedad no encontrada</h3>
          <p>No se especificó una propiedad válida.</p>
          <a href="/anuncio.html" class="btn btn-fill" style="margin-top:16px;display:inline-flex;">Ver todas las propiedades</a>
        </div>
      </div>`;
    return;
  }

  try {
    const res = await fetch(`http://localhost:8080/api/propiedades/${id}`);
    if (!res.ok) throw new Error('Propiedad no encontrada');
    const p = await res.json();
    const enProceso = (p.estadoComercial || '').toUpperCase() === 'EN_PROCESO';
    const operacion = (p.operacion || 'venta').toLowerCase();
    const etiquetaOperacion = operacion === 'arriendo' ? 'En Arriendo' : 'En Venta';
    const contactoHtml = enProceso
      ? `<div role="status" style="width:100%;padding:14px 16px;border-radius:var(--radius-small);background:#FFF7ED;color:#9A3412;display:flex;align-items:center;gap:10px;line-height:1.5;">
          <span class="material-symbols-rounded">hourglass_top</span>
          Esta propiedad está en proceso de ${operacion}. Por ahora no recibe consultas.
        </div>`
      : `<a href="/contacto.html?propiedadId=${encodeURIComponent(p.id)}" class="btn btn-fill" style="width:100%;justify-content:center;">
          <span class="material-symbols-rounded">mail</span>
          Contactar asesor
        </a>`;
    const calculadoraHtml = operacion === 'venta' && Number.isFinite(Number(p.precio)) && Number(p.precio) > 0
      ? `<section class="mortgage-calculator" aria-labelledby="mortgage-title">
          <h2 id="mortgage-title">
            <span class="material-symbols-rounded" aria-hidden="true">calculate</span>
            Calculadora de cuota
          </h2>
          <p>Simula un pago mensual aproximado para esta propiedad.</p>
          <label for="mortgage-down-payment">Cuota inicial <output id="mortgage-down-payment-label">20%</output></label>
          <input id="mortgage-down-payment" type="range" min="0" max="80" step="5" value="20">
          <label for="mortgage-term">Plazo</label>
          <select id="mortgage-term" class="form-control">
            <option value="10">10 años</option>
            <option value="15">15 años</option>
            <option value="20" selected>20 años</option>
            <option value="25">25 años</option>
            <option value="30">30 años</option>
          </select>
          <label for="mortgage-rate">Tasa anual estimada (%)</label>
          <input id="mortgage-rate" class="form-control" type="number" min="0" max="100" step="0.1" value="12">
          <p class="mortgage-payment-label">Cuota mensual estimada</p>
          <output id="mortgage-payment" class="mortgage-payment" aria-live="polite"></output>
          <small>Simulación orientativa; no incluye seguros, impuestos ni otros cargos del crédito.</small>
        </section>`
      : '';

    const imagenes = (p.imagenes && p.imagenes.length > 0) ? p.imagenes : ['/img/default.jpg'];
    const imagenPrincipal = imagenes[0];

    const galeriaHTML = imagenes.length > 1
      ? `<div class="thumb-gallery" style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap;">
          ${imagenes.map((src, i) => `
            <img src="${src}" alt="Imagen ${i+1}" onerror="this.src='/img/default.jpg'"
              style="width:80px;height:60px;object-fit:cover;border-radius:8px;cursor:pointer;border:2px solid ${i===0?'var(--primary-100)':'transparent'};"
              onclick="document.getElementById('imgPrincipal').src=this.src;
                       document.querySelectorAll('.thumb-gallery img').forEach(t=>t.style.borderColor='transparent');
                       this.style.borderColor='var(--primary-100)';">
          `).join('')}
         </div>`
      : '';

    // Location string for display
    const locationParts = [p.barrio, p.ciudad].filter(Boolean);
    const locationStr = locationParts.length ? locationParts.join(', ') : '';

    // Build map section HTML (only if coordinates exist)
    const hasCoords = p.latitud && p.longitud;
    const mapSectionHTML = hasCoords ? `
      <div class="admin-card" style="margin-top:24px;">
        <div class="admin-card-header">
          <h2 style="font-size:var(--fs-title-medium);">
            <span class="material-symbols-rounded" style="font-size:2rem;vertical-align:middle;color:var(--primary-100);margin-right:6px;">location_on</span>
            Ubicación
          </h2>
        </div>
        <div class="admin-card-body" style="padding:0;">
          ${p.direccion || locationStr ? `
            <div style="padding:16px 24px 0;display:flex;align-items:center;gap:8px;color:var(--neutral-40);font-size:var(--fs-body-medium);">
              <span class="material-symbols-rounded" style="font-size:1.8rem;color:var(--primary-100);">place</span>
              ${[p.direccion, p.barrio, p.ciudad].filter(Boolean).join(' · ')}
            </div>` : ''}
          <div id="mapaPropiedad" style="width:100%;height:360px;border-radius:0 0 var(--radius-large) var(--radius-large);"></div>
        </div>
      </div>` : '';

    document.title = `${p.titulo} | Bienes Raíces`;

    contenido.innerHTML = `
      <!-- Breadcrumb -->
      <div style="background:var(--white);padding:20px 0;border-bottom:1px solid var(--neutral-95);margin-bottom:0;">
        <div class="container">
          <div style="display:flex;align-items:center;gap:8px;font-size:var(--fs-label-medium);color:var(--neutral-60);">
            <a href="/" style="color:var(--primary-100);">Inicio</a>
            <span class="material-symbols-rounded" style="font-size:1.8rem;">chevron_right</span>
            <a href="/anuncio.html" style="color:var(--primary-100);">Propiedades</a>
            <span class="material-symbols-rounded" style="font-size:1.8rem;">chevron_right</span>
            <span>${p.titulo}</span>
          </div>
        </div>
      </div>

      <section class="section">
        <div class="container">
          <div class="property-detail-grid">

            <!-- Gallery + Description + Map -->
            <div>
              <div class="property-gallery">
                <img id="imgPrincipal" src="${imagenPrincipal}" alt="${p.titulo}" onerror="this.src='/img/default.jpg'">
              </div>
              ${galeriaHTML}

              <div class="admin-card" style="margin-top:24px;">
                <div class="admin-card-header">
                  <h2 style="font-size:var(--fs-title-medium);">Descripción</h2>
                </div>
                <div class="admin-card-body">
                  <p style="color:var(--neutral-40);font-size:var(--fs-body-large);line-height:1.8;">${p.descripcion || 'Sin descripción disponible.'}</p>
                </div>
              </div>

              ${mapSectionHTML}
            </div>

            <!-- Info Card -->
            <div>
              <div class="property-info-card">
                <span class="tag ${operacion === 'arriendo' ? 'green' : ''}" style="margin-bottom:16px;">${etiquetaOperacion} · ${(p.tipo || 'otro').charAt(0).toUpperCase() + (p.tipo || 'otro').slice(1)}</span>
                ${enProceso ? '<span class="tag" style="display:table;margin:-8px 0 16px;background:#FFF7ED;color:#9A3412;">En proceso</span>' : ''}
                <h1 style="font-size:var(--fs-headline-small);color:var(--neutral-10);margin-bottom:8px;line-height:1.3;">${p.titulo}</h1>

                ${locationStr ? `
                  <div style="display:flex;align-items:center;gap:6px;color:var(--neutral-60);font-size:var(--fs-body-medium);margin-bottom:16px;">
                    <span class="material-symbols-rounded" style="font-size:1.8rem;color:var(--primary-100);">location_on</span>
                    ${locationStr}
                  </div>` : ''}

                <p style="font-size:3.2rem;font-weight:700;color:var(--primary-100);margin-bottom:24px;">$${p.precio.toLocaleString('es-CO')}</p>

                <div class="property-features-list">
                  <div class="property-feature">
                    <span class="material-symbols-rounded">bed</span>
                    <div><strong>${p.habitaciones}</strong><span>Habitaciones</span></div>
                  </div>
                  <div class="property-feature">
                    <span class="material-symbols-rounded">bathroom</span>
                    <div><strong>${p.wc}</strong><span>Baños</span></div>
                  </div>
                  <div class="property-feature">
                    <span class="material-symbols-rounded">directions_car</span>
                    <div><strong>${p.estacionamiento}</strong><span>Parqueadero(s)</span></div>
                  </div>
                  ${p.metrosCuadrados ? `
                  <div class="property-feature">
                    <span class="material-symbols-rounded">square_foot</span>
                    <div><strong>${p.metrosCuadrados} m²</strong><span>Área</span></div>
                  </div>` : ''}
                  ${p.ciudad ? `
                  <div class="property-feature">
                    <span class="material-symbols-rounded">location_city</span>
                    <div><strong>${p.ciudad}</strong><span>Ciudad</span></div>
                  </div>` : ''}
                  ${p.barrio ? `
                  <div class="property-feature">
                    <span class="material-symbols-rounded">home_pin</span>
                    <div><strong>${p.barrio}</strong><span>Barrio</span></div>
                  </div>` : ''}
                </div>

                ${calculadoraHtml}

                <div style="margin-top:24px;display:flex;flex-direction:column;gap:12px;">
                  ${contactoHtml}
                  ${hasCoords ? `
                  <button onclick="document.getElementById('mapaPropiedad')?.scrollIntoView({behavior:'smooth'})"
                    class="btn btn-outline" style="width:100%;justify-content:center;">
                    <span class="material-symbols-rounded">map</span>
                    Ver en el mapa
                  </button>` : ''}
                  <a href="/anuncio.html" class="btn btn-outline" style="width:100%;justify-content:center;">
                    <span class="material-symbols-rounded">arrow_back</span>
                    Ver más propiedades
                  </a>
                </div>
              </div>
            </div>

          </div>
        </div>
      </section>
    `;

    if (calculadoraHtml) {
      const inicial = document.getElementById('mortgage-down-payment');
      const plazo = document.getElementById('mortgage-term');
      const tasa = document.getElementById('mortgage-rate');
      const porcentajeInicial = document.getElementById('mortgage-down-payment-label');
      const cuotaMensual = document.getElementById('mortgage-payment');
      const formatoPrecio = new Intl.NumberFormat('es-CO', {
        style: 'currency',
        currency: 'COP',
        maximumFractionDigits: 0
      });

      const actualizarCuota = () => {
        const porcentaje = Number(inicial.value);
        const tasaAnual = Number(tasa.value);
        const meses = Number(plazo.value) * 12;
        const precio = Number(p.precio);
        porcentajeInicial.value = `${porcentaje}%`;
        if (!Number.isFinite(tasaAnual) || tasaAnual < 0 || tasaAnual > 100) {
          cuotaMensual.textContent = 'Ingresa una tasa entre 0 y 100%.';
          return;
        }
        const capital = precio * (1 - porcentaje / 100);
        const tasaMensual = tasaAnual / 1200;
        const cuota = tasaMensual === 0
          ? capital / meses
          : capital * tasaMensual / (1 - Math.pow(1 + tasaMensual, -meses));
        cuotaMensual.textContent = formatoPrecio.format(cuota);
      };

      inicial.addEventListener('input', actualizarCuota);
      plazo.addEventListener('change', actualizarCuota);
      tasa.addEventListener('input', actualizarCuota);
      actualizarCuota();
    }

    // Initialize Leaflet map if coordinates exist
    if (hasCoords) {
      // Load Leaflet dynamically if not already loaded
      const initLeafletMap = () => {
        const mapEl = document.getElementById('mapaPropiedad');
        if (!mapEl || !window.L) return;

        const mapa = L.map('mapaPropiedad', { scrollWheelZoom: false }).setView([p.latitud, p.longitud], 16);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© <a href="https://openstreetmap.org/copyright">OpenStreetMap</a>',
          maxZoom: 19
        }).addTo(mapa);

        // Custom marker
        const icon = L.divIcon({
          html: `<div style="
            width: 40px; height: 40px;
            background: var(--primary-100, #2179FF);
            border-radius: 50% 50% 50% 0;
            transform: rotate(-45deg);
            border: 3px solid white;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            display:flex; align-items:center; justify-content:center;
          "><span style="transform:rotate(45deg);color:white;font-size:1.4rem;font-family:'Material Symbols Rounded';">home</span></div>`,
          iconSize: [40, 40],
          iconAnchor: [20, 40],
          className: ''
        });

        L.marker([p.latitud, p.longitud], { icon })
          .addTo(mapa)
          .bindPopup(`
            <div style="font-family:Montserrat,sans-serif;min-width:180px;">
              <strong style="font-size:1.4rem;">${p.titulo}</strong><br>
              ${p.barrio ? `<span style="color:#666;font-size:1.2rem;">${p.barrio}${p.ciudad ? ', ' + p.ciudad : ''}</span><br>` : ''}
              <span style="color:#2179FF;font-weight:700;font-size:1.3rem;">$${p.precio.toLocaleString('es-CO')}</span>
            </div>
          `, { maxWidth: 220 })
          .openPopup();
      };

      if (window.L) {
        initLeafletMap();
      } else {
        // Load Leaflet dynamically
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
        document.head.appendChild(link);

        const script = document.createElement('script');
        script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
        script.onload = initLeafletMap;
        document.head.appendChild(script);
      }
    }

  } catch (err) {
    console.error('Error cargando propiedad:', err);
    contenido.innerHTML = `
      <div class="container section">
        <div class="empty-state">
          <span class="material-symbols-rounded">error</span>
          <h3>Propiedad no encontrada</h3>
          <p>No pudimos cargar esta propiedad.</p>
          <a href="/anuncio.html" class="btn btn-fill" style="margin-top:16px;display:inline-flex;">Ver todas las propiedades</a>
        </div>
      </div>`;
  }
});