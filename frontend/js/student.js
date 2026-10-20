/**
 * Hostel Complaint & Maintenance Management System
 * Student Portal Logic (student.js)
 */

const StudentApp = {
  // Current user cache
  currentUser: null,
  cachedComplaints: [],

  init: function () {
    this.currentUser = Auth.requireAuth('STUDENT');
  },

  /**
   * Initialize Student Dashboard
   */
  initDashboard: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const res = await API.get(`/complaints/student/${encodeURIComponent(this.currentUser.userId)}`);
      const complaints = (res.success && Array.isArray(res.data)) ? res.data : [];
      this.cachedComplaints = complaints;

      // Calculate Statistics
      const total = complaints.length;
      const pending = complaints.filter(c => c.status === 'PENDING').length;
      const inProgress = complaints.filter(c => c.status === 'IN_PROGRESS' || c.status === 'ASSIGNED').length;
      const resolved = complaints.filter(c => c.status === 'RESOLVED').length;

      const totalEl = document.getElementById('stat-total');
      const pendingEl = document.getElementById('stat-pending');
      const progressEl = document.getElementById('stat-progress');
      const resolvedEl = document.getElementById('stat-resolved');

      if (totalEl) totalEl.textContent = total;
      if (pendingEl) pendingEl.textContent = pending;
      if (progressEl) progressEl.textContent = inProgress;
      if (resolvedEl) resolvedEl.textContent = resolved;

      // Render Progress / Status Bars
      this.renderStatusBar('bar-pending', pending, total, 'count-pending');
      this.renderStatusBar('bar-progress', inProgress, total, 'count-progress');
      this.renderStatusBar('bar-resolved', resolved, total, 'count-resolved');

      // Render Recent Complaints (Top 5)
      const recent = complaints.slice(0, 5);
      const tbody = document.getElementById('recent-complaints-tbody');
      if (tbody) {
        if (recent.length === 0) {
          tbody.innerHTML = `
            <tr>
              <td colspan="6" class="empty-state">
                <div class="empty-state-title">No complaints yet</div>
                <div class="empty-state-text">You haven't filed any hostel complaints.</div>
                <a href="create-complaint.html" class="btn btn-primary btn-sm">Create First Complaint</a>
              </td>
            </tr>
          `;
        } else {
          tbody.innerHTML = recent.map(c => `
            <tr>
              <td><strong>${c.complaintId}</strong></td>
              <td>${c.category}</td>
              <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
              <td>${ComplaintService.getStatusBadge(c.status)}</td>
              <td>Room ${c.roomNumber}</td>
              <td>
                <button class="btn btn-secondary btn-sm" onclick="StudentApp.showComplaintDetails('${c.complaintId}')">
                  View
                </button>
              </td>
            </tr>
          `).join('');
        }
      }
    } catch (e) {
      console.error('Error loading student dashboard', e);
      UI.showToast('Unable to load complaints from backend.', 'danger');
    }
  },

  /**
   * Helper to render percentage progress bar
   */
  renderStatusBar: function (barId, value, total, countId) {
    const bar = document.getElementById(barId);
    const count = document.getElementById(countId);
    const pct = total > 0 ? Math.round((value / total) * 100) : 0;
    if (bar) bar.style.width = `${pct}%`;
    if (count) count.textContent = `${value} (${pct}%)`;
  },

  /**
   * Initialize My Complaints Page
   */
  initComplaintsPage: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const res = await API.get(`/complaints/student/${encodeURIComponent(this.currentUser.userId)}`);
      const myComplaints = (res.success && Array.isArray(res.data)) ? res.data : [];
      this.cachedComplaints = myComplaints;

      const render = () => {
        const search = document.getElementById('filter-search')?.value || '';
        const status = document.getElementById('filter-status')?.value || 'ALL';
        const priority = document.getElementById('filter-priority')?.value || 'ALL';
        const category = document.getElementById('filter-category')?.value || 'ALL';

        const filtered = ComplaintService.filterComplaints(myComplaints, {
          search,
          status,
          priority,
          category
        });

        const tbody = document.getElementById('complaints-table-tbody');
        const countEl = document.getElementById('complaints-count');
        if (countEl) countEl.textContent = `Showing ${filtered.length} of ${myComplaints.length} complaints`;

        if (tbody) {
          if (filtered.length === 0) {
            tbody.innerHTML = `
              <tr>
                <td colspan="8" class="empty-state">
                  <div class="empty-state-title">No complaints found</div>
                  <div class="empty-state-text">Try adjusting your filters or search terms.</div>
                </td>
              </tr>
            `;
          } else {
            tbody.innerHTML = filtered.map(c => `
              <tr>
                <td><strong>${c.complaintId}</strong></td>
                <td>${c.category}</td>
                <td>${c.roomNumber}</td>
                <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
                <td>${ComplaintService.getStatusBadge(c.status)}</td>
                <td>${ComplaintService.getStaffInfo(c.assignedStaffId)}</td>
                <td class="cell-desc" title="${c.remarks || 'No remarks yet'}">${c.remarks ? c.remarks : '<span class="text-muted">—</span>'}</td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="StudentApp.showComplaintDetails('${c.complaintId}')">
                    View
                  </button>
                </td>
              </tr>
            `).join('');
          }
        }
      };

      // Attach filter listeners
      ['filter-search', 'filter-status', 'filter-priority', 'filter-category'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
          el.addEventListener('input', render);
          el.addEventListener('change', render);
        }
      });

      const resetBtn = document.getElementById('btn-reset-filters');
      if (resetBtn) {
        resetBtn.addEventListener('click', () => {
          if (document.getElementById('filter-search')) document.getElementById('filter-search').value = '';
          if (document.getElementById('filter-status')) document.getElementById('filter-status').value = 'ALL';
          if (document.getElementById('filter-priority')) document.getElementById('filter-priority').value = 'ALL';
          if (document.getElementById('filter-category')) document.getElementById('filter-category').value = 'ALL';
          render();
        });
      }

      render();

    } catch (e) {
      console.error('Error loading student complaints', e);
      UI.showToast('Unable to load complaints from backend.', 'danger');
    }
  },

  /**
   * Initialize Create Complaint Form
   */
  initCreateComplaint: function () {
    this.init();
    if (!this.currentUser) return;

    // Prefill Room Number from user session
    const roomInput = document.getElementById('complaint-room');
    if (roomInput && this.currentUser.roomNumber) {
      roomInput.value = this.currentUser.roomNumber;
    }

    const form = document.getElementById('create-complaint-form');
    if (form) {
      form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const roomNumber = document.getElementById('complaint-room').value.trim();
        const category = document.getElementById('complaint-category').value;
        const priority = document.getElementById('complaint-priority').value;
        const description = document.getElementById('complaint-description').value.trim();

        if (!roomNumber) {
          UI.showToast('Please enter your room number.', 'warning');
          return;
        }
        if (!category) {
          UI.showToast('Please select a complaint category.', 'warning');
          return;
        }
        if (!priority) {
          UI.showToast('Please select a priority level.', 'warning');
          return;
        }
        if (!description || description.length < 5) {
          UI.showToast('Please describe the problem in at least 5 characters.', 'warning');
          return;
        }

        const submitBtn = form.querySelector('button[type="submit"]');
        if (submitBtn) submitBtn.classList.add('is-loading');

        try {
          const res = await ComplaintService.createComplaint({
            studentId: this.currentUser.userId,
            roomNumber,
            category,
            description,
            priority
          });

          if (submitBtn) submitBtn.classList.remove('is-loading');

          if (res.success) {
            UI.showToast(`Complaint ${res.complaint.complaintId} created successfully!`, 'success');
            setTimeout(() => {
              window.location.href = 'complaints.html';
            }, 800);
          } else {
            UI.showToast(res.message, 'danger');
          }
        } catch (err) {
          if (submitBtn) submitBtn.classList.remove('is-loading');
          UI.showToast('Failed to connect to backend server.', 'danger');
        }
      });
    }

    const clearBtn = document.getElementById('btn-clear-form');
    if (clearBtn && form) {
      clearBtn.addEventListener('click', () => {
        form.reset();
        if (roomInput && this.currentUser.roomNumber) {
          roomInput.value = this.currentUser.roomNumber;
        }
      });
    }
  },

  /**
   * Initialize Student Profile Page
   */
  initProfile: async function () {
    this.init();
    if (!this.currentUser) return;

    const user = this.currentUser;

    const pidEl = document.getElementById('profile-id');
    const pnameEl = document.getElementById('profile-name');
    const puserEl = document.getElementById('profile-username');
    const proomEl = document.getElementById('profile-room');
    const pemailEl = document.getElementById('profile-email');
    const pphoneEl = document.getElementById('profile-phone');

    if (pidEl) pidEl.textContent = user.userId;
    if (pnameEl) pnameEl.textContent = user.name;
    if (puserEl) puserEl.textContent = user.username;
    if (proomEl) proomEl.textContent = user.roomNumber || 'N/A';
    if (pemailEl) pemailEl.textContent = user.email || `${user.username}@hostel.edu`;
    if (pphoneEl) pphoneEl.textContent = user.phone || 'N/A';

    try {
      const res = await API.get(`/complaints/student/${encodeURIComponent(user.userId)}`);
      const complaints = (res.success && Array.isArray(res.data)) ? res.data : [];

      const totalEl = document.getElementById('profile-total-complaints');
      const resolvedEl = document.getElementById('profile-resolved-complaints');
      const pendingEl = document.getElementById('profile-pending-complaints');

      if (totalEl) totalEl.textContent = complaints.length;
      if (resolvedEl) resolvedEl.textContent = complaints.filter(c => c.status === 'RESOLVED').length;
      if (pendingEl) pendingEl.textContent = complaints.filter(c => c.status === 'PENDING').length;
    } catch (e) {
      console.warn('Could not load profile complaint counts', e);
    }
  },

  /**
   * View Complaint Modal with Timeline
   */
  showComplaintDetails: async function (complaintId) {
    let c = this.cachedComplaints.find(item => item.complaintId === complaintId);

    if (!c) {
      const res = await API.get(`/complaints/${encodeURIComponent(complaintId)}`);
      if (res.success) {
        c = res.data;
      }
    }

    if (!c) {
      UI.showToast('Complaint not found.', 'danger');
      return;
    }

    const modalBody = document.getElementById('complaint-detail-body');
    if (!modalBody) return;

    modalBody.innerHTML = `
      <div style="margin-bottom: 1.25rem;">
        ${ComplaintService.renderTimelineHtml(c.status)}
      </div>

      <div class="detail-list">
        <div class="detail-item">
          <div class="detail-label">Complaint ID</div>
          <div class="detail-value text-primary">${c.complaintId}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Status</div>
          <div class="detail-value">${ComplaintService.getStatusBadge(c.status)}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Room Number</div>
          <div class="detail-value">${c.roomNumber}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Category</div>
          <div class="detail-value">${c.category}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Priority</div>
          <div class="detail-value">${ComplaintService.getPriorityBadge(c.priority)}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Assigned Staff</div>
          <div class="detail-value">${ComplaintService.getStaffInfo(c.assignedStaffId)}</div>
        </div>
        <div class="detail-item-full">
          <div class="detail-label">Description</div>
          <div class="detail-value" style="font-weight: normal; background: #f8fafc; padding: 0.75rem; border-radius: 6px; border: 1px solid #e2e8f0;">
            ${c.description}
          </div>
        </div>
        <div class="detail-item-full">
          <div class="detail-label">Staff Remarks</div>
          <div class="detail-value" style="font-weight: normal; background: #f8fafc; padding: 0.75rem; border-radius: 6px; border: 1px solid #e2e8f0;">
            ${c.remarks ? c.remarks : '<em class="text-muted">No remarks added yet.</em>'}
          </div>
        </div>
      </div>
    `;

    UI.openModal('complaint-detail-modal');
  }
};
