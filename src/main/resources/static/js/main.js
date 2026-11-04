document.addEventListener('DOMContentLoaded', function () {

  const formDatosContacto = document.getElementById('formContacto');
  if (formDatosContacto) {
    fetch('/api/cuenta/perfil', { credentials: 'same-origin' })
      .then(async response => {
        if (response.status === 401) return null;
        if (!response.ok) throw new Error('No se pudo cargar el perfil de la cuenta.');
        return response.json();
      })
      .then(perfil => {
        if (!perfil) return;
        const nombre = formDatosContacto.querySelector('#nombre');
        const correo = formDatosContacto.querySelector('#email');
        const celular = formDatosContacto.querySelector('#telefono');
        if (nombre && !nombre.value) nombre.value = perfil.nombre || '';
        if (correo && !correo.value) correo.value = perfil.email || '';
        if (celular && !celular.value) celular.value = perfil.celular || '';
      })
      .catch(error => console.error('Error al autocompletar el contacto:', error));
  }

  // ─── CARGAR PROPIEDADES EN SELECT (formulario de contacto) ────────────────
    const selectPropiedad = document.getElementById('propiedad');
  if (selectPropiedad) {
    const paramsUrl = new URLSearchParams(window.location.search);
    const propiedadIdUrl = paramsUrl.get('propiedadId');

    fetch('http://localhost:8080/api/propiedades/visibles')
      .then(r => r.json())
      .then(data => {
        data.forEach(p => {
          const opt = document.createElement('option');
          opt.value = p.id;
          opt.textContent = p.titulo;
          selectPropiedad.appendChild(opt);
        });

        // Preseleccionar la propiedad si viene en la URL (?propiedadId=...)
        if (propiedadIdUrl) {
          selectPropiedad.value = propiedadIdUrl;
        }
      })
      .catch(err => console.error('Error al cargar propiedades para select:', err));
  }

  // ─── FORMULARIO DE CONTACTO ───────────────────────────────────────────────
  const formContacto = document.getElementById('formContacto');
  if (formContacto) {
    formContacto.addEventListener('submit', async function (e) {
      e.preventDefault();

      const submitBtn = formContacto.querySelector('button[type="submit"]');
      const textoOriginal = submitBtn.innerHTML;
      submitBtn.innerHTML = '<span class="material-symbols-rounded" style="animation:spin .7s linear infinite;">refresh</span> Enviando...';
      submitBtn.disabled = true;

      const mensajeDiv = document.getElementById('mensajeContacto');

      const datos = {
        nombre:        document.getElementById('nombre')?.value,
        correo:        document.getElementById('email')?.value,
        celular:       document.getElementById('telefono')?.value,
        casaInteres:   document.getElementById('propiedad')?.value,
        mensaje:       document.getElementById('mensaje')?.value,
        medioContacto: document.querySelector('input[name="contacto"]:checked')?.value
      };

      try {
        const res = await fetch('http://localhost:8080/api/contacto', {
          method:  'POST',
          headers: { 'Content-Type': 'application/json' },
          body:    JSON.stringify(datos)
        });

        if (!res.ok) throw new Error('Error al enviar');

        if (mensajeDiv) {
          mensajeDiv.textContent   = '✓ ¡Mensaje enviado! Te contactaremos a la brevedad.';
          mensajeDiv.className     = 'form-message exito';
          mensajeDiv.style.display = 'block';
        }
        formContacto.reset();

      } catch (err) {
        console.error('Error enviando contacto:', err);
        if (mensajeDiv) {
          mensajeDiv.textContent   = 'Error al enviar el formulario. Intenta de nuevo.';
          mensajeDiv.className     = 'form-message error';
          mensajeDiv.style.display = 'block';
        }
      } finally {
        submitBtn.innerHTML = textoOriginal;
        submitBtn.disabled  = false;
      }
    });
  }

});
