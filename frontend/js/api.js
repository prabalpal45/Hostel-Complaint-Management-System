/**
 * Hostel Complaint & Maintenance Management System
 * API Client Layer (api.js)
 * 
 * Centralizes all HTTP communication between the browser frontend
 * and the Core Java HttpServer backend.
 */

const API = {
  BASE_URL: 'http://localhost:8080/api',

  /**
   * Helper to build request headers with authentication
   */
  getHeaders: function (customHeaders = {}) {
    const headers = {
      'Content-Type': 'application/json',
      ...customHeaders
    };

    // Attach user session identity for server-side authorization
    try {
      if (typeof Auth !== 'undefined' && Auth.getCurrentUser) {
        const user = Auth.getCurrentUser();
        if (user && user.userId) {
          headers['X-User-Id'] = user.userId;
          headers['X-User-Role'] = user.role;
        }
      }
    } catch (e) {
      console.warn('Could not attach auth headers', e);
    }

    return headers;
  },

  /**
   * Generic request executor with error handling
   */
  request: async function (endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${this.BASE_URL}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;
    
    try {
      const response = await fetch(url, options);
      const text = await response.text();

      let data;
      try {
        data = text ? JSON.parse(text) : {};
      } catch (jsonErr) {
        data = { success: response.ok, message: text };
      }

      if (!response.ok) {
        const errorMessage = data.message || `Server returned error (${response.status})`;
        return {
          success: false,
          status: response.status,
          message: errorMessage,
          data: data
        };
      }

      return {
        success: true,
        status: response.status,
        data: data
      };

    } catch (networkError) {
      console.error(`Network error requesting ${url}:`, networkError);
      return {
        success: false,
        status: 0,
        message: 'Unable to connect to the Java backend. Please verify Server is running at http://localhost:8080.',
        error: networkError
      };
    }
  },

  /**
   * HTTP GET request
   */
  get: async function (endpoint, customHeaders = {}) {
    return this.request(endpoint, {
      method: 'GET',
      headers: this.getHeaders(customHeaders)
    });
  },

  /**
   * HTTP POST request
   */
  post: async function (endpoint, body = {}, customHeaders = {}) {
    return this.request(endpoint, {
      method: 'POST',
      headers: this.getHeaders(customHeaders),
      body: JSON.stringify(body)
    });
  },

  /**
   * HTTP PUT request
   */
  put: async function (endpoint, body = {}, customHeaders = {}) {
    return this.request(endpoint, {
      method: 'PUT',
      headers: this.getHeaders(customHeaders),
      body: JSON.stringify(body)
    });
  },

  /**
   * Health Check
   */
  healthCheck: async function () {
    return this.get('/health');
  }
};
