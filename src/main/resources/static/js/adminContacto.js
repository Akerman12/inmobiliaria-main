document.addEventListener("DOMContentLoaded", async function () {

    const contenedor = document.getElementById("contenedor-contactos");
    const escaparHtml = valor => String(valor ?? '').replace(/[&<>"']/g, caracter => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[caracter]);

    try {

        //  Traer contactos
        const resContactos = await fetch("/api/contacto");
        const contactos = await resContactos.json();

        //  Traer propiedades
        const resPropiedades = await fetch("/api/propiedades");
        const propiedades = await resPropiedades.json();

        //  Crear mapa ID → Título
        const mapaPropiedades = Object.create(null);
        propiedades.forEach(p => {
            mapaPropiedades[p.id] = p.titulo;
        });

       
        contactos.reverse();

        contactos.forEach(c => {

            const nombrePropiedad = mapaPropiedades[c.casaInteres] || "Propiedad no encontrada";

            const card = document.createElement("div");
            card.classList.add("card-contacto");

            card.innerHTML = `
                <h3>${escaparHtml(c.nombre)}</h3>
                <p><strong>Correo:</strong> ${escaparHtml(c.correo)}</p>
                <p><strong>Celular:</strong> ${escaparHtml(c.celular)}</p>
                <p><strong>Casa:</strong> ${escaparHtml(nombrePropiedad)}</p>
                <div class="mensaje">${escaparHtml(c.mensaje)}</div>
            `;

            contenedor.appendChild(card);
        });

    } catch (error) {
        console.error("Error:", error);
    }

});
