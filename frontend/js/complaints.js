/**
 * Hostel Complaint & Maintenance Management System
 * Complaints Logic & Component Renderers (complaints.js)
 * 
 * Provides unified complaint processing, formatting, and UI badge/timeline renderers.
 */

const ComplaintService = {
  STATUSES: ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED'],
  PRIORITIES: ['LOW', 'MEDIUM', 'HIGH'],
  CATEGORIES: ['Electrical', 'Plumbing', 'Cleaning', 'Furniture', 'Internet', 'Water', 'Other'],

  /**
   * Return HTML string for status badge
   */
  getStatusBadge: function (status) {
    const s = (status || 'PENDING').toUpperCase();
    let badgeClass = 'badge-status-pending';
    let label = 'Pending';

    switch (s) {
      case 'PENDING':
        badgeClass = 'badge-status-pending';
        label = 'Pending';
        break;
      case 'ASSIGNED':
        badgeClass = 'badge-status-assigned';
        label = 'Assigned';
        break;
      case 'IN_PROGRESS':
        badgeClass = 'badge-status-in_progress';
        label = 'In Progress';
        break;
      case 'RESOLVED':
        badgeClass = 'badge-status-resolved';
        label = 'Resolved';
        break;
    }

    return `<span class="badge ${badgeClass}">${label}</span>`;
  },

  /**
   * Return HTML string for priority badge
   */
  getPriorityBadge: function (priority) {
    const p = (priority || 'MEDIUM').toUpperCase();
    let badgeClass = 'badge-priority-medium';
    let label = 'Medium';

    switch (p) {
      case 'LOW':
        badgeClass = 'badge-priority-low';
        label = 'Low';
        break;
      case 'MEDIUM':
        badgeClass = 'badge-priority-medium';
        label = 'Medium';
        break;
      case 'HIGH':
        badgeClass = 'badge-priority-high';
        label = 'High';
        break;
    }

    return `<span class="badge ${badgeClass}">${label}</span>`;
  },

  /**
   * Render horizontal/vertical tracking timeline based on complaint status
   */
  renderTimelineHtml: function (status) {
    const s = (status || 'PENDING').toUpperCase();

    // Step states: 1 = Submitted, 2 = Assigned, 3 = In Progress, 4 = Resolved
    let step1 = 'completed';
    let step2 = '';
    let step3 = '';
    let step4 = '';

    if (s === 'PENDING') {
      step1 = 'active';
    } else if (s === 'ASSIGNED') {
      step1 = 'completed';
      step2 = 'active';
    } else if (s === 'IN_PROGRESS') {
      step1 = 'completed';
      step2 = 'completed';
      step3 = 'active';
    } else if (s === 'RESOLVED') {
      step1 = 'completed';
      step2 = 'completed';
      step3 = 'completed';
      step4 = 'completed';
    }

    return `
      <div class="timeline">
        <div class="timeline-step ${step1}">
          <div class="timeline-marker">${step1 === 'completed' ? '✓' : '1'}</div>
          <div class="timeline-label">Submitted</div>
        </div>
        <div class="timeline-step ${step2}">
          <div class="timeline-marker">${step2 === 'completed' ? '✓' : '2'}</div>
          <div class="timeline-label">Assigned</div>
        </div>
        <div class="timeline-step ${step3}">
          <div class="timeline-marker">${step3 === 'completed' ? '✓' : '3'}</div>
          <div class="timeline-label">In Progress</div>
        </div>
        <div class="timeline-step ${step4}">
          <div class="timeline-marker">${step4 === 'completed' ? '✓' : '4'}</div>
          <div class="timeline-label">Resolved</div>
        </div>
      </div>
    `;
  },

  staffCache: {},
  setStaffCache: function (list) {
    if (Array.isArray(list)) {
      list.forEach(s => { this.staffCache[s.userId] = s; });
    }
  },

  /**
   * Get formatted staff name and department by staffId
   */
  getStaffInfo: function (staffId) {
    if (!staffId) return '<span class="text-muted">Not Assigned</span>';
    const staff = this.staffCache[staffId] || (typeof DataStore !== 'undefined' && DataStore.getUserById ? DataStore.getUserById(staffId) : null);
    if (staff) {
      return `<strong>${staff.name}</strong> <span class="text-muted">(${staff.department || 'Staff'})</span>`;
    }
    return `<strong>${staffId}</strong>`;
  },

  /**
   * Get formatted student name and room
   */
  getStudentInfo: function (studentId) {
    if (!studentId) return '<span class="text-muted">Unknown</span>';
    const student = typeof DataStore !== 'undefined' && DataStore.getUserById ? DataStore.getUserById(studentId) : null;
    if (student) {
      return `<strong>${student.name}</strong> <span class="text-muted">(${student.userId})</span>`;
    }
    return `<strong>${studentId}</strong>`;
  },

  /**
   * Filter and search complaints list
   */
  filterComplaints: function (list, filters = {}) {
    if (!Array.isArray(list)) return [];
    return list.filter(item => {
      // Search term (ID, Room, Description, Category)
      if (filters.search) {
        const query = filters.search.trim().toLowerCase();
        const matchesId = item.complaintId && item.complaintId.toLowerCase().includes(query);
        const matchesRoom = item.roomNumber && item.roomNumber.toLowerCase().includes(query);
        const matchesCategory = item.category && item.category.toLowerCase().includes(query);
        const matchesDesc = item.description && item.description.toLowerCase().includes(query);
        const matchesStudent = item.studentId && item.studentId.toLowerCase().includes(query);
        if (!matchesId && !matchesRoom && !matchesCategory && !matchesDesc && !matchesStudent) {
          return false;
        }
      }

      // Filter Status
      if (filters.status && filters.status !== 'ALL') {
        if (!item.status || item.status.toUpperCase() !== filters.status.toUpperCase()) {
          return false;
        }
      }

      // Filter Priority
      if (filters.priority && filters.priority !== 'ALL') {
        if (!item.priority || item.priority.toUpperCase() !== filters.priority.toUpperCase()) {
          return false;
        }
      }

      // Filter Category
      if (filters.category && filters.category !== 'ALL') {
        if (!item.category || item.category.toUpperCase() !== filters.category.toUpperCase()) {
          return false;
        }
      }

      // Filter Assigned Staff
      if (filters.staffId && filters.staffId !== 'ALL') {
        if (filters.staffId === 'UNASSIGNED') {
          if (item.assignedStaffId) return false;
        } else if (item.assignedStaffId !== filters.staffId) {
          return false;
        }
      }

      return true;
    });
  },

  /**
   * Create new complaint via Java backend
   */
  createComplaint: async function (data) {
    const { studentId, roomNumber, category, description, priority, complaintId } = data;

    if (!studentId || !roomNumber || !category || !description || !priority) {
      return { success: false, message: "Please fill in all required complaint fields." };
    }

    const payload = {
      studentId: studentId.trim(),
      roomNumber: roomNumber.trim().toUpperCase(),
      category: category.trim(),
      description: description.trim(),
      priority: priority.trim().toUpperCase()
    };

    if (complaintId && complaintId.trim()) {
      payload.complaintId = complaintId.trim().toUpperCase();
    }

    const res = await API.post('/complaints', payload);
    if (!res.success) {
      return { success: false, message: res.message || "Failed to create complaint." };
    }

    return {
      success: true,
      complaint: res.data.complaint || res.data,
      message: res.data.message || "Complaint created successfully."
    };
  },

  /**
   * Assign staff to complaint via Java backend
   */
  assignStaff: async function (complaintId, staffId) {
    if (!complaintId || !staffId) {
      return { success: false, message: "Complaint ID and Staff member are required." };
    }

    const res = await API.put(`/complaints/${encodeURIComponent(complaintId)}/assign`, {
      staffId: staffId.trim()
    });

    if (!res.success) {
      return { success: false, message: res.message || "Failed to assign staff." };
    }

    return {
      success: true,
      complaint: res.data.complaint,
      message: res.data.message || "Complaint assigned successfully."
    };
  },

  /**
   * Update complaint status via Java backend
   */
  updateStatus: async function (complaintId, newStatus) {
    if (!complaintId || !newStatus) {
      return { success: false, message: "Complaint ID and status are required." };
    }

    const res = await API.put(`/complaints/${encodeURIComponent(complaintId)}/status`, {
      status: newStatus.toUpperCase()
    });

    if (!res.success) {
      return { success: false, message: res.message || "Failed to update status." };
    }

    return {
      success: true,
      complaint: res.data.complaint,
      message: res.data.message || "Status updated successfully."
    };
  },

  /**
   * Add / update complaint remarks via Java backend
   */
  addRemarks: async function (complaintId, remarks) {
    if (!complaintId) {
      return { success: false, message: "Complaint ID is required." };
    }

    const res = await API.put(`/complaints/${encodeURIComponent(complaintId)}/remarks`, {
      remarks: (remarks || '').trim()
    });

    if (!res.success) {
      return { success: false, message: res.message || "Failed to add remarks." };
    }

    return {
      success: true,
      complaint: res.data.complaint,
      message: res.data.message || "Remarks saved successfully."
    };
  }
};
