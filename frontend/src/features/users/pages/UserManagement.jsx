import Spinner from "@/shared/components/ui/Spinner";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";

import {
  getAllUsers,
  suspendUser,
  restoreUser,
} from "../services/userManagementApi";

import {
  getMerchantApplications,
  approveMerchantApplication,
  rejectMerchantApplication,
} from "../services/merchantApprovalApi";

import "./UserManagement.css";
import PageHeader from "@/shared/components/ui/PageHeader";
import Button from "@/shared/components/ui/Button";

const PAGE_SIZE = 5;
const REJECTION_REASONS = [
  "Business information is incomplete",
  "Business details are inconsistent",
  "Contact details could not be verified",
  "Duplicate merchant application",
  "Other",
];

const USER_ROLES = [
  "CUSTOMER",
  "MERCHANT",
  "COURIER",
  "DISPATCHER",
  "ADMIN",
];

const USER_STATUSES = [
  "ACTIVE",
  "SUSPENDED",
  "PENDING_APPROVAL",
];

const MERCHANT_STATUSES = [
  "PENDING",
  "APPROVED",
  "REJECTED",
];

function normalize(value) {
  return String(value ?? "").trim().toUpperCase();
}

function normalizeRole(value) {
  return normalize(value).replace(/^ROLE_/, "");
}

function display(value) {
  return value ? String(value).replaceAll("_", " ") : "—";
}

function formatDate(value) {
  if (!value) return "—";

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleDateString();
}

function StatusBadge({ status }) {
  const value = normalize(status);

  return (
    <span className={`um-status um-status--${value.toLowerCase()}`}>
      {display(value)}
    </span>
  );
}

function SummaryCard({ title, value, variant = "navy" }) {
  return (
    <div className={`um-summary-card um-summary-card--${variant}`}>
      <span className="um-summary-label">{title}</span>
      <strong className="um-summary-value">{value}</strong>
    </div>
  );
}

function Pagination({ page, totalItems, onPageChange }) {
  const totalPages = Math.ceil(totalItems / PAGE_SIZE);

  if (totalPages <= 1) return null;

  const firstItem = (page - 1) * PAGE_SIZE + 1;
  const lastItem = Math.min(page * PAGE_SIZE, totalItems);

  return (
    <nav className="um-pagination" aria-label="Table pagination">
      <span className="um-pagination-info">
        Showing {firstItem}–{lastItem} of {totalItems}
      </span>

      <div className="um-pagination-controls">
        <button
          type="button"
          className="um-page-button"
          disabled={page === 1}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </button>

        {Array.from(
          { length: totalPages },
          (_, index) => index + 1
        ).map((number) => (
          <button
            type="button"
            key={number}
            className={`um-page-button ${
              page === number ? "um-page-button--active" : ""
            }`}
            aria-label={`Page ${number}`}
            aria-current={page === number ? "page" : undefined}
            onClick={() => onPageChange(number)}
          >
            {number}
          </button>
        ))}

        <button
          type="button"
          className="um-page-button"
          disabled={page === totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </button>
      </div>
    </nav>
  );
}

function EmptyState({ title, description }) {
  return (
    <div className="um-empty">
      <div className="um-empty-symbol" aria-hidden="true">
        —
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

function DetailRow({ label, value }) {
  return (
    <div className="um-detail-row">
      <span>{label}</span>
      <strong>{value === null || value === undefined || value === "" ? "—" : value}</strong>
    </div>
  );
}

function Modal({ title, children, onClose, busy = false }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const previousFocus = document.activeElement;
    const dialog = dialogRef.current;
    dialog.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    function trapFocus(event) {
      if (event.key !== "Tab") return;
      const controls = [...dialog.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [href], [tabindex="0"]')];
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (!first) {
        event.preventDefault();
        dialog.focus();
      } else if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog)) {
        event.preventDefault();
        first.focus();
      }
    }
    dialog.addEventListener("keydown", trapFocus);
    return () => {
      dialog.removeEventListener("keydown", trapFocus);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus();
    };
  }, []);

  useEffect(() => {
    function handleEscape(event) {
      if (event.key === "Escape" && !busy) {
        onClose();
      }
    }

    window.addEventListener("keydown", handleEscape);

    return () => {
      window.removeEventListener("keydown", handleEscape);
    };
  }, [onClose, busy]);

  return (
    <div
      className="um-modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) {
          onClose();
        }
      }}
    >
      <div
        className="um-modal"
        ref={dialogRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="um-modal-header">
          <h2>{title}</h2>

          <button
            type="button"
            className="um-close-button"
            onClick={onClose}
            disabled={busy}
            aria-label="Close dialog"
          >
            ×
          </button>
        </div>

        <div className="um-modal-content">{children}</div>
      </div>
    </div>
  );
}

