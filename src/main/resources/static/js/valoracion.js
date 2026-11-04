document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('valuation-form');
  const message = document.getElementById('valuation-message');
  const resultTitle = document.getElementById('valuation-result-title');
  const resultCopy = document.getElementById('valuation-result-copy');
  const price = document.getElementById('valuation-price');
  const resultDetails = document.getElementById('valuation-result-details');
  const minimum = document.getElementById('valuation-minimum');
  const maximum = document.getElementById('valuation-maximum');
  const errorCopy = document.getElementById('valuation-error-copy');
  const currency = new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0
  });
  const formatMillions = value => `${currency.format(value / 1_000_000)} millones`;

  form.addEventListener('submit', async event => {
    event.preventDefault();
    message.hidden = true;
    message.classList.remove('error');
    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;

    const datos = {
      ciudad: form.elements.namedItem('ciudad').value,
      habitaciones: Number(form.elements.namedItem('habitaciones').value),
      wc: Number(form.elements.namedItem('wc').value),
      estacionamiento: Number(form.elements.namedItem('estacionamiento').value),
      metrosCuadrados: Number(form.elements.namedItem('metrosCuadrados').value)
    };

    try {
      const response = await fetch('/api/prediccion/precio', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(datos)
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'No se pudo calcular la estimación.');

      resultTitle.textContent = 'Estimación de referencia';
      resultCopy.textContent = data.mensaje || 'El modelo calculó el siguiente precio de referencia.';
      price.textContent = data.precioFormateado;
      price.hidden = false;
      const error = Number(data.errorPromedioAbsoluto);
      const estimate = Number(data.precioEstimado);
      const trainingCount = Number(data.cantidadDatosEntrenamiento);
      if (!Number.isFinite(error) || !Number.isFinite(estimate) || !Number.isFinite(trainingCount) || trainingCount < 1) {
        throw new Error('La respuesta del modelo no incluye un margen de referencia válido.');
      }
      minimum.textContent = formatMillions(Math.max(0, estimate - error));
      maximum.textContent = formatMillions(estimate + error);
      errorCopy.textContent = `El margen de ${formatMillions(error)} corresponde al error absoluto promedio del modelo en sus ${trainingCount} datos de entrenamiento.`;
      resultDetails.hidden = false;
    } catch (error) {
      console.error('Error al calcular la valoración:', error);
      resultDetails.hidden = true;
      price.hidden = true;
      message.textContent = error.message || 'No se pudo calcular la estimación. Inténtalo nuevamente.';
      message.classList.add('error');
      message.hidden = false;
    } finally {
      submit.disabled = false;
    }
  });

  form.requestSubmit();
});
