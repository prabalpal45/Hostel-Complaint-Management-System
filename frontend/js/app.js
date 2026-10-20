/**
 * Hostel Complaint & Maintenance Management System
 * App Shell & Global Initialization (app.js)
 * 
 * Sets up sidebar behavior, responsive drawer, topbar profile badges, and global handlers.
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Setup Current User Display in Sidebar & Topbar
  const currentUser = Auth.getCurrentUser();
  if (currentUser) {
    // Fill user name
    document.querySelectorAll('.js-user-name').forEach(el => {
      el.textContent = currentUser.name || currentUser.username;
    });

    // Fill user ID
    document.querySelectorAll('.js-user-id').forEach(el => {
      el.textContent = currentUser.userId || '';
    });

    // Fill user role
    document.querySelectorAll('.js-user-role').forEach(el => {
      el.textContent = currentUser.role || '';
    });

    // Fill user room / department
    document.querySelectorAll('.js-user-extra').forEach(el => {
      if (currentUser.role === 'STUDENT') {
        el.textContent = currentUser.roomNumber ? `Room: ${currentUser.roomNumber}` : '';
      } else if (currentUser.role === 'STAFF') {
        el.textContent = currentUser.department ? `Dept: ${currentUser.department}` : '';
      } else {
        el.textContent = 'Administrator';
      }
    });

    // Fill avatar initials
    document.querySelectorAll('.js-user-avatar').forEach(el => {
      const name = currentUser.name || currentUser.username || 'U';
      const initials = name
        .split(' ')
        .map(n => n[0])
        .join('')
        .substring(0, 2)
        .toUpperCase();
      el.textContent = initials;
    });
  }

  // 2. Setup Topbar Current Date
  const dateElements = document.querySelectorAll('.js-current-date');
  if (dateElements.length > 0) {
    const today = new Date().toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
    dateElements.forEach(el => (el.textContent = today));
  }

  // 3. Highlight Active Navigation Link
  const currentPath = window.location.pathname.replace(/\\/g, '/');
  document.querySelectorAll('.sidebar-nav .nav-link').forEach(link => {
    const href = link.getAttribute('href');
    if (href && currentPath.endsWith(href)) {
      link.classList.add('active');
    }
  });

  // 4. Setup Mobile Sidebar Hamburger & Backdrop
  const hamburgerBtn = document.querySelector('.hamburger-btn');
  const sidebar = document.querySelector('.sidebar');
  let backdrop = document.querySelector('.sidebar-backdrop');

  if (hamburgerBtn && sidebar) {
    if (!backdrop) {
      backdrop = document.createElement('div');
      backdrop.className = 'sidebar-backdrop';
      document.body.appendChild(backdrop);
    }

    hamburgerBtn.addEventListener('click', () => {
      sidebar.classList.toggle('open');
      backdrop.classList.toggle('active');
    });

    backdrop.addEventListener('click', () => {
      sidebar.classList.remove('open');
      backdrop.classList.remove('active');
    });
  }

  // 5. Global Logout Handlers
  document.querySelectorAll('[data-action="logout"]').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      if (UI.confirm('Are you sure you want to log out?')) {
        Auth.logout();
      }
    });
  });

  // 6. Global Reset Sample Data Handler
  document.querySelectorAll('[data-action="reset-data"]').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      if (UI.confirm('Reset all complaints, students, and staff data back to default demo state?')) {
        DataStore.resetToDefaults();
        UI.showToast('Data reset to default demo values!', 'success');
        setTimeout(() => window.location.reload(), 800);
      }
    });
  });
});
