document.addEventListener('DOMContentLoaded', () => {
  const sidebar   = document.getElementById('sidebar');
  const overlay   = document.getElementById('sidebarOverlay');
  const toggleBtn = document.getElementById('sidebarToggle');
  const gestionNav = document.querySelector('.sidebar-nav .sidebar-section:nth-of-type(2)');

  if (gestionNav && !gestionNav.querySelector('a[href="/admin/optimizacion.html"]')) {
    const link = document.createElement('a');
    link.href = '/admin/optimizacion.html';
    link.className = 'sidebar-link';

    const icon = document.createElement('span');
    icon.className = 'material-symbols-rounded';
    icon.textContent = 'functions';
    link.append(icon, document.createTextNode(' Optimizar promociones'));

    const testimoniosLink = gestionNav.querySelector('a[href="/admin/testimonios.html"]');
    if (testimoniosLink) {
      testimoniosLink.after(link);
    } else {
      gestionNav.append(link);
    }
  }

  function openSidebar() {
    sidebar.classList.add('open');
    overlay.classList.add('show');
    document.body.style.overflow = 'hidden';
  }
  function closeSidebar() {
    sidebar.classList.remove('open');
    overlay.classList.remove('show');
    document.body.style.overflow = '';
  }

  if (toggleBtn) toggleBtn.addEventListener('click', () => {
    sidebar.classList.contains('open') ? closeSidebar() : openSidebar();
  });
  if (overlay) overlay.addEventListener('click', closeSidebar);
});