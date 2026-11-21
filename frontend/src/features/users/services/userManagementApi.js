import { request } from "@/shared/api/httpClient";

export async function getAllUsers() {
  const users = await request("/admin/users");
  if (!Array.isArray(users)) throw new Error("The user API returned an unexpected response.");
  return users;
}

export function suspendUser(userId) {
  return request(`/admin/users/${encodeURIComponent(userId)}/status?status=SUSPENDED`, { method: "PATCH" });
}

export function restoreUser(userId) {
  return request(`/admin/users/${encodeURIComponent(userId)}/status?status=ACTIVE`, { method: "PATCH" });
}

// Retained for existing callers of the account-status integration.
export function approveMerchant(userId) {
  return restoreUser(userId);
}
