/**
 * Hostel Complaint & Maintenance Management System
 * Mock Data Storage Layer (data.js)
 * 
 * Provides localStorage persistence mirroring the Java backend text-file storage.
 */

const STORAGE_KEYS = {
  USERS: 'HCS_USERS_DATA',
  COMPLAINTS: 'HCS_COMPLAINTS_DATA',
  AUTH: 'HCS_AUTH_SESSION'
};

// Default seed data matching Java model and data files
const DEFAULT_USERS = [
  // Students
  {
    userId: "S001",
    name: "Rahul Sharma",
    username: "student01",
    password: "1234",
    role: "STUDENT",
    roomNumber: "B-204",
    email: "rahul.s@college.edu",
    phone: "9876543210"
  },
  {
    userId: "S002",
    name: "Amit Kumar",
    username: "amit",
    password: "5678",
    role: "STUDENT",
    roomNumber: "B-305",
    email: "amit.k@college.edu",
    phone: "9876543211"
  },
  {
    userId: "TEST001",
    name: "Test Student",
    username: "teststudent",
    password: "1234",
    role: "STUDENT",
    roomNumber: "A-101",
    email: "test.student@college.edu",
    phone: "9876543212"
  },

  // Staff members
  {
    userId: "ST001",
    name: "Ramesh Kumar",
    username: "staff01",
    password: "1234",
    role: "STAFF",
    department: "Electrical",
    email: "ramesh.elec@hostel.edu",
    phone: "9123456780"
  },
  {
    userId: "ST002",
    name: "Suresh Patel",
    username: "staff02",
    password: "1234",
    role: "STAFF",
    department: "Plumbing",
    email: "suresh.plumb@hostel.edu",
    phone: "9123456781"
  },
  {
    userId: "ST003",
    name: "Mahesh Singh",
    username: "staff03",
    password: "1234",
    role: "STAFF",
    department: "Cleaning",
    email: "mahesh.clean@hostel.edu",
    phone: "9123456782"
  },
  {
    userId: "ST004",
    name: "Vikram Verma",
    username: "staff04",
    password: "1234",
    role: "STAFF",
    department: "General Maintenance",
    email: "vikram.gen@hostel.edu",
    phone: "9123456783"
  },

  // Admin
  {
    userId: "ADM001",
    name: "Chief Warden Admin",
    username: "admin",
    password: "admin123",
    role: "ADMIN",
    email: "warden.admin@hostel.edu",
    phone: "9988776655"
  }
];

const DEFAULT_COMPLAINTS = [
  {
    complaintId: "C001",
    studentId: "S001",
    roomNumber: "B-204",
    category: "Electrical",
    description: "Ceiling fan regulator is not responding and fan runs only at maximum speed.",
    status: "ASSIGNED",
    priority: "HIGH",
    assignedStaffId: "ST001",
    remarks: "Fan regulator needs replacement. Inspection scheduled for tomorrow morning.",
    createdAt: "2026-03-28"
  },
  {
    complaintId: "C002",
    studentId: "S002",
    roomNumber: "B-305",
    category: "Plumbing",
    description: "Washbasin tap is continuously dripping and valve seems loose.",
    status: "PENDING",
    priority: "MEDIUM",
    assignedStaffId: "",
    remarks: "",
    createdAt: "2026-03-29"
  },
  {
    complaintId: "C003",
    studentId: "S001",
    roomNumber: "B-204",
    category: "Cleaning",
    description: "Washroom drainage requires deep cleaning and sanitation.",
    status: "IN_PROGRESS",
    priority: "LOW",
    assignedStaffId: "ST003",
    remarks: "Cleaning crew assigned; chemical sanitization in progress.",
    createdAt: "2026-03-30"
  },
  {
    complaintId: "C004",
    studentId: "TEST001",
    roomNumber: "A-101",
    category: "Internet",
    description: "Wi-Fi access point in 1st floor corridor disconnects frequently.",
    status: "RESOLVED",
    priority: "MEDIUM",
    assignedStaffId: "ST004",
    remarks: "Access point router rebooted and Cat-6 patch cable replaced.",
    createdAt: "2026-03-25"
  },
  {
    complaintId: "C005",
    studentId: "S001",
    roomNumber: "B-204",
    category: "Furniture",
    description: "Study chair right armrest is cracked and unsteady.",
    status: "PENDING",
    priority: "LOW",
    assignedStaffId: "",
    remarks: "",
    createdAt: "2026-04-01"
  },
  {
    complaintId: "C006",
    studentId: "S002",
    roomNumber: "B-305",
    category: "Water",
    description: "Solar geyser hot water not reaching 3rd floor wing bathrooms.",
    status: "IN_PROGRESS",
    priority: "HIGH",
    assignedStaffId: "ST002",
    remarks: "Pressure pump valve inspected; replacement gasket being fitted.",
    createdAt: "2026-04-02"
  },
  {
    complaintId: "C007",
    studentId: "TEST001",
    roomNumber: "A-101",
    category: "Electrical",
    description: "Main tube light flickering repeatedly during study hours.",
    status: "RESOLVED",
    priority: "HIGH",
    assignedStaffId: "ST001",
    remarks: "Installed new LED choke and tube.",
    createdAt: "2026-03-22"
  }
];

