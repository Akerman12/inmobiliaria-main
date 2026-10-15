const filasCandidatos = document.getElementById('candidateRows');
const formulario = document.getElementById('optimizationForm');
const mensaje = document.getElementById('modelMessage');
const botonOptimizar = document.getElementById('optimizeButton');
const tarjetaResultado = document.getElementById('resultCard');

function escaparHtml(valor) {
  return String(valor ?? '').replace(/[&<>"']/g, caracter => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[caracter]);
}

async function cargarCandidatos() {
  try {
    const respuesta = await fetch('/api/propiedades');
    if (!respuesta.ok) {
      throw new Error('No fue posible cargar las propiedades.');
    }

    const propiedades = await respuesta.json();
    const visibles = propiedades.filter(propiedad => propiedad.visible);
    if (visibles.length === 0) {
      filasCandidatos.innerHTML = '<tr><td colspan="5">No hay propiedades publicadas para incluir en la optimización.</td></tr>';
      botonOptimizar.disabled = true;
      return;
    }

    filasCandidatos.innerHTML = visibles.map(propiedad => `
      <tr data-property-id="${escaparHtml(propiedad.id)}">
        <td><input class="candidate-select" type="checkbox" aria-label="Incluir ${escaparHtml(propiedad.titulo || 'propiedad sin título')}"></td>
        <td>${escaparHtml(propiedad.titulo || 'Propiedad sin título')}</td>
        <td><input class="form-control candidate-value" type="number" min="0" step="any" disabled aria-label="Comisión esperada para ${escaparHtml(propiedad.titulo || 'propiedad')}"></td>
        <td><input class="form-control candidate-value" type="number" min="0" step="any" disabled aria-label="Presupuesto publicitario para ${escaparHtml(propiedad.titulo || 'propiedad')}"></td>
        <td><input class="form-control candidate-value" type="number" min="0" step="any" disabled aria-label="Horas de asesor para ${escaparHtml(propiedad.titulo || 'propiedad')}"></td>
      </tr>
    `).join('');
  } catch (error) {
    filasCandidatos.innerHTML = '<tr><td colspan="4">Ocurrió un error al cargar las propiedades.</td></tr>';
    mostrarMensaje(error.message, true);
  }
}

filasCandidatos.addEventListener('change', evento => {
  if (!evento.target.matches('.candidate-select')) {
    return;
  }

  const fila = evento.target.closest('tr');
  fila.querySelectorAll('.candidate-value').forEach(input => {
    input.disabled = !evento.target.checked;
    input.required = evento.target.checked;
    if (!evento.target.checked) {
      input.value = '';
    }
  });
  fila.classList.toggle('candidate-selected', evento.target.checked);
});

function mostrarMensaje(texto, esError = false) {
  mensaje.textContent = texto;
  mensaje.classList.toggle('error', esError);
}

function formatoNumero(valor) {
  return Number(valor).toLocaleString('es-CO', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function renderizarResultado(resultado) {
  document.getElementById('resultSummary').innerHTML = `
    <div class="result-metric">Comisión máxima esperada<strong>$ ${formatoNumero(resultado.comisionTotalEsperadaMillones)} millones COP</strong></div>
    <div class="result-metric">Publicidad utilizada<strong>${formatoNumero(resultado.presupuestoPublicidadUsadoMillones)} / ${formatoNumero(resultado.presupuestoPublicidadDisponibleMillones)} millones COP</strong></div>
    <div class="result-metric">Horas de asesor utilizadas<strong>${formatoNumero(resultado.horasAsesorUsadas)} / ${formatoNumero(resultado.horasAsesorDisponibles)}</strong></div>
  `;
  document.getElementById('resultRows').innerHTML = resultado.decisiones.map(decision => `
    <tr>
      <td>${escaparHtml(decision.titulo)}</td>
      <td>${formatoNumero(decision.intensidadPromocion * 100)}%</td>
      <td>${formatoNumero(decision.comisionAportadaMillones)}</td>
      <td>${formatoNumero(decision.presupuestoPublicidadAsignadoMillones)}</td>
      <td>${formatoNumero(decision.horasAsesorAsignadas)}</td>
    </tr>
  `).join('');
  tarjetaResultado.hidden = false;
  tarjetaResultado.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

formulario.addEventListener('submit', async evento => {
  evento.preventDefault();
  tarjetaResultado.hidden = true;
  mostrarMensaje('');

  const filasSeleccionadas = [...filasCandidatos.querySelectorAll('tr[data-property-id]')]
    .filter(fila => fila.querySelector('.candidate-select').checked);

  if (filasSeleccionadas.length === 0) {
    mostrarMensaje('Selecciona al menos una propiedad para calcular la optimización.', true);
    return;
  }

  const candidatos = filasSeleccionadas.map(fila => {
    const [comisionEsperadaMillones, presupuestoPublicidadMillones, horasAsesor] =
      [...fila.querySelectorAll('.candidate-value')].map(input => Number(input.value));
    return {
      propiedadId: fila.dataset.propertyId,
      titulo: fila.cells[1].textContent.trim(),
      comisionEsperadaMillones,
      presupuestoPublicidadMillones,
      horasAsesor
    };
  });

  botonOptimizar.disabled = true;
  botonOptimizar.textContent = 'Calculando...';
  try {
    const respuesta = await fetch('/api/admin/optimizacion-promocion/simplex', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        presupuestoPublicidadDisponibleMillones: Number(document.getElementById('budget').value),
        horasAsesorDisponibles: Number(document.getElementById('hours').value),
        candidatos
      })
    });
    const resultado = await respuesta.json();
    if (!respuesta.ok) {
      throw new Error(resultado.message || resultado.error || 'No fue posible calcular la optimización.');
    }

    renderizarResultado(resultado);
    mostrarMensaje('Se encontró la asignación óptima para los recursos indicados.');
  } catch (error) {
    mostrarMensaje(error.message || 'Ocurrió un error al calcular la optimización.', true);
  } finally {
    botonOptimizar.disabled = false;
    botonOptimizar.innerHTML = '<span class="material-symbols-rounded">functions</span>Calcular con Simplex';
  }
});

cargarCandidatos();
