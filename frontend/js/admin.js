/**
 * Hostel Complaint & Maintenance Management System
 * Admin Portal Logic (admin.js)
 */

const AdminApp = {
  currentUser: null,
  cachedComplaints: [],
  cachedStaff: [],
  cachedStudents: [],

  init: function () {
    this.currentUser = Auth.requireAuth('ADMIN');
  },

  /**
   * Helper to load staff list into cache
   */
  loadStaffCache: async function () {
    if (this.cachedStaff.length > 0) return this.cachedStaff;
    try {
      const res = await API.get('/users/staff');
      if (res.success && Array.isArray(res.data)) {
        this.cachedStaff = res.data;
        ComplaintService.setStaffCache(this.cachedStaff);
      }
    } catch (e) {
      console.warn('Could not load staff list', e);
    }
    return this.cachedStaff;
  },

  /**
   * Initialize Admin Dashboard
   */
  initDashboard: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      await this.loadStaffCache();

      const [complaintsRes, reportsRes] = await Promise.all([
        API.get('/complaints'),
        API.get('/reports')
      ]);

      const complaints = (complaintsRes.success && Array.isArray(complaintsRes.data)) ? complaintsRes.data : [];
      this.cachedComplaints = complaints;

      let total = complaints.length;
      let pending = complaints.filter(c => c.status === 'PENDING').length;
      let assigned = complaints.filter(c => c.status === 'ASSIGNED').length;
      let inProgress = complaints.filter(c => c.status === 'IN_PROGRESS').length;
      let resolved = complaints.filter(c => c.status === 'RESOLVED').length;

      if (reportsRes.success && reportsRes.data) {
        total = reportsRes.data.total ?? total;
        pending = reportsRes.data.pending ?? pending;
        assigned = reportsRes.data.assigned ?? assigned;
        inProgress = reportsRes.data.inProgress ?? inProgress;
        resolved = reportsRes.data.resolved ?? resolved;
      }

      // Stat cards
      const totalEl = document.getElementById('stat-total-complaints');
      const pendingEl = document.getElementById('stat-pending');
      const assignedEl = document.getElementById('stat-assigned');
      const progressEl = document.getElementById('stat-in-progress');
      const resolvedEl = document.getElementById('stat-resolved');

      if (totalEl) totalEl.textContent = total;
      if (pendingEl) pendingEl.textContent = pending;
      if (assignedEl) assignedEl.textContent = assigned;
      if (progressEl) progressEl.textContent = inProgress;
      if (resolvedEl) resolvedEl.textContent = resolved;

      // Status Distributions
      this.renderStatusBar('bar-status-pending', pending, total, 'label-status-pending');
      this.renderStatusBar('bar-status-assigned', assigned, total, 'label-status-assigned');
      this.renderStatusBar('bar-status-progress', inProgress, total, 'label-status-progress');
      this.renderStatusBar('bar-status-resolved', resolved, total, 'label-status-resolved');

      // Category Distributions
      const catContainer = document.getElementById('category-distribution-container');
      if (catContainer) {
        const categories = ComplaintService.CATEGORIES;
        catContainer.innerHTML = categories.map(cat => {
          const catCount = complaints.filter(c => c.category === cat).length;
          const pct = total > 0 ? Math.round((catCount / total) * 100) : 0;
          return `
            <div class="dist-item">
              <div class="dist-header">
                <span>${cat}</span>
                <span class="text-muted">${catCount} (${pct}%)</span>
              </div>
              <div class="dist-bar-bg">
                <div class="dist-bar-fill" style="width: ${pct}%; background-color: var(--primary);"></div>
              </div>
            </div>
          `;
        }).join('');
      }

      // Recent Complaints Table (Top 6)
      const recent = complaints.slice(0, 6);
      const tbody = document.getElementById('admin-recent-tbody');
      if (tbody) {
        if (recent.length === 0) {
          tbody.innerHTML = `
            <tr>
              <td colspan="7" class="empty-state">
                <div class="empty-state-title">No complaints lodged yet</div>
                <div class="empty-state-text">Complaints submitted by students will appear here.</div>
              </td>
            </tr>
          `;
        } else {
          tbody.innerHTML = recent.map(c => `
            <tr>
              <td><strong>${c.complaintId}</strong></td>
              <td>${c.studentId} (${c.roomNumber})</td>
              <td>${c.category}</td>
              <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
              <td>${ComplaintService.getStatusBadge(c.status)}</td>
              <td>${c.assignedStaffId ? ComplaintService.getStaffInfo(c.assignedStaffId) : '<span class="badge badge-priority-high">Unassigned</span>'}</td>
              <td>
                <div class="d-flex gap-2">
                  <button class="btn btn-secondary btn-sm" onclick="AdminApp.viewComplaint('${c.complaintId}')">
                    View
                  </button>
                  ${c.status === 'PENDING' || !c.assignedStaffId ? `
                    <button class="btn btn-primary btn-sm" onclick="AdminApp.openAssignModal('${c.complaintId}')">
                      Assign
                    </button>
                  ` : ''}
                </div>
              </td>
            </tr>
          `).join('');
        }
      }

    } catch (e) {
      console.error('Error loading admin dashboard', e);
      UI.showToast('Unable to load admin dashboard data.', 'danger');
    }
  },

  renderStatusBar: function (barId, val, total, labelId) {
    const bar = document.getElementById(barId);
    const label = document.getElementById(labelId);
    const pct = total > 0 ? Math.round((val / total) * 100) : 0;
    if (bar) bar.style.width = `${pct}%`;
    if (label) label.textContent = `${val} (${pct}%)`;
  },

  /**
   * Initialize All Complaints Page
   */
  initComplaintsPage: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const [complaintsRes, staffList] = await Promise.all([
        API.get('/complaints'),
        this.loadStaffCache()
      ]);

      const all = (complaintsRes.success && Array.isArray(complaintsRes.data)) ? complaintsRes.data : [];
      this.cachedComplaints = all;

      // Populate staff filter dropdown
      const staffFilter = document.getElementById('filter-staff');
      if (staffFilter) {
        staffFilter.innerHTML = `
          <option value="ALL">All Staff</option>
          <option value="UNASSIGNED">Unassigned Only</option>
          ${staffList.map(s => `<option value="${s.userId}">${s.name} (${s.department || 'Staff'})</option>`).join('')}
        `;
      }

      const render = () => {
        const search = document.getElementById('filter-search')?.value || '';
        const status = document.getElementById('filter-status')?.value || 'ALL';
        const priority = document.getElementById('filter-priority')?.value || 'ALL';
        const category = document.getElementById('filter-category')?.value || 'ALL';
        const staffId = document.getElementById('filter-staff')?.value || 'ALL';

        const filtered = ComplaintService.filterComplaints(all, {
          search,
          status,
          priority,
          category,
          staffId
        });

        const tbody = document.getElementById('admin-complaints-tbody');
        const countEl = document.getElementById('complaints-count');
        if (countEl) countEl.textContent = `Showing ${filtered.length} of ${all.length} complaints`;

        if (tbody) {
          if (filtered.length === 0) {
            tbody.innerHTML = `
              <tr>
                <td colspan="8" class="empty-state">
                  <div class="empty-state-title">No complaints match filters</div>
                  <div class="empty-state-text">Try resetting or broadening your search criteria.</div>
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
                <td>${ComplaintService.getStaffInfo(c.assignedStaffId)}</td>
                <td>
                  <div class="d-flex gap-2">
                    <button class="btn btn-secondary btn-sm" onclick="AdminApp.viewComplaint('${c.complaintId}')">
                      View
                    </button>
                    <button class="btn btn-primary btn-sm" onclick="AdminApp.openAssignModal('${c.complaintId}')">
                      Assign
                    </button>
                  </div>
                </td>
              </tr>
            `).join('');
          }
        }
      };

      ['filter-search', 'filter-status', 'filter-priority', 'filter-category', 'filter-staff'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
          el.addEventListener('input', render);
          el.addEventListener('change', render);
        }
      });

      const resetBtn = document.getElementById('btn-reset-filters');
      if (resetBtn) {
        resetBtn.addEventListener('click', () => {
          ['filter-search', 'filter-status', 'filter-priority', 'filter-category', 'filter-staff'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = 'ALL';
          });
          if (document.getElementById('filter-search')) document.getElementById('filter-search').value = '';
          render();
        });
      }

      render();

    } catch (e) {
      console.error('Error loading admin complaints page', e);
      UI.showToast('Unable to load complaints list.', 'danger');
    }
  },

  /**
   * View Complaint Modal
   */
  viewComplaint: async function (complaintId) {
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

    const modalBody = document.getElementById('admin-view-modal-body');
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
          <div class="detail-label">Student ID & Room</div>
          <div class="detail-value">${c.studentId} · Room ${c.roomNumber}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Category & Priority</div>
          <div class="detail-value">${c.category} · ${ComplaintService.getPriorityBadge(c.priority)}</div>
        </div>
        <div class="detail-item-full">
          <div class="detail-label">Assigned Staff</div>
          <div class="detail-value">${ComplaintService.getStaffInfo(c.assignedStaffId)}</div>
        </div>
        <div class="detail-item-full">
          <div class="detail-label">Problem Description</div>
          <div class="detail-value" style="font-weight: normal; background: #f8fafc; padding: 0.65rem 0.85rem; border-radius: 6px; border: 1px solid #e2e8f0;">
            ${c.description}
          </div>
        </div>
        <div class="detail-item-full">
          <div class="detail-label">Maintenance Remarks</div>
          <div class="detail-value" style="font-weight: normal; background: #f8fafc; padding: 0.65rem 0.85rem; border-radius: 6px; border: 1px solid #e2e8f0;">
            ${c.remarks ? c.remarks : '<em class="text-muted">No remarks entered yet.</em>'}
          </div>
        </div>
      </div>

      <div class="d-flex justify-between align-center mt-4">
        <button type="button" class="btn btn-secondary" data-close-modal>Close</button>
        <button type="button" class="btn btn-primary" onclick="UI.closeModal('admin-view-modal'); AdminApp.openAssignModal('${c.complaintId}');">
          Reassign Staff
        </button>
      </div>
    `;

    UI.openModal('admin-view-modal');
  },

  /**
   * Open Assign Staff Modal
   */
  openAssignModal: async function (complaintId) {
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

    const modalBody = document.getElementById('admin-assign-modal-body');
    if (!modalBody) return;

    const staffList = await this.loadStaffCache();

    modalBody.innerHTML = `
      <div class="detail-list" style="margin-bottom: 1rem;">
        <div class="detail-item">
          <div class="detail-label">Complaint ID</div>
          <div class="detail-value text-primary">${c.complaintId}</div>
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
        <div class="detail-item-full">
          <div class="detail-label">Description</div>
          <div class="detail-value" style="font-weight: normal; background: #f8fafc; padding: 0.5rem 0.75rem; border-radius: 6px; border: 1px solid #e2e8f0; font-size: 0.85rem;">
            ${c.description}
          </div>
        </div>
      </div>

      <form id="form-assign-staff" onsubmit="event.preventDefault(); AdminApp.executeAssignment('${c.complaintId}');">
        <div class="form-group">
          <label class="form-label" for="assign-staff-select">Select Maintenance Staff <span class="required">*</span></label>
          <select id="assign-staff-select" class="form-control" required>
            <option value="">-- Choose Staff Member --</option>
            ${staffList.map(s => `
              <option value="${s.userId}" ${c.assignedStaffId === s.userId ? 'selected' : ''}>
                ${s.userId} - ${s.name} (${s.department || 'Maintenance'} Dept)
              </option>
            `).join('')}
          </select>
          <div class="form-hint">Staff will receive assignment to inspect and resolve this complaint.</div>
        </div>

        <div class="d-flex justify-between align-center mt-4">
          <button type="button" class="btn btn-secondary" data-close-modal>Cancel</button>
          <button type="submit" class="btn btn-primary" id="btn-submit-assignment">
            Assign Complaint
          </button>
        </div>
      </form>
    `;

    UI.openModal('admin-assign-modal');
  },

  /**
   * Execute Assign Action
   */
  executeAssignment: async function (complaintId) {
    const select = document.getElementById('assign-staff-select');
    if (!select || !select.value) {
      UI.showToast('Please select a staff member.', 'warning');
      return;
    }

    const staffId = select.value;
    const btn = document.getElementById('btn-submit-assignment');
    if (btn) btn.classList.add('is-loading');

    try {
      const res = await ComplaintService.assignStaff(complaintId, staffId);
      if (btn) btn.classList.remove('is-loading');

      if (res.success) {
        UI.closeModal('admin-assign-modal');
        UI.showToast(`Complaint ${complaintId} assigned to ${staffId} successfully!`, 'success');

        // Re-render whichever view is active
        if (document.getElementById('admin-complaints-tbody')) {
          this.initComplaintsPage();
        } else if (document.getElementById('admin-recent-tbody')) {
          this.initDashboard();
        } else if (document.getElementById('assign-page-tbody')) {
          this.initAssignPage();
        }
      } else {
        UI.showToast(res.message, 'danger');
      }
    } catch (e) {
      if (btn) btn.classList.remove('is-loading');
      UI.showToast('Failed to assign complaint.', 'danger');
    }
  },

  /**
   * Initialize Dedicated Assign Complaints Page (admin/assign.html)
   */
  initAssignPage: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const [complaintsRes, staffList] = await Promise.all([
        API.get('/complaints'),
        this.loadStaffCache()
      ]);

      const all = (complaintsRes.success && Array.isArray(complaintsRes.data)) ? complaintsRes.data : [];
      this.cachedComplaints = all;

      const render = () => {
        const unassigned = all.filter(c => c.status === 'PENDING' || !c.assignedStaffId);
        const tbody = document.getElementById('assign-page-tbody');
        const countEl = document.getElementById('unassigned-count');
        if (countEl) countEl.textContent = unassigned.length;

        if (tbody) {
          if (unassigned.length === 0) {
            tbody.innerHTML = `
              <tr>
                <td colspan="7" class="empty-state">
                  <div class="empty-state-title">All complaints are assigned!</div>
                  <div class="empty-state-text">There are currently no unassigned or pending complaints waiting for staff.</div>
                </td>
              </tr>
            `;
          } else {
            tbody.innerHTML = unassigned.map(c => `
              <tr>
                <td><strong>${c.complaintId}</strong></td>
                <td>${c.studentId}</td>
                <td>${c.roomNumber}</td>
                <td>${c.category}</td>
                <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
                <td class="cell-desc" title="${c.description}">${c.description}</td>
                <td>
                  <div class="d-flex gap-2">
                    <select id="quick-assign-${c.complaintId}" class="form-control" style="width: auto; padding: 0.35rem 0.5rem; font-size: 0.8rem;">
                      <option value="">-- Choose Staff --</option>
                      ${staffList.map(s => `<option value="${s.userId}">${s.name} (${s.department || 'Staff'})</option>`).join('')}
                    </select>
                    <button class="btn btn-primary btn-sm" onclick="AdminApp.handleQuickAssign('${c.complaintId}')">
                      Assign
                    </button>
                  </div>
                </td>
              </tr>
            `).join('');
          }
        }
      };

      render();

    } catch (e) {
      console.error('Error loading assign page', e);
      UI.showToast('Unable to load unassigned complaints.', 'danger');
    }
  },

  handleQuickAssign: async function (complaintId) {
    const select = document.getElementById(`quick-assign-${complaintId}`);
    if (!select || !select.value) {
      UI.showToast('Please pick a staff member from the dropdown.', 'warning');
      return;
    }
    const staffId = select.value;
    const res = await ComplaintService.assignStaff(complaintId, staffId);
    if (res.success) {
      UI.showToast(`Complaint ${complaintId} assigned to ${staffId}!`, 'success');
      this.initAssignPage();
    } else {
      UI.showToast(res.message, 'danger');
    }
  },

  /**
   * Initialize Students Management Page (admin/students.html)
   */
  initStudentsPage: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const [studentsRes, complaintsRes] = await Promise.all([
        API.get('/users/students'),
        API.get('/complaints')
      ]);

      const students = (studentsRes.success && Array.isArray(studentsRes.data)) ? studentsRes.data : [];
      const complaints = (complaintsRes.success && Array.isArray(complaintsRes.data)) ? complaintsRes.data : [];
      this.cachedStudents = students;
      this.cachedComplaints = complaints;

      const render = () => {
        const search = document.getElementById('search-student')?.value.trim().toLowerCase() || '';

        const filtered = students.filter(s => {
          if (!search) return true;
          return (
            s.userId.toLowerCase().includes(search) ||
            s.name.toLowerCase().includes(search) ||
            s.username.toLowerCase().includes(search) ||
            (s.roomNumber && s.roomNumber.toLowerCase().includes(search))
          );
        });

        const tbody = document.getElementById('students-table-tbody');
        const countEl = document.getElementById('students-count');
        if (countEl) countEl.textContent = `Total Students: ${filtered.length}`;

        if (tbody) {
          if (filtered.length === 0) {
            tbody.innerHTML = `
              <tr>
                <td colspan="6" class="empty-state">
                  <div class="empty-state-title">No students found</div>
                  <div class="empty-state-text">No registered students match "${search}".</div>
                </td>
              </tr>
            `;
          } else {
            tbody.innerHTML = filtered.map(s => {
              const studentComplaints = complaints.filter(c => c.studentId === s.userId);
              return `
                <tr>
                  <td><strong>${s.userId}</strong></td>
                  <td>${s.name}</td>
                  <td><code>${s.username}</code></td>
                  <td>${s.roomNumber || '—'}</td>
                  <td><span class="badge ${studentComplaints.length > 0 ? 'badge-status-assigned' : 'badge-priority-low'}">${studentComplaints.length} Filed</span></td>
                  <td>
                    <button class="btn btn-secondary btn-sm" onclick="AdminApp.viewStudentDetails('${s.userId}')">
                      View
                    </button>
                  </td>
                </tr>
              `;
            }).join('');
          }
        }
      };

      const searchInput = document.getElementById('search-student');
      if (searchInput) searchInput.addEventListener('input', render);

      render();

    } catch (e) {
      console.error('Error loading students page', e);
      UI.showToast('Unable to load students list.', 'danger');
    }
  },

  viewStudentDetails: function (studentId) {
    const student = this.cachedStudents.find(s => s.userId === studentId);
    if (!student) return;

    const complaints = this.cachedComplaints.filter(c => c.studentId === studentId);
    const modalBody = document.getElementById('student-modal-body');
    if (!modalBody) return;

    modalBody.innerHTML = `
      <div class="detail-list">
        <div class="detail-item">
          <div class="detail-label">Student ID</div>
          <div class="detail-value text-primary">${student.userId}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Full Name</div>
          <div class="detail-value">${student.name}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Username</div>
          <div class="detail-value">${student.username}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Room Number</div>
          <div class="detail-value">${student.roomNumber || 'N/A'}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Role</div>
          <div class="detail-value">${student.role}</div>
        </div>
      </div>

      <h4 style="margin: 1.25rem 0 0.5rem; font-size: 0.95rem; font-weight: 700;">Complaint History (${complaints.length})</h4>
      ${complaints.length === 0 ? '<p class="text-muted" style="font-size: 0.85rem;">No complaints filed by this student.</p>' : `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Category</th>
                <th>Priority</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              ${complaints.map(c => `
                <tr>
                  <td><strong>${c.complaintId}</strong></td>
                  <td>${c.category}</td>
                  <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
                  <td>${ComplaintService.getStatusBadge(c.status)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `}
    `;

    UI.openModal('student-detail-modal');
  },

  /**
   * Initialize Staff Management Page (admin/staff.html)
   */
  initStaffPage: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const [staffList, complaintsRes] = await Promise.all([
        this.loadStaffCache(),
        API.get('/complaints')
      ]);

      const complaints = (complaintsRes.success && Array.isArray(complaintsRes.data)) ? complaintsRes.data : [];
      this.cachedComplaints = complaints;

      const render = () => {
        const search = document.getElementById('search-staff')?.value.trim().toLowerCase() || '';
        const deptFilter = document.getElementById('filter-dept')?.value || 'ALL';

        const filtered = staffList.filter(s => {
          if (deptFilter !== 'ALL' && s.department !== deptFilter) return false;
          if (!search) return true;
          return (
            s.userId.toLowerCase().includes(search) ||
            s.name.toLowerCase().includes(search) ||
            s.username.toLowerCase().includes(search) ||
            (s.department && s.department.toLowerCase().includes(search))
          );
        });

        const tbody = document.getElementById('staff-table-tbody');
        const countEl = document.getElementById('staff-count');
        if (countEl) countEl.textContent = `Total Staff: ${filtered.length}`;

        if (tbody) {
          tbody.innerHTML = filtered.map(s => {
            const assignedComplaints = complaints.filter(c => c.assignedStaffId === s.userId);
            const activeTasks = assignedComplaints.filter(c => c.status !== 'RESOLVED').length;
            return `
              <tr>
                <td><strong>${s.userId}</strong></td>
                <td>${s.name}</td>
                <td><code>${s.username}</code></td>
                <td><span class="badge badge-role-staff">${s.department || 'General'}</span></td>
                <td>
                  <strong>${assignedComplaints.length}</strong> total
                  <span class="text-muted" style="font-size: 0.75rem;">(${activeTasks} active)</span>
                </td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="AdminApp.viewStaffDetails('${s.userId}')">
                    View Workload
                  </button>
                </td>
              </tr>
            `;
          }).join('');
        }
      };

      const searchInput = document.getElementById('search-staff');
      if (searchInput) searchInput.addEventListener('input', render);

      const deptFilter = document.getElementById('filter-dept');
      if (deptFilter) deptFilter.addEventListener('change', render);

      render();

    } catch (e) {
      console.error('Error loading staff page', e);
      UI.showToast('Unable to load staff list.', 'danger');
    }
  },

  viewStaffDetails: function (staffId) {
    const staff = this.cachedStaff.find(s => s.userId === staffId);
    if (!staff) return;

    const complaints = this.cachedComplaints.filter(c => c.assignedStaffId === staffId);
    const modalBody = document.getElementById('staff-modal-body');
    if (!modalBody) return;

    modalBody.innerHTML = `
      <div class="detail-list">
        <div class="detail-item">
          <div class="detail-label">Staff ID</div>
          <div class="detail-value text-primary">${staff.userId}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Name</div>
          <div class="detail-value">${staff.name}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Department</div>
          <div class="detail-value">${staff.department || 'General Maintenance'}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Username</div>
          <div class="detail-value">${staff.username}</div>
        </div>
      </div>

      <h4 style="margin: 1.25rem 0 0.5rem; font-size: 0.95rem; font-weight: 700;">Assigned Workload (${complaints.length})</h4>
      ${complaints.length === 0 ? '<p class="text-muted" style="font-size: 0.85rem;">No maintenance tasks assigned currently.</p>' : `
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Room</th>
                <th>Category</th>
                <th>Status</th>
                <th>Remarks</th>
              </tr>
            </thead>
            <tbody>
              ${complaints.map(c => `
                <tr>
                  <td><strong>${c.complaintId}</strong></td>
                  <td>${c.roomNumber}</td>
                  <td>${c.category}</td>
                  <td>${ComplaintService.getStatusBadge(c.status)}</td>
                  <td class="cell-desc">${c.remarks || '—'}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `}
    `;

    UI.openModal('staff-detail-modal');
  },

  /**
   * Initialize Reports Page (admin/reports.html)
   */
  initReportsPage: async function () {
    this.init();
    if (!this.currentUser) return;

    try {
      const [reportsRes, complaintsRes, staffList] = await Promise.all([
        API.get('/reports'),
        API.get('/complaints'),
        this.loadStaffCache()
      ]);

      const complaints = (complaintsRes.success && Array.isArray(complaintsRes.data)) ? complaintsRes.data : [];
      this.cachedComplaints = complaints;

      const reportData = (reportsRes.success && reportsRes.data) ? reportsRes.data : {};
      const total = reportData.total ?? complaints.length;
      const pending = reportData.pending ?? complaints.filter(c => c.status === 'PENDING').length;
      const assigned = reportData.assigned ?? complaints.filter(c => c.status === 'ASSIGNED').length;
      const inProgress = reportData.inProgress ?? complaints.filter(c => c.status === 'IN_PROGRESS').length;
      const resolved = reportData.resolved ?? complaints.filter(c => c.status === 'RESOLVED').length;

      const repTotalEl = document.getElementById('rep-total');
      const repPendingEl = document.getElementById('rep-pending');
      const repAssignedEl = document.getElementById('rep-assigned');
      const repProgressEl = document.getElementById('rep-progress');
      const repResolvedEl = document.getElementById('rep-resolved');

      if (repTotalEl) repTotalEl.textContent = total;
      if (repPendingEl) repPendingEl.textContent = pending;
      if (repAssignedEl) repAssignedEl.textContent = assigned;
      if (repProgressEl) repProgressEl.textContent = inProgress;
      if (repResolvedEl) repResolvedEl.textContent = resolved;

      // Category breakdown
      const catContainer = document.getElementById('rep-categories-list');
      if (catContainer) {
        catContainer.innerHTML = ComplaintService.CATEGORIES.map(cat => {
          const count = reportData.categories ? (reportData.categories[cat] || 0) : complaints.filter(c => c.category === cat).length;
          const pct = total > 0 ? Math.round((count / total) * 100) : 0;
          return `
            <div class="dist-item">
              <div class="dist-header">
                <span>${cat}</span>
                <span><strong>${count}</strong> (${pct}%)</span>
              </div>
              <div class="dist-bar-bg">
                <div class="dist-bar-fill" style="width: ${pct}%; background-color: var(--primary);"></div>
              </div>
            </div>
          `;
        }).join('');
      }

      // Priority breakdown
      const priContainer = document.getElementById('rep-priorities-list');
      if (priContainer) {
        priContainer.innerHTML = ComplaintService.PRIORITIES.map(pri => {
          const count = reportData.priorities ? (reportData.priorities[pri] || 0) : complaints.filter(c => c.priority === pri).length;
          const pct = total > 0 ? Math.round((count / total) * 100) : 0;
          let color = pri === 'HIGH' ? 'var(--danger)' : pri === 'MEDIUM' ? 'var(--warning)' : 'var(--gray)';
          return `
            <div class="dist-item">
              <div class="dist-header">
                <span>${pri} Priority</span>
                <span><strong>${count}</strong> (${pct}%)</span>
              </div>
              <div class="dist-bar-bg">
                <div class="dist-bar-fill" style="width: ${pct}%; background-color: ${color};"></div>
              </div>
            </div>
          `;
        }).join('');
      }

      // Generate Report Button Handler
      const genBtn = document.getElementById('btn-generate-report');
      const reportOutput = document.getElementById('generated-report-area');
      if (genBtn && reportOutput) {
        genBtn.addEventListener('click', () => {
          genBtn.classList.add('is-loading');

          setTimeout(() => {
            genBtn.classList.remove('is-loading');
            const resolutionRate = total > 0 ? Math.round((resolved / total) * 100) : 0;

            reportOutput.innerHTML = `
              <div class="card" style="border-top: 4px solid var(--primary); animation: fadeIn 0.3s ease;">
                <div class="card-header">
                  <div>
                    <h3 class="card-title">Official Hostel Maintenance Performance Audit</h3>
                    <span class="text-muted" style="font-size: 0.8rem;">Generated on: ${new Date().toLocaleString()} | Authority: Chief Warden Office</span>
                  </div>
                  <button class="btn btn-secondary btn-sm" onclick="window.print()">
                    Print / Save PDF
                  </button>
                </div>
                <div class="card-body">
                  <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; text-align: center;">
                    <div style="background: #f8fafc; padding: 1rem; border-radius: 8px; border: 1px solid #e2e8f0;">
                      <div style="font-size: 1.8rem; font-weight: 700; color: var(--primary);">${total}</div>
                      <div style="font-size: 0.75rem; color: var(--gray);">Total Registered</div>
                    </div>
                    <div style="background: #f8fafc; padding: 1rem; border-radius: 8px; border: 1px solid #e2e8f0;">
                      <div style="font-size: 1.8rem; font-weight: 700; color: var(--success);">${resolutionRate}%</div>
                      <div style="font-size: 0.75rem; color: var(--gray);">Resolution Rate</div>
                    </div>
                    <div style="background: #f8fafc; padding: 1rem; border-radius: 8px; border: 1px solid #e2e8f0;">
                      <div style="font-size: 1.8rem; font-weight: 700; color: var(--warning);">${pending + inProgress}</div>
                      <div style="font-size: 0.75rem; color: var(--gray);">Active Work Orders</div>
                    </div>
                    <div style="background: #f8fafc; padding: 1rem; border-radius: 8px; border: 1px solid #e2e8f0;">
                      <div style="font-size: 1.8rem; font-weight: 700; color: var(--info);">${staffList.length}</div>
                      <div style="font-size: 0.75rem; color: var(--gray);">Active Staff Deployments</div>
                    </div>
                  </div>

                  <h4 style="font-size: 0.95rem; font-weight: 700; margin-bottom: 0.75rem;">Summary Analysis</h4>
                  <p style="font-size: 0.85rem; color: var(--gray-dark); margin-bottom: 1rem;">
                    The system currently records <strong>${total}</strong> total student complaints. <strong>${resolved}</strong> complaints have been successfully resolved by the maintenance personnel, yielding an overall completion rate of <strong>${resolutionRate}%</strong>.
                    High priority items are prioritized with immediate staff assignments.
                  </p>

                  <h4 style="font-size: 0.95rem; font-weight: 700; margin-bottom: 0.75rem;">Detailed Itemized Audit</h4>
                  <div class="table-responsive">
                    <table class="data-table">
                      <thead>
                        <tr>
                          <th>Complaint ID</th>
                          <th>Student & Room</th>
                          <th>Category</th>
                          <th>Priority</th>
                          <th>Assigned Staff</th>
                          <th>Status</th>
                          <th>Remarks</th>
                        </tr>
                      </thead>
                      <tbody>
                        ${complaints.map(c => `
                          <tr>
                            <td><strong>${c.complaintId}</strong></td>
                            <td>${c.studentId} (${c.roomNumber})</td>
                            <td>${c.category}</td>
                            <td>${ComplaintService.getPriorityBadge(c.priority)}</td>
                            <td>${ComplaintService.getStaffInfo(c.assignedStaffId)}</td>
                            <td>${ComplaintService.getStatusBadge(c.status)}</td>
                            <td style="font-size: 0.8rem;">${c.remarks || 'None'}</td>
                          </tr>
                        `).join('')}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            `;

            reportOutput.scrollIntoView({ behavior: 'smooth' });
            UI.showToast('Audit Report generated successfully!', 'success');
          }, 400);
        });
      }

    } catch (e) {
      console.error('Error loading reports page', e);
      UI.showToast('Unable to load reports data.', 'danger');
    }
  }
};