// DataStore API
const DataStore = {
  // Initialize storage if empty
  init: function () {
    if (!localStorage.getItem(STORAGE_KEYS.USERS)) {
      localStorage.setItem(STORAGE_KEYS.USERS, JSON.stringify(DEFAULT_USERS));
    }
    if (!localStorage.getItem(STORAGE_KEYS.COMPLAINTS)) {
      localStorage.setItem(STORAGE_KEYS.COMPLAINTS, JSON.stringify(DEFAULT_COMPLAINTS));
    }
  },

  // Reset to default seed
  resetToDefaults: function () {
    localStorage.setItem(STORAGE_KEYS.USERS, JSON.stringify(DEFAULT_USERS));
    localStorage.setItem(STORAGE_KEYS.COMPLAINTS, JSON.stringify(DEFAULT_COMPLAINTS));
  },

  // Users
  getUsers: function () {
    this.init();
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEYS.USERS)) || [];
    } catch (e) {
      console.error("Error parsing users", e);
      return [];
    }
  },

  saveUsers: function (users) {
    localStorage.setItem(STORAGE_KEYS.USERS, JSON.stringify(users));
  },

  addUser: function (user) {
    const users = this.getUsers();
    users.push(user);
    this.saveUsers(users);
    return user;
  },

  getUserById: function (userId) {
    return this.getUsers().find(u => u.userId === userId) || null;
  },

  getUserByUsername: function (username) {
    return this.getUsers().find(u => u.username.toLowerCase() === username.trim().toLowerCase()) || null;
  },

  getStudents: function () {
    return this.getUsers().filter(u => u.role === "STUDENT");
  },

  getStaffList: function () {
    return this.getUsers().filter(u => u.role === "STAFF");
  },

  // Complaints
  getComplaints: function () {
    this.init();
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEYS.COMPLAINTS)) || [];
    } catch (e) {
      console.error("Error parsing complaints", e);
      return [];
    }
  },

  saveComplaints: function (complaints) {
    localStorage.setItem(STORAGE_KEYS.COMPLAINTS, JSON.stringify(complaints));
  },

  getComplaintById: function (complaintId) {
    return this.getComplaints().find(c => c.complaintId === complaintId) || null;
  },

  addComplaint: function (complaint) {
    const complaints = this.getComplaints();
    complaints.unshift(complaint); // Add to top
    this.saveComplaints(complaints);
    return complaint;
  },

  updateComplaint: function (complaintId, updatedFields) {
    const complaints = this.getComplaints();
    const index = complaints.findIndex(c => c.complaintId === complaintId);
    if (index !== -1) {
      complaints[index] = { ...complaints[index], ...updatedFields };
      this.saveComplaints(complaints);
      return complaints[index];
    }
    return null;
  },

  // Helper to generate next IDs
  generateComplaintId: function () {
    const complaints = this.getComplaints();
    const numbers = complaints
      .map(c => {
        const match = c.complaintId.match(/\d+/);
        return match ? parseInt(match[0], 10) : 0;
      })
      .filter(n => !isNaN(n));
    const maxNum = numbers.length > 0 ? Math.max(...numbers) : 0;
    const nextNum = maxNum + 1;
    return "C" + String(nextNum).padStart(3, "0");
  },

  generateStudentId: function () {
    const students = this.getStudents();
    const numbers = students
      .map(s => {
        const match = s.userId.match(/\d+/);
        return match ? parseInt(match[0], 10) : 0;
      })
      .filter(n => !isNaN(n));
    const maxNum = numbers.length > 0 ? Math.max(...numbers) : 0;
    return "S" + String(maxNum + 1).padStart(3, "0");
  }
};

// Initialize immediately on file load
DataStore.init();
