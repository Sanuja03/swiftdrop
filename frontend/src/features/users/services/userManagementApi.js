
const API_BASE_URL = "/api/v1";

// Retrieve the authentication token from local storage.
function getToken() {
  return localStorage.getItem("swiftdrop_token");
}

// Send authenticated requests to the admin backend API.
async function adminRequest(path, options = {}) {
  const token = getToken();

  if (!token) {
    throw new Error(
      "Please log in with an admin account to continue."
    );
  }

  let response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
        ...options.headers,
      },
    });
  } catch {
    throw new Error(
      "Cannot connect to the SwiftDrop backend. Check that the server is running."
    );
  }

  // Handle unsuccessful API responses.
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        "Your session has expired. Please log in again."
      );
    }

    if (response.status === 403) {
      throw new Error(
        "Access denied. You need administrator permission for this action."
      );
    }

    let message = "The request failed. Please try again.";

    try {
      const data = await response.json();

      if (typeof data.message === "string") {
        message = data.message;
      }
    } catch {
      // Use the default error message.
    }

    throw new Error(message);
  }

  // Handle responses without content.
  if (response.status === 204) {
    return null;
  }

  return response.json();
}

// --------------------------------------------------
// USER MANAGEMENT
// --------------------------------------------------

// Retrieve registered users through Spring Boot.
export async function getAllUsers() {
  const users = await adminRequest("/admin/users");

  if (!Array.isArray(users)) {
    throw new Error(
      "The user API returned an unexpected response."
    );
  }

  return users;
}

// Suspend an active user account.
export async function suspendUser(userId) {
  return adminRequest(
    `/admin/users/${encodeURIComponent(userId)}/status?status=SUSPENDED`,
    {
      method: "PATCH",
    }
  );
}

// Restore a suspended user account.
export async function restoreUser(userId) {
  return adminRequest(
    `/admin/users/${encodeURIComponent(userId)}/status?status=ACTIVE`,
    {
      method: "PATCH",
    }
  );
}


// MERCHANT APPROVAL MANAGEMENT

// Approve a merchant awaiting administrator approval.
// Changes the merchant account status from
// PENDING_APPROVAL to ACTIVE.
export async function approveMerchant(userId) {
  return adminRequest(
    `/admin/users/${encodeURIComponent(userId)}/status?status=ACTIVE`,
    {
      method: "PATCH",
    }
  );
}
