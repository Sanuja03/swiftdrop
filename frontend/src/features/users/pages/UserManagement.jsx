import { useCallback, useEffect, useMemo, useState } from "react";
import {
  getAllUsers,
  suspendUser,
  restoreUser,
  approveMerchant,
} from "../services/userManagementApi";
import "./UserManagement.css";

const PAGE_SIZE = 5;

const USER_ROLES = [
  "CUSTOMER",
  "MERCHANT",
  "COURIER",
  "DISPATCHER",
  "ADMIN",
];

const USER_STATUSES = ["ACTIVE", "SUSPENDED", "PENDING_APPROVAL"];

const MERCHANT_STATUSES = [
  "PENDING_APPROVAL",
  "ACTIVE",
  "SUSPENDED",
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
        <button type="button" className="um-page-button"
          disabled={page === 1} onClick={() => onPageChange(page - 1)}>
          Previous
        </button>
        {Array.from({ length: totalPages }, (_, index) => index + 1).map((number) => (
          <button type="button" key={number}
            className={`um-page-button ${page === number ? "um-page-button--active" : ""}`}
            aria-label={`Page ${number}`} aria-current={page === number ? "page" : undefined}
            onClick={() => onPageChange(number)}>
            {number}
          </button>
        ))}
        <button type="button" className="um-page-button"
          disabled={page === totalPages} onClick={() => onPageChange(page + 1)}>
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
      <strong>{value ?? "—"}</strong>
    </div>
  );
}

