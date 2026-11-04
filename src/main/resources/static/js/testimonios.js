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
      const estrellas = '★'.repeat(t.estrellas || 5) + '☆'.repeat(5 - (t.estrellas || 5));
      const inicial = (t.nombre || '?').charAt(0).toUpperCase();
      return `
        <div class="testimonial-card" data-aos="fade-up">
          <div class="stars">${estrellas}</div>
          <blockquote>"${t.mensaje}"</blockquote>
          <div class="author">
            <div style="width:44px;height:44px;background:var(--gradient);border-radius:50%;display:flex;align-items:center;justify-content:center;color:var(--white);font-weight:700;font-size:1.8rem;flex-shrink:0;">
              ${inicial}
            </div>
            <div>
              <div class="author-name">${t.nombre}</div>
              <div class="author-role">${t.rol || ''}</div>
            </div>
          </div>
        </div>`;
    }).join('');

  } catch (err) {
    console.error('Error cargando testimonios:', err);
  }
});