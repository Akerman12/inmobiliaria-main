(function () {
  const claveTema = 'belar-apariencia';
  const raiz = document.documentElement;
  let tema = 'light';

  try {
    const temaGuardado = window.localStorage.getItem(claveTema);
    if (temaGuardado === 'dark' || temaGuardado === 'light') tema = temaGuardado;
  } catch (error) {
    console.warn('No se pudo leer la preferencia del tema:', error);
  }

  raiz.dataset.theme = tema;

  function actualizarBoton(boton) {
    const modoOscuroActivo = raiz.dataset.theme === 'dark';
    boton.setAttribute('aria-pressed', String(modoOscuroActivo));
    boton.setAttribute('aria-label', `Cambiar al modo ${modoOscuroActivo ? 'claro' : 'oscuro'}`);
    boton.title = `Cambiar al modo ${modoOscuroActivo ? 'claro' : 'oscuro'}`;
    boton.querySelector('.material-symbols-rounded').textContent =
      modoOscuroActivo ? 'light_mode' : 'dark_mode';
    boton.querySelector('[data-theme-label]').textContent =
      modoOscuroActivo ? 'Modo claro' : 'Modo oscuro';
  }

  function montarTema() {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'theme-toggle';
    boton.innerHTML = '<span class="material-symbols-rounded" aria-hidden="true"></span><span data-theme-label></span>';
    boton.addEventListener('click', () => {
      raiz.dataset.theme = raiz.dataset.theme === 'dark' ? 'light' : 'dark';
      try {
        window.localStorage.setItem(claveTema, raiz.dataset.theme);
      } catch (error) {
        console.warn('No se pudo guardar la preferencia del tema:', error);
      }
      actualizarBoton(boton);
    });
    actualizarBoton(boton);

    const barra = document.querySelector('.navbar-cta');
    const barraLateral = document.querySelector('.cliente-sidebar, .propietario-sidebar');
    const encabezadoAdmin = document.querySelector('.admin-topbar');
    const paginaLogin = document.querySelector('.login-page');
    let barraAdmin = document.querySelector('.admin-topbar .topbar-right');
    if (!barraAdmin && encabezadoAdmin) {
      barraAdmin = document.createElement('div');
      barraAdmin.className = 'topbar-right';
      encabezadoAdmin.appendChild(barraAdmin);
    }
    if (barra) {
      barra.prepend(boton);
    } else if (barraAdmin) {
      barraAdmin.prepend(boton);
    } else if (paginaLogin) {
      paginaLogin.appendChild(boton);
    } else if (barraLateral) {
      const formularioSesion = barraLateral.querySelector('form');
      if (formularioSesion) barraLateral.insertBefore(boton, formularioSesion);
      else barraLateral.appendChild(boton);
    }

    const pie = document.querySelector('.footer-bottom');
    if (pie && !pie.querySelector('.site-legal-links')) {
      const enlacesLegales = document.createElement('nav');
      enlacesLegales.className = 'site-legal-links';
      enlacesLegales.setAttribute('aria-label', 'Información legal');
      enlacesLegales.innerHTML = '<a href="/terminos.html">Términos y condiciones</a><a href="/privacidad.html">Privacidad</a>';
      pie.appendChild(enlacesLegales);
    }

    if (barraLateral) {
      const enlacesLegales = document.createElement('nav');
      enlacesLegales.className = 'portal-legal-links';
      enlacesLegales.setAttribute('aria-label', 'Información legal');
      enlacesLegales.innerHTML = '<a href="/terminos.html">Términos y condiciones</a><a href="/privacidad.html">Privacidad</a>';
      const formularioSesion = barraLateral.querySelector('form');
      if (formularioSesion) barraLateral.insertBefore(enlacesLegales, formularioSesion);
      else barraLateral.appendChild(enlacesLegales);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', montarTema, { once: true });
  } else {
    montarTema();
  }
})();