function Modal({ title, children, onClose, busy = false }) {
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
  // Registered users are retrieved from the admin API.
  const [users, setUsers] = useState([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState("");

  // Tabs and filters.
  const [activeTab, setActiveTab] = useState("users");
  const [userPage, setUserPage] = useState(1);
  const [merchantPage, setMerchantPage] = useState(1);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [userStatusFilter, setUserStatusFilter] = useState("ALL");
  const [merchantStatusFilter, setMerchantStatusFilter] = useState("ALL");

  // Modal state.
  const [selectedItem, setSelectedItem] = useState(null);
  const [modalType, setModalType] = useState(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");

  // Identify the logged-in user when available.
  // The backend must enforce authorization independently.
  const currentUserId = useMemo(() => {
    try {
      const savedUser = localStorage.getItem("swiftdrop_user");
      return savedUser ? JSON.parse(savedUser)?.id : null;
    } catch {
      return null;
    }
  }, []);

  // Retrieve all users from Spring Boot.
  const loadUsers = useCallback(async () => {
    setUsersLoading(true);
    setUsersError("");

    try {
      const data = await getAllUsers();
      setUsers(data);
    } catch (error) {
      setUsersError(
        error?.message || "Unable to retrieve registered users."
      );
    } finally {
      setUsersLoading(false);
    }
  }, []);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  // Filter registered users.
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

      const matchesSearch =
        !term || searchable.includes(term);

      const matchesRole =
        roleFilter === "ALL" ||
        normalizeRole(user.role) === roleFilter;

      const matchesStatus =
        userStatusFilter === "ALL" ||
        normalize(user.status) === userStatusFilter;

      return matchesSearch && matchesRole && matchesStatus;
    });
  }, [users, search, roleFilter, userStatusFilter]);

  // Derive merchant accounts from the real users API.
  const merchants = useMemo(() => {
    return users.filter(
      (user) => normalizeRole(user.role) === "MERCHANT"
    );
  }, [users]);

  // Filter merchant accounts.
  const filteredMerchants = useMemo(() => {
    const term = search.trim().toLowerCase();

    return merchants.filter((merchant) => {
      const searchable = [
        merchant.fullName,
        merchant.email,
        merchant.phone,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      const matchesSearch =
        !term || searchable.includes(term);

      const matchesStatus =
        merchantStatusFilter === "ALL" ||
        normalize(merchant.status) === merchantStatusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [merchants, search, merchantStatusFilter]);

  const userPageCount = Math.max(1, Math.ceil(filteredUsers.length / PAGE_SIZE));
  const merchantPageCount = Math.max(1, Math.ceil(filteredMerchants.length / PAGE_SIZE));
  const safeUserPage = Math.min(userPage, userPageCount);
  const safeMerchantPage = Math.min(merchantPage, merchantPageCount);
  const paginatedUsers = filteredUsers.slice(
    (safeUserPage - 1) * PAGE_SIZE, safeUserPage * PAGE_SIZE
  );
  const paginatedMerchants = filteredMerchants.slice(
    (safeMerchantPage - 1) * PAGE_SIZE, safeMerchantPage * PAGE_SIZE
  );

  // User statistics.
  const userCounts = {
    total: users.length,
    active: users.filter(
      (user) => normalize(user.status) === "ACTIVE"
    ).length,
    suspended: users.filter(
      (user) => normalize(user.status) === "SUSPENDED"
    ).length,
  };

  // Merchant statistics.
  const merchantCounts = {
    pending: merchants.filter(
      (merchant) =>
        normalize(merchant.status) === "PENDING_APPROVAL"
    ).length,
    approved: merchants.filter(
      (merchant) => normalize(merchant.status) === "ACTIVE"
    ).length,
    suspended: merchants.filter(
      (merchant) => normalize(merchant.status) === "SUSPENDED"
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
  }

  const closeModal = useCallback(() => {
    if (busy) return;

    setModalType(null);
    setSelectedItem(null);
    setActionError("");
  }, [busy]);

  function closeModalAfterSuccess() {
    setModalType(null);
    setSelectedItem(null);
    setActionError("");
  }

  // Confirm account-management actions.
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
          throw new Error(
            "You cannot suspend your own account."
          );
        }

        if (normalizeRole(selectedItem.role) === "ADMIN") {
          throw new Error(
            "Administrator accounts cannot be suspended from this page."
          );
        }

        await suspendUser(selectedItem.id);
      } else if (modalType === "restore") {
        if (normalize(selectedItem.status) !== "SUSPENDED") {
          throw new Error(
            "Only suspended accounts can be restored."
          );
        }

        await restoreUser(selectedItem.id);
      } else if (modalType === "approve") {
        if (normalizeRole(selectedItem.role) !== "MERCHANT") {
          throw new Error(
            "Only merchant accounts can be approved."
          );
        }

        if (
          normalize(selectedItem.status) !== "PENDING_APPROVAL"
        ) {
          throw new Error(
            "This merchant is not awaiting approval."
          );
        }

        await approveMerchant(selectedItem.id);
      } else {
        return;
      }

      // Refresh the user and merchant tables.
      await loadUsers();
      closeModalAfterSuccess();
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
    <main className="um-page">
      <div className="um-container">
        <header className="um-page-header">
          <h1>User Management</h1>
          <p>
            Manage registered users and review merchant applications.
          </p>
        </header>

        {/* Main tabs */}
        <div
          className="um-tabs"
          role="tablist"
          aria-label="Management sections"
        >
          <button
            type="button"
            role="tab"
            aria-selected={isUsersTab}
            className={`um-tab ${
              isUsersTab ? "um-tab--active" : ""
            }`}
            onClick={() => changeTab("users")}
          >
            All Users
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={!isUsersTab}
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
            {/* User summary cards */}
            <section
              className="um-summary-grid"
              aria-label="User summary"
            >
              <SummaryCard
                title="Total Users"
                value={usersLoading ? "—" : userCounts.total}
              />

              <SummaryCard
                title="Active Users"
                value={usersLoading ? "—" : userCounts.active}
                variant="green"
              />

              <SummaryCard
                title="Suspended Users"
                value={usersLoading ? "—" : userCounts.suspended}
                variant="red"
              />
            </section>

            {/* User table */}
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
                    onChange={(event) =>
                      { setSearch(event.target.value); setUserPage(1); }
                    }
                  />
                </label>

                <label className="um-field">
                  <span>Role</span>
                  <select
                    value={roleFilter}
                    onChange={(event) =>
                      { setRoleFilter(event.target.value); setUserPage(1); }
                    }
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
                    onChange={(event) =>
                      { setUserStatusFilter(event.target.value); setUserPage(1); }
                    }
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
                <EmptyState
                  title="Loading Users"
                  description="Retrieving registered users from SwiftDrop."
                />
              ) : usersError ? (
                <div className="um-fetch-error">
                  <p>{usersError}</p>

                  <button
                    type="button"
                    className="um-button um-button--primary"
                    onClick={loadUsers}
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
                <div className="um-table-scroll">
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
                          String(user.id) ===
                            String(currentUserId);

                        return (
                          <tr key={user.id}>
                            <td>
                              <strong>
                                {user.fullName || "—"}
                              </strong>
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
                  <Pagination page={safeUserPage} totalItems={filteredUsers.length}
                    onPageChange={setUserPage} />
                </div>
              )}
            </section>
          </>
        ) : (
          <>
            {/* Merchant summary cards */}
            <section
              className="um-summary-grid"
              aria-label="Merchant summary"
            >
              <SummaryCard
                title="Pending Applications"
                value={
                  usersLoading ? "—" : merchantCounts.pending
                }
                variant="orange"
              />

              <SummaryCard
                title="Approved Merchants"
                value={
                  usersLoading ? "—" : merchantCounts.approved
                }
                variant="green"
              />

              <SummaryCard
                title="Suspended Merchants"
                value={
                  usersLoading ? "—" : merchantCounts.suspended
                }
                variant="red"
              />
            </section>

            {/* Merchant applications table */}
            <section className="um-panel">
              <div className="um-panel-header">
                <div>
                  <h2>Merchant Applications</h2>
                  <p>
                    Review merchant accounts and approve pending
                    registrations.
                  </p>
                </div>

                {!usersLoading && !usersError && (
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
                    placeholder="Search merchant name, email or phone"
                    value={search}
                    onChange={(event) =>
                      { setSearch(event.target.value); setMerchantPage(1); }
                    }
                  />
                </label>

                <label className="um-field">
                  <span>Approval Status</span>
                  <select
                    value={merchantStatusFilter}
                    onChange={(event) =>
                      { setMerchantStatusFilter(event.target.value); setMerchantPage(1); }
                    }
                  >
                    <option value="ALL">
                      All Merchant Accounts
                    </option>

                    {MERCHANT_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {display(status)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {usersLoading ? (
                <EmptyState
                  title="Loading Merchants"
                  description="Retrieving merchant accounts from SwiftDrop."
                />
              ) : usersError ? (
                <div className="um-fetch-error">
                  <p>{usersError}</p>

                  <button
                    type="button"
                    className="um-button um-button--primary"
                    onClick={loadUsers}
                  >
                    Try Again
                  </button>
                </div>
              ) : filteredMerchants.length === 0 ? (
                <EmptyState
                  title={
                    merchants.length === 0
                      ? "No Merchant Accounts Found"
                      : "No Matching Merchants"
                  }
                  description={
                    merchants.length === 0
                      ? "No merchant accounts were returned by the backend."
                      : "Try changing your search or filters."
                  }
                />
              ) : (
                <div className="um-table-scroll">
                  <table className="um-table">
                    <thead>
                      <tr>
                        <th>Merchant</th>
                        <th>Email</th>
                        <th>Phone</th>
                        <th>Registered</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>

                    <tbody>
                      {paginatedMerchants.map((merchant) => {
                        const status = normalize(merchant.status);

                        const submitted =
                          merchant.createdAt ??
                          merchant.created_at;

                        const submittedDate = submitted
                          ? new Date(submitted)
                          : null;

                        return (
                          <tr key={merchant.id}>
                            <td>
                              <strong>
                                {merchant.fullName || "—"}
                              </strong>

                            </td>

                            <td>{merchant.email || "—"}</td>
                            <td>
                              {merchant.phone || "—"}
                            </td>

                            <td>
                              {submittedDate &&
                              !Number.isNaN(
                                submittedDate.getTime()
                              )
                                ? submittedDate.toLocaleDateString()
                                : "—"}
                            </td>

                            <td>
                              <StatusBadge
                                status={merchant.status}
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

                                {status === "PENDING_APPROVAL" && (
                                  <>
                                    <button
                                      type="button"
                                      className="um-button um-button--success"
                                      onClick={() =>
                                        openModal(
                                          "approve",
                                          merchant
                                        )
                                      }
                                    >
                                      Approve
                                    </button>

                                    <button
                                      type="button"
                                      className="um-button um-button--danger-outline"
                                      disabled
                                      title="Merchant rejection is not supported by the backend yet"
                                    >
                                      Reject
                                    </button>
                                  </>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                  <Pagination page={safeMerchantPage} totalItems={filteredMerchants.length}
                    onPageChange={setMerchantPage} />
                </div>
              )}
            </section>
          </>
        )}

      </div>

      {/* Details and confirmation dialogs */}
      {modalType && selectedItem && (
        <Modal
          title={
            {
              userDetails: "User Details",
              merchantDetails: "Merchant Application",
              suspend: "Suspend User",
              restore: "Restore User",
              approve: "Approve Merchant",
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
                value={display(
                  normalizeRole(selectedItem.role)
                )}
              />

              <DetailRow
                label="Status"
                value={display(selectedItem.status)}
              />

              <DetailRow
                label="Login Provider"
                value={display(selectedItem.provider)}
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
                label="Merchant Name"
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
                value={display(
                  normalizeRole(selectedItem.role)
                )}
              />

              <DetailRow
                label="Account Status"
                value={display(selectedItem.status)}
              />

              <DetailRow
                label="Login Provider"
                value={display(selectedItem.provider)}
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

          {["suspend", "restore", "approve"].includes(
            modalType
          ) && (
            <>
              <p className="um-confirm-text">
                {modalType === "suspend" &&
                  "Are you sure you want to suspend this user? Their access to SwiftDrop should be restricted."}

                {modalType === "restore" &&
                  "Are you sure you want to restore this suspended user's account?"}

                {modalType === "approve" &&
                  "Are you sure you want to approve this merchant account? The account status will change from PENDING_APPROVAL to ACTIVE."}
              </p>

              {actionError && (
                <p className="um-error">{actionError}</p>
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
                    modalType === "suspend"
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
    </main>
  );
}
