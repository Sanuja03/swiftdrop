import { request } from "@/shared/api/httpClient";

async function notificationList(path, options) {
  const notifications = await request(path, options);
  if (!Array.isArray(notifications)) throw new Error("The notification API returned an unexpected response.");
  return notifications;
}

export function getMyNotifications(options = {}) {
  return notificationList("/notifications", options);
}

export function getUnreadNotifications(options = {}) {
  return notificationList("/notifications/unread", options);
}

export function getUnreadCount() {
  return request("/notifications/unread-count");
}

export function markNotificationAsRead(notificationId) {
  return request(`/notifications/${encodeURIComponent(notificationId)}/read`, { method: "PATCH" });
}
