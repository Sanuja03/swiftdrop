import Spinner from "@/shared/components/ui/Spinner";

import { useCallback, useEffect, useState } from "react";
import {
  getMyNotifications,
  markNotificationAsRead,
} from "../services/notificationApi";
import "./MyNotifications.css";
import PageHeader from "@/shared/components/ui/PageHeader";
import Button from "@/shared/components/ui/Button";
import { Link } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";

function formatDate(dateString) {
  if (!dateString) return "";

  return new Intl.DateTimeFormat("en-LK", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(dateString));
}

export default function MyNotifications() {
  const { user } = useAuth();
  const role = user?.role?.replace(/^ROLE_/, "");
  const isOperations = ["ADMIN", "DISPATCHER"].includes(role);
  const [notifications, setNotifications] = useState([]);
  const [filter, setFilter] = useState("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [updatingId, setUpdatingId] = useState(null);

  const loadNotifications = useCallback(async () => {
    try {
      const data = await getMyNotifications();
      setNotifications(data);
      setError("");
      window.dispatchEvent(new Event("swiftdrop:notifications-changed"));
    } catch (err) {
      setError(err.message || "Unable to load notifications.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Initial loading state is already true. Updates in this loader follow awaited API requests.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadNotifications();
  }, [loadNotifications]);

  const unreadCount = notifications.filter(
    (notification) => !notification.isRead
  ).length;

  const visibleNotifications =
    filter === "unread"
      ? notifications.filter((notification) => !notification.isRead)
      : notifications;

  async function handleMarkAsRead(notificationId) {
    setUpdatingId(notificationId);
    setError("");

    try {
      const updated = await markNotificationAsRead(notificationId);
      window.dispatchEvent(new Event("swiftdrop:notifications-changed"));

      setNotifications((current) =>
        current.map((notification) =>
          notification.id === notificationId
            ? updated
            : notification
        )
      );
    } catch (err) {
      setError(err.message || "Unable to update notification.");
    } finally {
      setUpdatingId(null);
    }
  }

  return (
    <div className="my-notifications">
      <div className="my-notifications__container">
        <PageHeader
          title="My Notifications"
          subtitle={isOperations ? "Stay updated on important operations activity." : "Stay updated on your parcels and account activity."}
          action={<div className="my-notifications__count" aria-live="polite"><strong>{unreadCount}</strong><span>Unread</span></div>}
        />

        <section className="my-notifications__panel">
          <div className="my-notifications__toolbar">
            <div
              className="my-notifications__filters"
              role="group"
              aria-label="Notification filters"
            >
              <button
                type="button"
                aria-pressed={filter === "all"}
                className={filter === "all" ? "active" : ""}
                onClick={() => setFilter("all")}
              >
                All ({notifications.length})
              </button>

              <button
                type="button"
                aria-pressed={filter === "unread"}
                className={filter === "unread" ? "active" : ""}
                onClick={() => setFilter("unread")}
              >
                Unread ({unreadCount})
              </button>
            </div>

            <Button
              variant="secondary"
              onClick={() => {
                setLoading(true);
                setError("");
                loadNotifications();
              }}
              disabled={loading || updatingId !== null}
            >
              Refresh
            </Button>
          </div>

          {error && (
            <div className="my-notifications__error" role="alert">
              {error}
            </div>
          )}

          {loading ? (
            <Spinner label="Loading notifications..." />
          ) : error && notifications.length === 0 ? null : visibleNotifications.length === 0 ? (
            <div className="my-notifications__empty">
              <div className="my-notifications__empty-icon" aria-hidden="true">
                ✓
              </div>

              <h2>
                {filter === "unread"
                  ? "You're all caught up!"
                  : "No notifications yet"}
              </h2>

              <p>
                {filter === "unread"
                  ? "You have no unread notifications."
                  : isOperations ? "Important operations alerts will appear here." : "Your parcel and account updates will appear here."}
              </p>
            </div>
          ) : (
            <div className="my-notifications__list">
              {visibleNotifications.map((notification) => (
                <article
                  key={notification.id}
                  className={`my-notifications__item ${
                    notification.isRead ? "is-read" : "is-unread"
                  }`}
                >
                  <div className="my-notifications__item-icon" aria-hidden="true">
                    {notification.notificationType === "PARCEL_STATUS"
                      ? "📦"
                      : "🔔"}
                  </div>

                  <div className="my-notifications__item-content">
                    <div className="my-notifications__item-heading">
                      <h2>{notification.title}</h2>

                      {!notification.isRead && (
                        <span className="my-notifications__unread-dot" />
                      )}
                    </div>

                    <p>{notification.message}</p>
                    {notification.notificationType === "MERCHANT_APPROVAL" && role === "ADMIN" && (
                      <Link to="/admin/users?tab=merchants">Review merchant applications</Link>
                    )}

                    {notification.notificationType === "ASSIGNMENT_CREATED" && role === "COURIER" && notification.parcelId && (
                      <Link to={`/courier/deliveries/${encodeURIComponent(notification.parcelId)}`}>View assignment</Link>
                    )}
                    {notification.notificationType === "ASSIGNMENT_CANCELLED" && role === "COURIER" && (
                      <Link to="/courier/deliveries">View current deliveries</Link>
                    )}
                    {notification.notificationType === "ASSIGNMENT_REJECTED" && isOperations && (
                      <Link to="/dispatch">Review dispatch queue</Link>
                    )}
                    {notification.notificationType === "DELIVERY_FAILED" && isOperations && (
                      <Link to="/dispatch/workload">Review courier workloads</Link>
                    )}
                    {notification.notificationType === "MERCHANT_PARCEL_OUTCOME" && role === "MERCHANT" && (
                      <Link to="/merchant">View merchant portal</Link>
                    )}
                    <time dateTime={notification.createdAt}>
                      {formatDate(notification.createdAt)}
                    </time>
                  </div>

                  {!notification.isRead && (
                    <button
                      type="button"
                      className="my-notifications__mark-read"
                      disabled={updatingId !== null || loading}
                      onClick={() =>
                        handleMarkAsRead(notification.id)
                      }
                    >
                      {updatingId === notification.id
                        ? "Saving..."
                        : "Mark as read"}
                    </button>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
