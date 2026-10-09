document.addEventListener('DOMContentLoaded', async () => {
    const form = document.getElementById('formContacto');
    const mensajeEstado = document.getElementById('mensajeContacto');
    const propiedadSelect = document.getElementById('propiedad');
    const botonEnviar = form.querySelector('[type="submit"]');
    const propiedadId = new URLSearchParams(window.location.search).get('propiedadId');
    let consultaBloqueada = false;

    function mostrarEstado(mensaje, color) {
        mensajeEstado.textContent = mensaje;
        mensajeEstado.style.color = color;
        mensajeEstado.setAttribute('role', 'status');
    }

    try {
        const respuesta = await fetch('/api/propiedades/visibles');
        if (!respuesta.ok) throw new Error('No se pudieron cargar las propiedades');
        const propiedades = await respuesta.json();
        propiedades
            .filter(propiedad => (propiedad.estadoComercial || '').toUpperCase() !== 'EN_PROCESO')
            .forEach(propiedad => {
                const opcion = document.createElement('option');
                opcion.value = propiedad.id;
                opcion.textContent = propiedad.titulo;
                propiedadSelect.appendChild(opcion);
            });
    } catch (error) {
        console.error('Error cargando propiedades:', error);
    }

    if (propiedadId) {
        try {
            const respuesta = await fetch(`/api/propiedades/${encodeURIComponent(propiedadId)}`);
            if (!respuesta.ok) throw new Error('No se pudo cargar la propiedad');
            const propiedad = await respuesta.json();
            const enProceso = (propiedad.estadoComercial || '').toUpperCase() === 'EN_PROCESO';
            let opcion = Array.from(propiedadSelect.options).find(item => item.value === propiedad.id);

            if (!opcion) {
                opcion = new Option(
                    `${propiedad.titulo}${enProceso ? ' (En proceso)' : ''}`,
                    propiedad.id,
                    true,
                    true
                );
                opcion.disabled = enProceso;
                propiedadSelect.add(opcion);
            } else {
                opcion.selected = true;
            }

            if (enProceso) {
                consultaBloqueada = true;
                botonEnviar.disabled = true;
                botonEnviar.setAttribute('aria-disabled', 'true');
                mostrarEstado(
                    `Esta propiedad está en proceso de ${(propiedad.operacion || 'venta').toLowerCase()}. No es posible enviar consultas por ahora.`,
                    '#9A3412'
                );
            }
        } catch (error) {
            console.error('Error cargando propiedad:', error);
        }
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (consultaBloqueada) return;

        const contacto = {
            nombre: form.elements.namedItem('nombre').value,
            correo: form.elements.namedItem('email').value,
            celular: form.elements.namedItem('telefono').value,
            casaInteres: propiedadSelect.value,
            mensaje: form.elements.namedItem('mensaje').value,
            medioContacto: form.elements.namedItem('contacto').value
        };

        botonEnviar.disabled = true;
        try {
            const respuesta = await fetch('/api/contacto', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(contacto)
            });

            if (respuesta.status === 409) {
                consultaBloqueada = true;
                mostrarEstado('Esta propiedad pasó a estar en proceso y ya no recibe consultas.', '#9A3412');
                return;
            }
            if (!respuesta.ok) throw new Error('No se pudo enviar el mensaje');

            mostrarEstado('Mensaje enviado correctamente.', 'green');
            form.reset();
        } catch (error) {
            console.error(error);
            mostrarEstado('Hubo un error al enviar el mensaje. Inténtalo nuevamente.', 'red');
        } finally {
            botonEnviar.disabled = consultaBloqueada;
        }
    });
});

