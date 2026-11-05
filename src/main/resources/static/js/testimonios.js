document.addEventListener('DOMContentLoaded', async () => {
  const contenedor = document.getElementById('lista-testimonios');
  if (!contenedor) return;

  try {
    const res = await fetch('/api/testimonios/visibles');
    if (!res.ok) throw new Error('Error al cargar testimonios');
    const testimonios = await res.json();

    if (testimonios.length === 0) {
      contenedor.closest('section')?.remove();
      return;
    }

    contenedor.innerHTML = testimonios.map(t => {
      const valorEstrellas = Number(t.estrellas);
      const estrellasNumero = Number.isInteger(valorEstrellas) && valorEstrellas >= 1 && valorEstrellas <= 5
        ? valorEstrellas
        : 5;
      const estrellas = '★'.repeat(estrellasNumero) + '☆'.repeat(5 - estrellasNumero);
      const nombre = window.escapeHtmlForHtml(t.nombre || '?');
      const mensaje = window.escapeHtmlForHtml(t.mensaje);
      const rol = window.escapeHtmlForHtml(t.rol);
      const inicial = nombre.charAt(0).toUpperCase();
      return `
        <div class="testimonial-card" data-aos="fade-up">
          <div class="stars">${estrellas}</div>
          <blockquote>"${mensaje}"</blockquote>
          <div class="author">
            <div style="width:44px;height:44px;background:var(--gradient);border-radius:50%;display:flex;align-items:center;justify-content:center;color:var(--white);font-weight:700;font-size:1.8rem;flex-shrink:0;">
              ${inicial}
            </div>
            <div>
              <div class="author-name">${nombre}</div>
              <div class="author-role">${rol}</div>
            </div>
          </div>
        </div>`;
    }).join('');

  } catch (err) {
    console.error('Error cargando testimonios:', err);
  }
});