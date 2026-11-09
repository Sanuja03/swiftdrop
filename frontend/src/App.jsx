import { useState } from "react";
import ReportsDashboard from "./features/reports/pages/ReportsDashboard";
import UserManagement from "./features/users/pages/UserManagement";

export default function App() {
  const [activePage, setActivePage] = useState("reports");

  return (
    <div>
      <nav
        style={{
          display: "flex",
          gap: "12px",
          padding: "12px 20px",
          borderBottom: "1px solid #e5e7eb",
        }}
      >
        <button
          type="button"
          onClick={() => setActivePage("reports")}
          disabled={activePage === "reports"}
        >
          Reports Dashboard
        </button>

        <button
          type="button"
          onClick={() => setActivePage("users")}
          disabled={activePage === "users"}
        >
          User Management
        </button>
      </nav>

      {activePage === "reports" ? (
        <ReportsDashboard />
      ) : (
        <UserManagement />
      )}
    </div>
  );
}