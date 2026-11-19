import { useEffect, useRef, useState } from "react";
import Button from "@/shared/components/ui/Button";
import Spinner from "@/shared/components/ui/Spinner";
import { getReportSnapshot, getReportSnapshots, saveReportSnapshot, REPORT_SNAPSHOT_PAGE_SIZE } from "../services/reportApi";
import { reportPeriod } from "../services/reportPeriods";

function savedTime(value) {
  return new Intl.DateTimeFormat("en-LK", {
    dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Colombo",
  }).format(new Date(value));
}

export default function ReportSnapshots({ startDate, endDate, successPeriod, canSave, activeSnapshot, onOpen }) {
  const [result, setResult] = useState({ items: [], total: 0, size: REPORT_SNAPSHOT_PAGE_SIZE });
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const pendingSave = useRef(null);
  const [savedPeriod, setSavedPeriod] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const data = await getReportSnapshots(page, { signal: controller.signal }, reportPeriod(savedPeriod));
        if (!controller.signal.aborted) setResult(data);
      } catch (error) {
        if (!controller.signal.aborted) setError(error.message || "Unable to load saved reports.");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [page, revision, savedPeriod]);

  function refresh() {
    setLoading(true);
    setError("");
    setRevision((value) => value + 1);
  }

  function changePage(next) {
    setLoading(true);
    setError("");
    setPage(next);
  }

  async function save() {
    setBusy(true);
    setError("");
    setMessage("");
    if (!pendingSave.current || pendingSave.current.startDate !== startDate || pendingSave.current.endDate !== endDate
        || JSON.stringify(pendingSave.current.successPeriod) !== JSON.stringify(successPeriod)) {
      pendingSave.current = { id: crypto.randomUUID(), startDate, endDate, successPeriod };
    }
    try {
      const snapshot = await saveReportSnapshot(pendingSave.current);
      pendingSave.current = null;
      onOpen(snapshot);
      setMessage("Report saved. You are viewing its frozen values.");
      setPage(0);
      refresh();
    } catch (error) {
      setError(error.message || "Unable to save this report. You can retry safely.");
    } finally {
      setBusy(false);
    }
  }

  async function open(id) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      onOpen(await getReportSnapshot(id));
    } catch (error) {
      setError(error.message || "Unable to open this saved report.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="reports-panel reports-snapshots" aria-label="Saved reports">
      <div className="reports-chart-header">
        <div>
          <h2>Saved Reports</h2>
          <p className="reports-note">Save a dated copy of the figures above. Open a saved report to view its summary, charts, and report tables at the top of this page.</p>
        </div>
        <div className="reports-chart-actions">
          <select className="reports-period-select" aria-label="Filter reports by saved date"
            value={savedPeriod} disabled={busy} onChange={(event) => {
              setLoading(true);
              setError("");
              setPage(0);
              setSavedPeriod(Number(event.target.value));
            }}>
            <option value={0}>Saved: All Time</option>
            <option value={7}>Saved: Last 7 Days</option>
            <option value={30}>Saved: Last 30 Days</option>
          </select>
          <Button variant="secondary" onClick={refresh} disabled={loading || busy}>Refresh saved reports</Button>
          <Button className="reports-save-snapshot" onClick={save} disabled={!canSave || busy || Boolean(activeSnapshot)}>
            {busy ? "Please wait..." : "Save current report"}
          </Button>
        </div>
      </div>
      {message && <p className="reports-snapshot-message" role="status">{message}</p>}
      {error && <p className="reports-error" role="alert">{error}</p>}
      {loading ? <Spinner label="Loading saved reports..." /> : result.items.length === 0 ? (
        !error && <p className="reports-note">{savedPeriod ? "No reports saved in this period." : "No saved reports yet."}</p>
      ) : (
        <>
          <div className="reports-table-wrapper" tabIndex={0} role="region" aria-label="Saved reports table">
            <table className="reports-table">
              <thead><tr><th>Saved (Sri Lanka)</th><th>Daily reporting period</th><th>Action</th></tr></thead>
              <tbody>{result.items.map((snapshot) => (
                <tr key={snapshot.id}>
                  <td>{savedTime(snapshot.createdAt)}</td>
                  <td>{snapshot.startDate} to {snapshot.endDate}</td>
                  <td><Button variant="secondary" size="sm" onClick={() => open(snapshot.id)} disabled={busy}>
                    Open report
                  </Button></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <nav className="reports-snapshot-pagination" aria-label="Saved report pagination">
            <span>Showing {page * result.size + 1}–{page * result.size + result.items.length} of {result.total} saved {result.total === 1 ? "report" : "reports"}</span>
            <Button variant="secondary" size="sm" onClick={() => changePage(page - 1)} disabled={page === 0 || busy}>Previous</Button>
            <Button variant="secondary" size="sm" onClick={() => changePage(page + 1)} disabled={(page + 1) * result.size >= result.total || busy}>Next</Button>
          </nav>
        </>
      )}
    </section>
  );
}
