const API_BASE_URL = 'http://localhost:8080/api/v1';

export async function loginUser(email, password) {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password }),
    });

    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.message || 'Login failed. Please check your credentials.');
    }

    return { success: true, data };
  } catch (error) {
    return {
      success: false,
      error: error.message || 'Unable to connect to SwiftDrop authentication server.',
    };
  }
}

export async function signupUser(userData) {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/signup`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(userData),
    });

    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.message || 'Registration failed.');
    }

    return { success: true, data };
  } catch (error) {
    return {
      success: false,
      error: error.message || 'Unable to connect to authentication server.',
    };
  }
}

export async function getCurrentUser(token) {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/me`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      throw new Error('Failed to fetch user profile');
    }

    return await response.json();
  } catch (error) {
    return null;
  }
}

export async function testEndpoint(endpoint, token) {
  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    });

    const data = await response.json();
    return {
      status: response.status,
      ok: response.ok,
      data,
    };
  } catch (error) {
    return {
      status: 500,
      ok: false,
      error: error.message,
    };
  }
}