export default function UserManagement() {
  // Registered users and merchant applications use separate APIs.
  const [users, setUsers] = useState([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState("");

  const [merchants, setMerchants] = useState([]);
  const [merchantsLoading, setMerchantsLoading] = useState(true);
  const [merchantsError, setMerchantsError] = useState("");

  // Tabs, pagination and filters.
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get("tab") === "merchants" ? "merchants" : "users";
  function setActiveTab(tab) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (tab === "merchants") next.set("tab", "merchants");
      else next.delete("tab");
      return next;
    });
  }
  const [userPage, setUserPage] = useState(1);
  const [merchantPage, setMerchantPage] = useState(1);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [userStatusFilter, setUserStatusFilter] = useState("ALL");
  const [merchantStatusFilter, setMerchantStatusFilter] = useState("ALL");

  // Modal and action state.
  const [selectedItem, setSelectedItem] = useState(null);
  const [modalType, setModalType] = useState(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [actionNotes, setActionNotes] = useState("");
  const [rejectionReason, setRejectionReason] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const currentUserId = useMemo(() => {
    try {
      const savedUser = localStorage.getItem("swiftdrop_user");
      return savedUser ? JSON.parse(savedUser)?.id : null;
    } catch {
      return null;
    }
  }, []);

  const loadUsers = useCallback(async () => {
    try {
      const data = await getAllUsers();
      setUsers(data);
      setUsersError("");
    } catch (error) {
      setUsersError(
        error?.message || "Unable to retrieve registered users."
      );
    } finally {
      setUsersLoading(false);
    }
  }, []);

  const loadMerchants = useCallback(async () => {
    try {
      const data = await getMerchantApplications();
      setMerchants(data);
      setMerchantsError("");
    } catch (error) {
      setMerchantsError(
        error?.message || "Unable to retrieve merchant applications."
      );
    } finally {
      setMerchantsLoading(false);
    }
  }, []);

  useEffect(() => {
    // Initial loading state is already true. Updates in this loader follow awaited API requests.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadUsers();
    loadMerchants();
  }, [loadUsers, loadMerchants]);

  const filteredUsers = useMemo(() => {
    const term = search.trim().toLowerCase();

    return users.filter((user) => {
      const searchable = [
        user.fullName,
        user.email,
        user.phone,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return (
        (!term || searchable.includes(term)) &&
        (roleFilter === "ALL" ||
          normalizeRole(user.role) === roleFilter) &&
        (userStatusFilter === "ALL" ||
          normalize(user.status) === userStatusFilter)
      );
    });
  }, [users, search, roleFilter, userStatusFilter]);

  const filteredMerchants = useMemo(() => {
    const term = search.trim().toLowerCase();

    return merchants.filter((merchant) => {
      const searchable = [
        merchant.businessName,
        merchant.registrationNumber,
        merchant.contactPhone,
        merchant.businessAddress,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return (
        (!term || searchable.includes(term)) &&
        (merchantStatusFilter === "ALL" ||
          normalize(merchant.registrationStatus) === merchantStatusFilter)
      );
    });
  }, [merchants, search, merchantStatusFilter]);

  const userPageCount = Math.max(
    1,
    Math.ceil(filteredUsers.length / PAGE_SIZE)
  );

  const merchantPageCount = Math.max(
    1,
    Math.ceil(filteredMerchants.length / PAGE_SIZE)
  );

  const safeUserPage = Math.min(userPage, userPageCount);
  const safeMerchantPage = Math.min(merchantPage, merchantPageCount);

  const paginatedUsers = filteredUsers.slice(
    (safeUserPage - 1) * PAGE_SIZE,
    safeUserPage * PAGE_SIZE
  );

  const paginatedMerchants = filteredMerchants.slice(
    (safeMerchantPage - 1) * PAGE_SIZE,
    safeMerchantPage * PAGE_SIZE
  );

  const userCounts = {
    total: users.length,
    active: users.filter(
      (user) => normalize(user.status) === "ACTIVE"
    ).length,
    suspended: users.filter(
      (user) => normalize(user.status) === "SUSPENDED"
    ).length,
  };

  const merchantCounts = {
    pending: merchants.filter(
      (merchant) =>
        normalize(merchant.registrationStatus) === "PENDING"
    ).length,
    approved: merchants.filter(
      (merchant) =>
        normalize(merchant.registrationStatus) === "APPROVED"
    ).length,
    rejected: merchants.filter(
      (merchant) =>
        normalize(merchant.registrationStatus) === "REJECTED"
    ).length,
  };

  function changeTab(tab) {
    setActiveTab(tab);
    setUserPage(1);
    setMerchantPage(1);
    setSearch("");
    setRoleFilter("ALL");
    setUserStatusFilter("ALL");
    setMerchantStatusFilter("ALL");
  }

  function openModal(type, item) {
    setModalType(type);
    setSelectedItem(item);
    setActionError("");
    setActionNotes("");
    setSuccessMessage("");
    setRejectionReason("");
  }

  const closeModal = useCallback(() => {
    if (busy) return;

    setModalType(null);
    setSelectedItem(null);
    setActionError("");
    setActionNotes("");
  }, [busy]);

  async function confirmAction() {
    if (!selectedItem || busy) return;

    setBusy(true);
    setActionError("");

    try {
      if (modalType === "suspend") {
        if (
          currentUserId &&
          String(selectedItem.id) === String(currentUserId)
        ) {
          throw new Error("You cannot suspend your own account.");
        }

        if (normalizeRole(selectedItem.role) === "ADMIN") {
          throw new Error(
            "Administrator accounts cannot be suspended from this page."
          );
        }

        await suspendUser(selectedItem.id);
        await loadUsers();
      } else if (modalType === "restore") {
        if (normalize(selectedItem.status) !== "SUSPENDED") {
          throw new Error(
            "Only suspended accounts can be restored."
          );
        }

        await restoreUser(selectedItem.id);
        await loadUsers();
      } else if (modalType === "approve") {
        if (
          normalize(selectedItem.registrationStatus) !== "PENDING"
        ) {
          throw new Error(
            "Only pending merchant applications can be approved."
          );
        }

        await approveMerchantApplication(
          selectedItem.id,
          actionNotes.trim()
        );

        await loadMerchants();
      } else if (modalType === "reject") {
        if (!rejectionReason) throw new Error("Select a rejection reason.");
        if (rejectionReason === "Other" && !actionNotes.trim()) {
          throw new Error("Explain the rejection reason when selecting Other.");
        }
        const reasonText = rejectionReason === "Other" ? actionNotes.trim()
          : rejectionReason + (actionNotes.trim() ? `\n\nDetails: ${actionNotes.trim()}` : "");
        if (reasonText.length > 2000) throw new Error("The rejection reason must not exceed 2000 characters.");
        if (
          !["PENDING", "APPROVED"].includes(normalize(selectedItem.registrationStatus))
        ) {
          throw new Error(
            "Only pending or approved merchant applications can be rejected."
          );
        }

        await rejectMerchantApplication(
          selectedItem.id,
          reasonText
        );

        await loadMerchants();
      } else {
        return;
      }

      const messages = {
        suspend: "User suspended successfully.",
        restore: "User reactivated successfully.",
        approve: "Merchant application approved.",
        reject: "Merchant application rejected.",
      };
      setSuccessMessage(messages[modalType]);
      setModalType(null);
      setSelectedItem(null);
      setActionError("");
      setActionNotes("");
    } catch (error) {
      setActionError(
        error?.message || "The action failed. Please try again."
      );
    } finally {
      setBusy(false);
    }
  }

  const isUsersTab = activeTab === "users";

  return (
    <div className="um-page">
      <div className="um-container">
        <PageHeader
          title="User Management"
          subtitle="Manage registered users and review merchant applications."
          action={<Button to="/admin/reports" variant="secondary">Delivery reports</Button>}
        />
        {successMessage && <p className="um-success" role="status">{successMessage}</p>}

        <div
          className="um-tabs"
          role="group"
          aria-label="Management sections"
        >
          <button
            type="button"
            aria-pressed={isUsersTab}
            className={`um-tab ${
              isUsersTab ? "um-tab--active" : ""
            }`}
            onClick={() => changeTab("users")}
          >
            All Users
          </button>

          <button
            type="button"
            aria-pressed={!isUsersTab}
            className={`um-tab ${
              !isUsersTab ? "um-tab--active" : ""
            }`}
            onClick={() => changeTab("merchants")}
          >
            Merchant Approvals

            {merchantCounts.pending > 0 && (
              <span className="um-tab-count">
                {merchantCounts.pending}
              </span>
            )}
          </button>
        </div>

        {isUsersTab ? (
          <>
            <section
              className="um-summary-grid"
              aria-label="User summary"
            >
              <SummaryCard
                title="Total Users"
                value={usersLoading || usersError ? "—" : userCounts.total}
              />
              <SummaryCard
                title="Active Users"
                value={usersLoading || usersError ? "—" : userCounts.active}
                variant="green"
              />
              <SummaryCard
                title="Suspended Users"
                value={usersLoading || usersError ? "—" : userCounts.suspended}
                variant="red"
              />
            </section>

            <section className="um-panel">
              <div className="um-panel-header">
                <div>
                  <h2>Registered Users</h2>
                  <p>
                    View account details and manage account access.
                  </p>
                </div>

                {!usersLoading && !usersError && (
                  <span className="um-result-count">
                    {filteredUsers.length} results
                  </span>
                )}
              </div>

              <div className="um-filters">
                <label className="um-field um-search-field">
                  <span>Search Users</span>
                  <input
                    type="search"
                    placeholder="Search name, email or phone"
                    value={search}
                    onChange={(event) => {
                      setSearch(event.target.value);
                      setUserPage(1);
                    }}
                  />
                </label>

                <label className="um-field">
                  <span>Role</span>
                  <select
                    value={roleFilter}
                    onChange={(event) => {
                      setRoleFilter(event.target.value);
                      setUserPage(1);
                    }}
                  >
                    <option value="ALL">All Roles</option>

                    {USER_ROLES.map((role) => (
                      <option key={role} value={role}>
                        {display(role)}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="um-field">
                  <span>Status</span>
                  <select
                    value={userStatusFilter}
                    onChange={(event) => {
                      setUserStatusFilter(event.target.value);
                      setUserPage(1);
                    }}
                  >
                    <option value="ALL">All Statuses</option>

                    {USER_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {display(status)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {usersLoading ? (
                <Spinner label="Loading users..." />
              ) : usersError ? (
                <div className="um-fetch-error" role="alert">
                  <p>{usersError}</p>
                  <button
                    type="button"
                    className="um-button um-button--primary"
                    onClick={() => {
                      setUsersLoading(true);
                      setUsersError("");
                      loadUsers();
                    }}
                  >
                    Try Again
                  </button>
                </div>
              ) : filteredUsers.length === 0 ? (
                <EmptyState
                  title={
                    users.length === 0
                      ? "No Users Found"
                      : "No Matching Users"
                  }
                  description={
                    users.length === 0
                      ? "No registered users were returned by the backend."
                      : "Try changing your search or filters."
                  }
                />
              ) : (
                <div className="um-table-scroll" role="region" aria-label="Management table" tabIndex={0}>
                  <table className="um-table">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Phone</th>
                        <th>Role</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>

                    <tbody>
                      {paginatedUsers.map((user) => {
                        const status = normalize(user.status);
                        const isAdmin =
                          normalizeRole(user.role) === "ADMIN";
                        const isSelf =
                          currentUserId &&
                          String(user.id) === String(currentUserId);

                        return (
                          <tr key={user.id}>
                            <td>
                              <strong>{user.fullName || "—"}</strong>
                            </td>
                            <td>{user.email || "—"}</td>
                            <td>{user.phone || "—"}</td>
                            <td>
                              {display(normalizeRole(user.role))}
                            </td>
                            <td>
                              <StatusBadge status={user.status} />
                            </td>
                            <td>
                              <div className="um-actions">
                                <button
                                  type="button"
                                  className="um-button um-button--outline"
                                  onClick={() =>
                                    openModal("userDetails", user)
                                  }
                                >
                                  View
                                </button>

                                {status === "ACTIVE" &&
                                  !isAdmin &&
                                  !isSelf && (
                                    <button
                                      type="button"
                                      className="um-button um-button--danger-outline"
                                      onClick={() =>
                                        openModal("suspend", user)
                                      }
                                    >
                                      Suspend
                                    </button>
                                  )}

                                {status === "SUSPENDED" &&
                                  !isAdmin && (
                                    <button
                                      type="button"
                                      className="um-button um-button--outline"
                                      onClick={() =>
                                        openModal("restore", user)
                                      }
                                    >
                                      Restore
                                    </button>
                                  )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>

                  <Pagination
                    page={safeUserPage}
                    totalItems={filteredUsers.length}
                    onPageChange={setUserPage}
                  />
                </div>
              )}
            </section>
          </>
        ) : (
          <>
            <section
              className="um-summary-grid"
              aria-label="Merchant summary"
            >
              <SummaryCard
                title="Pending Applications"
                value={merchantsLoading || merchantsError ? "—" : merchantCounts.pending}
                variant="orange"
              />
              <SummaryCard
                title="Approved Merchants"
                value={merchantsLoading || merchantsError ? "—" : merchantCounts.approved}
                variant="green"
              />
              <SummaryCard
                title="Rejected Applications"
                value={merchantsLoading || merchantsError ? "—" : merchantCounts.rejected}
                variant="red"
              />
            </section>

            <section className="um-panel">
              <div className="um-panel-header">
                <div>
                  <h2>Merchant Applications</h2>
                  <p>
                    Review business registrations and approve or reject
                    pending applications.
                  </p>
                </div>

                {!merchantsLoading && !merchantsError && (
                  <span className="um-result-count">
                    {filteredMerchants.length} results
                  </span>
                )}
              </div>

              <div className="um-filters">
                <label className="um-field um-search-field">
                  <span>Search Merchants</span>
                  <input
                    type="search"
                    placeholder="Search business name, registration or phone"
                    value={search}
                    onChange={(event) => {
                      setSearch(event.target.value);
                      setMerchantPage(1);
                    }}
                  />
                </label>

                <label className="um-field">
                  <span>Approval Status</span>
                  <select
                    value={merchantStatusFilter}
                    onChange={(event) => {
                      setMerchantStatusFilter(event.target.value);
                      setMerchantPage(1);
                    }}
                  >
                    <option value="ALL">All Applications</option>

                    {MERCHANT_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {display(status)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {merchantsLoading ? (
                <Spinner label="Loading merchant applications..." />
              ) : merchantsError ? (
                <div className="um-fetch-error" role="alert">
                  <p>{merchantsError}</p>
                  <button
                    type="button"
                    className="um-button um-button--primary"
                    onClick={() => {
                      setMerchantsLoading(true);
                      setMerchantsError("");
                      loadMerchants();
                    }}
                  >
                    Try Again
                  </button>
                </div>
              ) : filteredMerchants.length === 0 ? (
                <EmptyState
                  title={
                    merchants.length === 0
                      ? "No Merchant Applications Found"
                      : "No Matching Merchants"
                  }
                  description={
                    merchants.length === 0
                      ? "No merchant applications were returned by the backend."
                      : "Try changing your search or filters."
                  }
                />
              ) : (
                <div className="um-table-scroll" role="region" aria-label="Management table" tabIndex={0}>
                  <table className="um-table">
                    <thead>
                      <tr>
                        <th>Business Name</th>
                        <th>Registration Number</th>
                        <th>Phone</th>
                        <th>Registered</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>

                    <tbody>
                      {paginatedMerchants.map((merchant) => {
                        const status = normalize(
                          merchant.registrationStatus
                        );

                        return (
                          <tr key={merchant.id}>
                            <td>
                              <strong>
                                {merchant.businessName || "—"}
                              </strong>
                            </td>

                            <td>
                              {merchant.registrationNumber || "—"}
                            </td>

                            <td>
                              {merchant.contactPhone || "—"}
                            </td>

                            <td>
                              {formatDate(merchant.createdAt)}
                            </td>

                            <td>
                              <StatusBadge
                                status={merchant.registrationStatus}
                              />
                            </td>

                            <td>
                              <div className="um-actions">
                                <button
                                  type="button"
                                  className="um-button um-button--outline"
                                  onClick={() =>
                                    openModal(
                                      "merchantDetails",
                                      merchant
                                    )
                                  }
                                >
                                  Review
                                </button>

                                {status === "PENDING" && (
                                  <>
                                    <button
                                      type="button"
                                      className="um-button um-button--success"
                                      onClick={() =>
                                        openModal("approve", merchant)
                                      }
                                    >
                                      Approve
                                    </button>
                                  </>
                                )}
                                {["PENDING", "APPROVED"].includes(status) && (
                                    <button
                                      type="button"
                                      className="um-button um-button--danger-outline"
                                      onClick={() =>
                                        openModal("reject", merchant)
                                      }
                                    >
                                      Reject
                                    </button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>

                  <Pagination
                    page={safeMerchantPage}
                    totalItems={filteredMerchants.length}
                    onPageChange={setMerchantPage}
                  />
                </div>
              )}
            </section>
          </>
        )}
      </div>

      {modalType && selectedItem && (
        <Modal
          title={
            {
              userDetails: "User Details",
              merchantDetails: "Merchant Application",
              suspend: "Suspend User",
              restore: "Restore User",
              approve: "Approve Merchant",
              reject: "Reject Merchant",
            }[modalType]
          }
          onClose={closeModal}
          busy={busy}
        >
          {modalType === "userDetails" && (
            <div className="um-details">
              <DetailRow
                label="Full Name"
                value={selectedItem.fullName}
              />
              <DetailRow
                label="Email"
                value={selectedItem.email}
              />
              <DetailRow
                label="Phone"
                value={selectedItem.phone}
              />
              <DetailRow
                label="Role"
                value={display(normalizeRole(selectedItem.role))}
              />
              <DetailRow
                label="Status"
                value={display(selectedItem.status)}
              />
              <DetailRow
                label="Login Provider"
                value={display(selectedItem.provider)}
              />
              <DetailRow
                label="Email Verified"
                value={selectedItem.emailVerified ? "Yes" : "No"}
              />

              <div className="um-modal-footer">
                <button
                  type="button"
                  className="um-button um-button--outline"
                  onClick={closeModal}
                >
                  Close
                </button>
              </div>
            </div>
          )}

          {modalType === "merchantDetails" && (
            <div className="um-details">
              <DetailRow
                label="Business Name"
                value={selectedItem.businessName}
              />
              <DetailRow
                label="Registration Number"
                value={selectedItem.registrationNumber}
              />
              <DetailRow
                label="Contact Phone"
                value={selectedItem.contactPhone}
              />
              <DetailRow
                label="Business Address"
                value={selectedItem.businessAddress}
              />
              <DetailRow
                label="Registration Status"
                value={display(selectedItem.registrationStatus)}
              />
              <DetailRow
                label="Registered"
                value={formatDate(selectedItem.createdAt)}
              />
              <DetailRow label={normalize(selectedItem.registrationStatus) === "REJECTED" ? "Rejection Reason" : "Review Notes"}
                value={selectedItem.approvalNotes} />
              <DetailRow
                label="Webhook Configured"
                value={
                  selectedItem.webhookConfigured ? "Yes" : "No"
                }
              />

              <div className="um-modal-footer">
                <button
                  type="button"
                  className="um-button um-button--outline"
                  onClick={closeModal}
                >
                  Close
                </button>
              </div>
            </div>
          )}

          {["suspend", "restore", "approve", "reject"].includes(
            modalType
          ) && (
            <>
              <p className="um-confirm-text">
                {modalType === "suspend" &&
                  "Are you sure you want to suspend this user? Their access to SwiftDrop should be restricted."}

                {modalType === "restore" &&
                  "Are you sure you want to restore this suspended user's account?"}

                {modalType === "approve" &&
                  `Approve ${selectedItem.businessName}? Its registration status will change from PENDING to APPROVED.`}

                {modalType === "reject" &&
                  `Reject ${selectedItem.businessName}? Its status will change from ${normalize(selectedItem.registrationStatus)} to REJECTED and merchant access will be blocked.`}
              </p>

              {modalType === "reject" && (
                <label className="um-field">
                  <span>Rejection Reason (Required)</span>
                  <select required value={rejectionReason} disabled={busy}
                    onChange={(event) => { setRejectionReason(event.target.value); setActionError(""); }}>
                    <option value="">Select a reason</option>
                    {REJECTION_REASONS.map((reason) => <option key={reason} value={reason}>{reason}</option>)}
                  </select>
                </label>
              )}
              {["approve", "reject"].includes(modalType) && (
                <>
                <label className="um-field">
                  <span>{modalType === "reject"
                    ? rejectionReason === "Other" ? "Explain the Reason (Required)" : "Additional Details (Optional)"
                    : "Admin Notes (Optional)"}</span>
                  <textarea
                    rows={3}
                    required={modalType === "reject" && rejectionReason === "Other"}
                    maxLength={modalType === "reject" && rejectionReason !== "Other" ? 1800 : 2000}
                    value={actionNotes}
                    placeholder="Enter a reason or review notes"
                    onChange={(event) =>
                      setActionNotes(event.target.value)
                    }
                    disabled={busy}
                  />
                </label>
                {modalType === "reject" && <small>This reason will be included in the email to the applicant.</small>}
                </>
              )}

              {actionError && (
                <p className="um-error" role="alert">{actionError}</p>
              )}

              <div className="um-modal-footer">
                <button
                  type="button"
                  className="um-button um-button--outline"
                  onClick={closeModal}
                  disabled={busy}
                >
                  Cancel
                </button>

                <button
                  type="button"
                  className={`um-button ${
                    ["suspend", "reject"].includes(modalType)
                      ? "um-button--danger"
                      : "um-button--primary"
                  }`}
                  onClick={confirmAction}
                  disabled={busy}
                >
                  {busy ? "Processing..." : "Confirm"}
                </button>
              </div>
            </>
          )}
        </Modal>
      )}
    </div>
  );
}
