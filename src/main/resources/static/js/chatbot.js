(() => {
  if (window.__belarChatbotInicializado) return;
  window.__belarChatbotInicializado = true;

  const MAX_CHARS = 500;
  const MAX_HISTORY_UI = 40;
  const STORAGE_KEY = 'belar_chatbot_historial_v1';

  const ROLE_INFO = {
    VISITANTE: {
      badge: 'Visitante',
      saludo: '¡Hola! Soy el asistente de Belar Inmobiliaria. Pregúntame sobre nuestras propiedades disponibles, precios o cómo contactar a un asesor.'
    },
    CLIENTE: {
      badge: 'Cliente',
      saludo: '¡Hola! Soy el asistente de Belar Inmobiliaria. Puedo ayudarte a consultar propiedades visibles y el estado de tus solicitudes de visita.'
    },
    PROPIETARIO: {
      badge: 'Propietario',
      saludo: '¡Hola! Puedo darte un resumen de tus propiedades publicadas, las consultas recibidas y las solicitudes de visita.'
    },
    ADMIN: {
      badge: 'Administrador',
      saludo: '¡Hola! Puedo mostrarte conteos y resúmenes generales de propiedades, contactos, visitas, testimonios y cuentas por rol.'
    }
  };

  function escapeHtml(value) {
    if (typeof window.escapeHtmlForHtml === 'function') {
      return window.escapeHtmlForHtml(value);
    }
    return String(value ?? '').replace(/[&<>"']/g, character => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    })[character]);
  }

  function renderSafeMessageHtml(rawText) {
    const escaped = escapeHtml(rawText);
    const routePattern = /(\/(?:admin|cliente|propietario)?\/?[a-zA-Z0-9_-]+\.html(?:\?id=[a-zA-Z0-9_-]+)?)/g;
    return escaped.replace(routePattern, route => {
      try {
        const parsed = new URL(route, window.location.origin);
        if (parsed.origin !== window.location.origin) {
          return route;
        }
        const safeHref = escapeHtml(parsed.pathname + parsed.search);
        return `<a href="${safeHref}">${route}</a>`;
      } catch (e) {
        return route;
      }
    });
  }

  function loadSessionState() {
    try {
      const raw = window.sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.messages)) {
        return null;
      }
      return {
        rol: typeof parsed.rol === 'string' ? parsed.rol : 'VISITANTE',
        messages: parsed.messages.slice(-MAX_HISTORY_UI)
      };
    } catch (error) {
      return null;
    }
  }

  function saveSessionState(rol, messages) {
    try {
      window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
        rol,
        messages: messages.slice(-MAX_HISTORY_UI)
      }));
    } catch (error) {
      // Ignora restricciones de almacenamiento de sesión
    }
  }

  function initChatbot() {
    if (!document.body) return;

    let currentRole = 'VISITANTE';
    let disponible = true;
    let isSending = false;
    let messages = [];

    const widget = document.createElement('div');
    widget.className = 'chatbot-widget';
    widget.innerHTML = `
      <section id="belarChatbotPanel" class="chatbot-panel" role="dialog" aria-label="Asistente virtual de Belar Inmobiliaria" hidden>
        <header class="chatbot-header">
          <div class="chatbot-header-info">
            <span class="chatbot-avatar" aria-hidden="true">
              <span class="material-symbols-rounded">smart_toy</span>
            </span>
            <div class="chatbot-title-wrap">
              <div class="chatbot-title">Asistente Belar</div>
              <span id="belarChatbotRole" class="chatbot-role-badge">Visitante</span>
            </div>
          </div>
          <div class="chatbot-header-actions">
            <button type="button" id="belarChatbotClear" class="chatbot-icon-btn" title="Limpiar conversación" aria-label="Limpiar conversación">
              <span class="material-symbols-rounded" aria-hidden="true">delete_sweep</span>
            </button>
            <button type="button" id="belarChatbotClose" class="chatbot-icon-btn" title="Cerrar asistente" aria-label="Cerrar asistente">
              <span class="material-symbols-rounded" aria-hidden="true">close</span>
            </button>
          </div>
        </header>

        <div id="belarChatbotBanner" class="chatbot-status-banner" role="status" hidden>
          <span class="material-symbols-rounded" aria-hidden="true">info</span>
          <span id="belarChatbotBannerText"></span>
        </div>

        <div id="belarChatbotMessages" class="chatbot-messages" aria-live="polite"></div>

        <div id="belarChatbotTyping" class="chatbot-typing" aria-live="polite" hidden>
          <span>Escribiendo</span>
          <span class="chatbot-typing-dots" aria-hidden="true">
            <span></span><span></span><span></span>
          </span>
        </div>

        <form id="belarChatbotForm" class="chatbot-form" novalidate>
          <div class="chatbot-input-row">
            <textarea
              id="belarChatbotInput"
              class="chatbot-input"
              rows="1"
              maxlength="${MAX_CHARS}"
              placeholder="Escribe tu consulta..."
              aria-label="Mensaje para el asistente"
              required></textarea>
            <button type="submit" id="belarChatbotSend" class="chatbot-send-btn" aria-label="Enviar mensaje">
              <span class="material-symbols-rounded" aria-hidden="true">send</span>
            </button>
          </div>
          <div class="chatbot-meta-row">
            <span>Solo consultas de lectura</span>
            <span id="belarChatbotCounter" class="chatbot-char-count">0/${MAX_CHARS}</span>
          </div>
        </form>
      </section>

      <button
        type="button"
        id="belarChatbotFab"
        class="chatbot-fab"
        aria-expanded="false"
        aria-controls="belarChatbotPanel"
        aria-label="Abrir asistente virtual">
        <span class="material-symbols-rounded" aria-hidden="true">chat</span>
      </button>
    `;

    document.body.appendChild(widget);

    const fab = document.getElementById('belarChatbotFab');
    const panel = document.getElementById('belarChatbotPanel');
    const closeBtn = document.getElementById('belarChatbotClose');
    const clearBtn = document.getElementById('belarChatbotClear');
    const roleBadge = document.getElementById('belarChatbotRole');
    const banner = document.getElementById('belarChatbotBanner');
    const bannerText = document.getElementById('belarChatbotBannerText');
    const messagesContainer = document.getElementById('belarChatbotMessages');
    const typingEl = document.getElementById('belarChatbotTyping');
    const form = document.getElementById('belarChatbotForm');
    const input = document.getElementById('belarChatbotInput');
    const sendBtn = document.getElementById('belarChatbotSend');
    const counter = document.getElementById('belarChatbotCounter');

    function updateRoleUI(rol) {
      const info = ROLE_INFO[rol] || ROLE_INFO.VISITANTE;
      roleBadge.textContent = info.badge;
    }

    function updateAvailabilityUI(isAvailable) {
      disponible = Boolean(isAvailable);
      if (!disponible) {
        bannerText.textContent = 'El asistente virtual está desactivado temporalmente.';
        banner.hidden = false;
      } else {
        banner.hidden = true;
      }
    }

    function appendMessageElement(entry) {
      const item = document.createElement('div');
      const tipo = entry.type === 'user'
        ? 'is-user'
        : entry.type === 'error'
          ? 'is-error'
          : 'is-bot';
      item.className = `chatbot-message ${tipo}`;
      item.innerHTML = renderSafeMessageHtml(entry.text);
      messagesContainer.appendChild(item);
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function renderMessages() {
      messagesContainer.replaceChildren();
      messages.forEach(appendMessageElement);
    }

    function addMessage(type, text, persist = true) {
      const entry = { type, text: String(text ?? '') };
      messages.push(entry);
      if (messages.length > MAX_HISTORY_UI) {
        messages = messages.slice(-MAX_HISTORY_UI);
      }
      appendMessageElement(entry);
      if (persist) {
        saveSessionState(currentRole, messages);
      }
    }

    function resetConversationForRole(rol) {
      const info = ROLE_INFO[rol] || ROLE_INFO.VISITANTE;
      messages = [{ type: 'bot', text: info.saludo }];
      saveSessionState(rol, messages);
      renderMessages();
    }

    async function syncStatus() {
      const saved = loadSessionState();
      if (saved && saved.messages.length > 0) {
        currentRole = saved.rol;
        messages = saved.messages;
        updateRoleUI(currentRole);
        renderMessages();
      } else {
        resetConversationForRole(currentRole);
      }

      try {
        const res = await fetch('/api/chatbot/estado', { credentials: 'same-origin' });
        if (res.status === 401) {
          currentRole = 'VISITANTE';
          updateRoleUI(currentRole);
          return;
        }
        if (!res.ok) return;
        const data = await res.json();
        const serverRole = typeof data.rol === 'string' && ROLE_INFO[data.rol] ? data.rol : 'VISITANTE';
        updateAvailabilityUI(data.disponible);
        if (serverRole !== currentRole) {
          currentRole = serverRole;
          updateRoleUI(currentRole);
          resetConversationForRole(currentRole);
        } else {
          updateRoleUI(currentRole);
        }
      } catch (error) {
        // Silencioso si no hay conexión en carga inicial
      }
    }

    function setOpen(open) {
      panel.hidden = !open;
      fab.setAttribute('aria-expanded', String(open));
      fab.setAttribute('aria-label', open ? 'Cerrar asistente virtual' : 'Abrir asistente virtual');
      if (open) {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
        window.setTimeout(() => input.focus(), 40);
      }
    }

    function updateCounter() {
      const len = input.value.length;
      counter.textContent = `${len}/${MAX_CHARS}`;
    }

    fab.addEventListener('click', () => {
      setOpen(panel.hidden);
    });

    closeBtn.addEventListener('click', () => {
      setOpen(false);
    });

    clearBtn.addEventListener('click', () => {
      resetConversationForRole(currentRole);
    });

    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && !panel.hidden) {
        setOpen(false);
      }
    });

    input.addEventListener('input', () => {
      if (input.value.length > MAX_CHARS) {
        input.value = input.value.slice(0, MAX_CHARS);
      }
      updateCounter();
    });

    input.addEventListener('keydown', event => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
      }
    });

    function mapHttpError(status, payloadError) {
      if (status === 401) {
        return 'Tu sesión ha expirado o no es válida. Inicia sesión nuevamente en /ingresar.html.';
      }
      if (status === 429) {
        return payloadError || 'Has enviado demasiadas solicitudes seguidas. Espera un momento antes de volver a intentar.';
      }
      if (status === 503) {
        updateAvailabilityUI(false);
        return payloadError || 'El asistente virtual no está disponible en este momento. Puedes contactarnos en /contacto.html.';
      }
      if (status === 400) {
        return payloadError || 'Tu mensaje no es válido. Verifica que tenga entre 1 y 500 caracteres.';
      }
      return payloadError || 'Ocurrió un error al consultar al asistente. Intenta nuevamente.';
    }

    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (isSending) return;

      const mensaje = input.value.trim();
      if (!mensaje) return;
      if (mensaje.length > MAX_CHARS) {
        addMessage('error', `El mensaje no puede superar los ${MAX_CHARS} caracteres.`);
        return;
      }

      addMessage('user', mensaje);
      input.value = '';
      updateCounter();

      isSending = true;
      sendBtn.disabled = true;
      typingEl.hidden = false;
      messagesContainer.scrollTop = messagesContainer.scrollHeight;

      try {
        const response = await fetch('/api/chatbot', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ mensaje })
        });

        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
          addMessage('error', mapHttpError(response.status, data.error));
          return;
        }

        if (data.rol && ROLE_INFO[data.rol] && data.rol !== currentRole) {
          currentRole = data.rol;
          updateRoleUI(currentRole);
        }
        updateAvailabilityUI(data.disponible !== false);
        addMessage('bot', data.respuesta || 'No obtuve una respuesta en este momento.');
      } catch (error) {
        addMessage('error', 'No se pudo conectar con el servidor. Verifica tu conexión e intenta de nuevo.');
      } finally {
        isSending = false;
        sendBtn.disabled = false;
        typingEl.hidden = true;
        input.focus();
      }
    });

    updateCounter();
    syncStatus();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initChatbot, { once: true });
  } else {
    initChatbot();
  }
})();

