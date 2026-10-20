/**
 * Hostel Complaint & Maintenance Management System
 * Authentication & Session Management (auth.js)
 * 
 * Provides mock client-side authentication and route protection.
 * NOTE: For project demonstration only; backend Java service will provide security later.
 */

const Auth = {
  SESSION_KEY: 'HCS_LOGGED_IN_USER',

  /**
   * Log in user with credentials and role
   */
  login: async function (username, password, selectedRole) {

    if (!username || !password || !selectedRole) {
      return {
        success: false,
        message: "Please fill in all fields."
      };
    }

    const res = await API.post('/login', {
      username: username.trim(),
      password: password
    });

    if (!res.success) {
      return {
        success: false,
        message: res.message || "Login failed."
      };
    }

    const data = res.data;
    if (!data.role) {
      return {
        success: false,
        message: "Server did not return a user role."
      };
    }

    const backendRole = data.role.toUpperCase();

    // Check selected role matches backend role
    if (backendRole !== selectedRole.toUpperCase()) {
      return {
        success: false,
        message: "Selected role does not match your account."
      };
    }

    // Create session (never store password!)
    const sessionData = {
      userId: data.userId,
      name: data.name,
      username: data.username,
      role: backendRole,
      roomNumber: data.roomNumber || null,
      department: data.department || null,
      loginAt: new Date().toISOString()
    };

    localStorage.setItem(this.SESSION_KEY, JSON.stringify(sessionData));
    sessionStorage.setItem(this.SESSION_KEY, JSON.stringify(sessionData));

    return {
      success: true,
      user: sessionData
    };
  },

  /**
   * Register a new student via Java backend
   */
  registerStudent: async function (data) {
    const { studentId, name, username, password, confirmPassword, roomNumber } = data;

    // Validation
    if (!studentId || !studentId.trim()) {
      return { success: false, message: "Student ID is required." };
    }
    if (!name || !name.trim()) {
      return { success: false, message: "Full Name is required." };
    }
    if (!username || !username.trim()) {
      return { success: false, message: "Username is required." };
    }
    if (!password) {
      return { success: false, message: "Password is required." };
    }
    if (password.length < 4) {
      return { success: false, message: "Password must be at least 4 characters." };
    }
    if (password !== confirmPassword) {
      return { success: false, message: "Passwords do not match." };
    }
    if (!roomNumber || !roomNumber.trim()) {
      return { success: false, message: "Room Number is required." };
    }

    const res = await API.post('/register', {
      studentId: studentId.trim(),
      name: name.trim(),
      username: username.trim(),
      password: password,
      roomNumber: roomNumber.trim()
    });

    if (!res.success) {
      return {
        success: false,
        message: res.message || "Registration failed."
      };
    }

    return {
      success: true,
      message: res.data.message || "Registration successful.",
      student: res.data
    };
  },

  /**
   * Get current logged-in user
   */
  getCurrentUser: function () {
    try {
      const stored = sessionStorage.getItem(this.SESSION_KEY) || localStorage.getItem(this.SESSION_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch (e) {
      console.error("Error reading auth session", e);
      return null;
    }
  },

  /**
   * Determine relative path based on current folder
   */
  getRelativeBase: function () {
    const path = window.location.pathname.replace(/\\/g, '/');
    if (path.includes('/student/') || path.includes('/staff/') || path.includes('/admin/')) {
      return '../';
    }
    return './';
  },

  /**
   * Require login and specific role for protected pages
   */
  requireAuth: function (requiredRole) {
    const user = this.getCurrentUser();
    const base = this.getRelativeBase();

    if (!user) {
      window.location.href = base + 'index.html?error=unauthorized';
      return null;
    }

    if (requiredRole && user.role.toUpperCase() !== requiredRole.toUpperCase()) {
      // Redirect to correct dashboard based on actual role
      if (user.role === 'STUDENT') {
        window.location.href = base + 'student/dashboard.html';
      } else if (user.role === 'STAFF') {
        window.location.href = base + 'staff/dashboard.html';
      } else if (user.role === 'ADMIN') {
        window.location.href = base + 'admin/dashboard.html';
      }
      return null;
    }

    return user;
  },

  /**
   * Log out user
   */
  logout: function () {
    localStorage.removeItem(this.SESSION_KEY);
    sessionStorage.removeItem(this.SESSION_KEY);
    const base = this.getRelativeBase();
    window.location.href = base + 'index.html?logged_out=1';
  }
};
