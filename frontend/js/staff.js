/**
 * Hostel Complaint & Maintenance Management System
 * Staff Portal Logic (staff.js)
 */

const StaffApp = {
  currentUser: null,
  cachedComplaints: [],

  init: function () {
    this.currentUser = Auth.requireAuth('STAFF');
  },

  /**
   * Initialize Staff Dashboard
   */
  initDashboard: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const res = await API.get(`/complaints/staff/${encodeURIComponent(this.currentUser.userId)}`);
      const complaints = (res.success && Array.isArray(res.data)) ? res.data : [];
      this.cachedComplaints = complaints;

      const assigned = complaints.filter(c => c.status === 'ASSIGNED').length;
      const inProgress = complaints.filter(c => c.status === 'IN_PROGRESS').length;
      const resolved = complaints.filter(c => c.status === 'RESOLVED').length;
      const pendingAction = assigned + inProgress;

      const totalEl = document.getElementById('stat-total-assigned');
      const pendingActionEl = document.getElementById('stat-pending-action');
      const inProgressEl = document.getElementById('stat-in-progress');
      const resolvedEl = document.getElementById('stat-resolved');

      if (totalEl) totalEl.textContent = complaints.length;
      if (pendingActionEl) pendingActionEl.textContent = pendingAction;
      if (inProgressEl) inProgressEl.textContent = inProgress;
      if (resolvedEl) resolvedEl.textContent = resolved;

      // Render Recent Active Tasks
      const activeTasks = complaints.filter(c => c.status !== 'RESOLVED').slice(0, 5);
      const tbody = document.getElementById('staff-tasks-tbody');
      if (tbody) {
        if (activeTasks.length === 0) {
          tbody.innerHTML = `
            <tr>
              <td colspan="7" class="empty-state">
                <div class="empty-state-title">No active pending tasks</div>
                <div class="empty-state-text">All your assigned maintenance complaints are currently resolved.</div>
              </td>
            </tr>
          `;
        } else {
          tbody.innerHTML = activeTasks.map(c => `
            <tr>
              <td><strong>${c.complaintId}</strong></td>
              <td>${c.studentId}</td>
              <td>${c.roomNumber}</td>
              <td>${c.category}</td>
              <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
              <td>${ComplaintService.getStatusBadge(c.status)}</td>
              <td>
                <div class="d-flex gap-2">
                  <button class="btn btn-secondary btn-sm" onclick="StaffApp.openManageModal('${c.complaintId}')">
                    Manage
                  </button>
                </div>
              </td>
            </tr>
          `).join('');
        }
      }
    } catch (e) {
      console.error('Error loading staff dashboard', e);
      UI.showToast('Unable to load assigned complaints.', 'danger');
    }
  },

  /**
   * Initialize Staff Assigned Complaints List Page
   */
  initComplaintsPage: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const res = await API.get(`/complaints/staff/${encodeURIComponent(this.currentUser.userId)}`);
      const staffComplaints = (res.success && Array.isArray(res.data)) ? res.data : [];
      this.cachedComplaints = staffComplaints;

      const render = () => {
        const search = document.getElementById('filter-search')?.value || '';
        const status = document.getElementById('filter-status')?.value || 'ALL';
        const priority = document.getElementById('filter-priority')?.value || 'ALL';

        const filtered = ComplaintService.filterComplaints(staffComplaints, {
          search,
          status,
          priority
        });

        const tbody = document.getElementById('staff-complaints-tbody');
        const countEl = document.getElementById('complaints-count');
        if (countEl) countEl.textContent = `Showing ${filtered.length} of ${staffComplaints.length} assigned complaints`;

        if (tbody) {
          if (filtered.length === 0) {
            tbody.innerHTML = `
              <tr>
                <td colspan="8" class="empty-state">
                  <div class="empty-state-title">No assigned complaints found</div>
                  <div class="empty-state-text">No complaints match your selected search or filter criteria.</div>
                </td>
              </tr>
            `;
          } else {
            tbody.innerHTML = filtered.map(c => `
              <tr>
                <td><strong>${c.complaintId}</strong></td>
                <td>${c.studentId}</td>
                <td>${c.roomNumber}</td>
                <td>${c.category}</td>
                <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
                <td>${ComplaintService.getStatusBadge(c.status)}</td>
                <td class="cell-desc" title="${c.remarks || 'No remarks'}">${c.remarks ? c.remarks : '<span class="text-muted">—</span>'}</td>
                <td>
                  <div class="d-flex gap-2">
                    <button class="btn btn-primary btn-sm" onclick="StaffApp.openManageModal('${c.complaintId}')">
                      Update / Remarks
                    </button>
                  </div>
                </td>
              </tr>
            `).join('');
          }
        }
      };

      // Attach filters
      ['filter-search', 'filter-status', 'filter-priority'].forEach(id => {
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
          render();
        });
      }

      render();

    } catch (e) {
      console.error('Error loading staff complaints', e);
      UI.showToast('Unable to load assigned complaints.', 'danger');
    }
  },

  /**
   * Open Complaint Manage Modal (Status update + Remarks)
   */
  openManageModal: async function (complaintId) {
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

    const modalBody = document.getElementById('manage-modal-body');
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
          <div class="detail-label">Current Status</div>
          <div class="detail-value">${ComplaintService.getStatusBadge(c.status)}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Room Number</div>
          <div class="detail-value">${c.roomNumber}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Category / Priority</div>
          <div class="detail-value">${c.category} · ${ComplaintService.getPriorityBadge(c.priority)}</div>
        </div>
        <div class="detail-item-full">
          <div class="detail-label">Issue Description</div>
          <div class="detail-value" style="font-weight: normal; background: #f8fafc; padding: 0.6rem 0.85rem; border-radius: 6px; border: 1px solid #e2e8f0;">
            ${c.description}
          </div>
        </div>
      </div>

      <hr style="margin: 1.25rem 0; border: none; border-top: 1px solid #e2e8f0;" />

      <form id="form-manage-complaint" onsubmit="event.preventDefault(); StaffApp.saveComplaintAction('${c.complaintId}');">
        <div class="form-group">
          <label class="form-label" for="update-status-select">Update Work Status</label>
          <select id="update-status-select" class="form-control">
            <option value="ASSIGNED" ${c.status === 'ASSIGNED' ? 'selected' : ''}>ASSIGNED</option>
            <option value="IN_PROGRESS" ${c.status === 'IN_PROGRESS' ? 'selected' : ''}>IN_PROGRESS</option>
            <option value="RESOLVED" ${c.status === 'RESOLVED' ? 'selected' : ''}>RESOLVED</option>
          </select>
          <div class="form-hint">Change status as you begin work or complete the repair.</div>
        </div>

        <div class="form-group">
          <label class="form-label" for="update-remarks-input">Maintenance Staff Remarks</label>
          <textarea id="update-remarks-input" class="form-control" rows="3" placeholder="Enter repair notes, parts replaced, or progress update...">${c.remarks || ''}</textarea>
        </div>

        <div class="d-flex justify-between align-center mt-4">
          <button type="button" class="btn btn-secondary" data-close-modal>Cancel</button>
          <button type="submit" class="btn btn-primary" id="btn-save-staff-action">
            Save Status & Remarks
          </button>
        </div>
      </form>
    `;

    UI.openModal('staff-manage-modal');
  },

  /**
   * Save status & remarks from modal
   */
  saveComplaintAction: async function (complaintId) {
    const statusSelect = document.getElementById('update-status-select');
    const remarksInput = document.getElementById('update-remarks-input');

    if (!statusSelect || !remarksInput) return;

    const newStatus = statusSelect.value;
    const newRemarks = remarksInput.value.trim();

    const saveBtn = document.getElementById('btn-save-staff-action');
    if (saveBtn) saveBtn.classList.add('is-loading');

    try {
      // 1. Update status
      const statusRes = await ComplaintService.updateStatus(complaintId, newStatus);
      if (!statusRes.success) {
        if (saveBtn) saveBtn.classList.remove('is-loading');
        UI.showToast(statusRes.message, 'danger');
        return;
      }

      // 2. Add remarks if provided
      if (newRemarks) {
        const remarksRes = await ComplaintService.addRemarks(complaintId, newRemarks);
        if (!remarksRes.success) {
          console.warn('Remark save warning', remarksRes.message);
        }
      }

      if (saveBtn) saveBtn.classList.remove('is-loading');
      UI.closeModal('staff-manage-modal');
      UI.showToast(`Complaint ${complaintId} updated successfully!`, 'success');

      // Refresh current page view
      if (document.getElementById('staff-tasks-tbody')) {
        StaffApp.initDashboard();
      } else if (document.getElementById('staff-complaints-tbody')) {
        StaffApp.initComplaintsPage();
      }

    } catch (err) {
      if (saveBtn) saveBtn.classList.remove('is-loading');
      UI.showToast('Failed to update complaint.', 'danger');
    }
  },

  /**
   * Initialize Staff Profile Page
   */
  initProfile: async function () {
    this.init();
    if (!this.currentUser) return;

    const user = this.currentUser;

    const pidEl = document.getElementById('profile-id');
    const pnameEl = document.getElementById('profile-name');
    const puserEl = document.getElementById('profile-username');
    const pdeptEl = document.getElementById('profile-dept');
    const pemailEl = document.getElementById('profile-email');
    const pphoneEl = document.getElementById('profile-phone');

    if (pidEl) pidEl.textContent = user.userId;
    if (pnameEl) pnameEl.textContent = user.name;
    if (puserEl) puserEl.textContent = user.username;
    if (pdeptEl) pdeptEl.textContent = user.department || 'General Maintenance';
    if (pemailEl) pemailEl.textContent = user.email || `${user.username}@hostel.edu`;
    if (pphoneEl) pphoneEl.textContent = user.phone || 'N/A';

    try {
      const res = await API.get(`/complaints/staff/${encodeURIComponent(user.userId)}`);
      const complaints = (res.success && Array.isArray(res.data)) ? res.data : [];

      const totalEl = document.getElementById('profile-assigned-total');
      const resolvedEl = document.getElementById('profile-resolved-total');
      const activeEl = document.getElementById('profile-active-total');

      if (totalEl) totalEl.textContent = complaints.length;
      if (resolvedEl) resolvedEl.textContent = complaints.filter(c => c.status === 'RESOLVED').length;
      if (activeEl) activeEl.textContent = complaints.filter(c => c.status !== 'RESOLVED').length;
    } catch (e) {
      console.warn('Could not load staff complaint profile stats', e);
    }
  }
};
