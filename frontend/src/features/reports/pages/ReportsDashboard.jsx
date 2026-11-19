import Spinner from "@/shared/components/ui/Spinner";

import { useEffect, useRef, useState } from "react";
import { jsPDF } from "jspdf";
import { getReportSummary, getSuccessRate, getDailyDeliveries } from "../services/reportApi";
import ReportSnapshots from "./ReportSnapshots";
import { reportPeriod as datePeriod } from "../services/reportPeriods";
import "./ReportsDashboard.css";
import PageHeader from "@/shared/components/ui/PageHeader";
import Button from "@/shared/components/ui/Button";

const TIME_ZONE = "Asia/Colombo";

// Get today's date in Sri Lankan time.
function getSriLankaToday() {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());

  const getPart = (type) =>
    parts.find((part) => part.type === type)?.value;

  return `${getPart("year")}-${getPart("month")}-${getPart("day")}`;
}

// Get the date and time when a report is generated.
function getGeneratedTime(value) {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: TIME_ZONE,
    dateStyle: "medium",
    timeStyle: "short",
  }).format(value ? new Date(value) : new Date());
}

// Format a date as YYYY-MM-DD.
function formatDate(date) {
  return [
    date.getUTCFullYear(),
    String(date.getUTCMonth() + 1).padStart(2, "0"),
    String(date.getUTCDate()).padStart(2, "0"),
  ].join("-");
}

// Prepare every date for the daily deliveries chart and report.
function buildChartDays(dailyDeliveries, numberOfDays, endDate = getSriLankaToday()) {
  const [year, month, day] = endDate
    .split("-")
    .map(Number);

  const today = new Date(Date.UTC(year, month - 1, day));

  return Array.from({ length: numberOfDays }, (_, index) => {
    const date = new Date(today);

    date.setUTCDate(
      today.getUTCDate() - (numberOfDays - 1 - index)
    );

    const dateKey = formatDate(date);

    const matchingDay = dailyDeliveries.find(
      (item) => item.deliveryDate === dateKey
    );

    return {
      date: dateKey,
      label: date.toLocaleDateString("en-GB", {
        day: "numeric",
        month: "short",
        timeZone: "UTC",
      }),
      deliveries: matchingDay?.deliveries ?? 0,
    };
  });
}

// Download report data as a CSV file.
function downloadCSV(filename, rows) {
  const csvContent = rows
    .map((row) =>
      row
        .map((value) => {
          const text = String(value ?? "");
          return `"${text.replace(/"/g, '""')}"`;
        })
        .join(",")
    )
    .join("\r\n");

  const blob = new Blob(["\uFEFF", csvContent], {
    type: "text/csv;charset=utf-8;",
  });

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = url;
  link.download = filename;

  document.body.appendChild(link);
  link.click();
  link.remove();

  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// Generate a formatted PDF report.
function createPDF(title, metadata, columns, rows, filename) {
  const pdf = new jsPDF();

  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();

  const margin = 16;
  const contentWidth = pageWidth - margin * 2;

  let y = 0;

  function addPageHeader() {
    pdf.setFillColor(15, 39, 71);
    pdf.rect(0, 0, pageWidth, 34, "F");

    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(20);
    pdf.setTextColor(255, 255, 255);
    pdf.text("SwiftDrop", margin, 16);

    pdf.setFontSize(11);
    pdf.text(title, margin, 25);

    pdf.setTextColor(30, 41, 59);
    y = 44;
  }

  function ensureSpace(height) {
    if (y + height > pageHeight - 18) {
      pdf.addPage();
      addPageHeader();
    }
  }

  addPageHeader();

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(11);
  pdf.text("Report Information", margin, y);

  y += 9;

  metadata.forEach(([label, value]) => {
    ensureSpace(7);

    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(9);
    pdf.text(`${label}:`, margin, y);

    pdf.setFont("helvetica", "normal");
    pdf.text(String(value), margin + 49, y);

    y += 7;
  });

  y += 7;

  ensureSpace(18);

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(11);
  pdf.text("Report Details", margin, y);

  y += 7;

  function drawTableHeader() {
    pdf.setFillColor(245, 247, 250);
    pdf.rect(margin, y - 5, contentWidth, 9, "F");

    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(9);
    pdf.setTextColor(15, 39, 71);

    const colWidth = contentWidth / columns.length;

    columns.forEach((column, index) => {
      pdf.text(
        String(column),
        margin + 3 + index * colWidth,
        y
      );
    });

    y += 9;

    pdf.setTextColor(30, 41, 59);
  }

  drawTableHeader();

  rows.forEach((row) => {
    if (y + 8 > pageHeight - 18) {
      pdf.addPage();
      addPageHeader();
      drawTableHeader();
    }

    const colWidth = contentWidth / columns.length;

    pdf.setFont("helvetica", "normal");
    pdf.setFontSize(9);

    row.forEach((value, index) => {
      pdf.text(
        String(value),
        margin + 3 + index * colWidth,
        y
      );
    });

    pdf.setDrawColor(226, 232, 240);

    pdf.line(
      margin,
      y + 3,
      pageWidth - margin,
      y + 3
    );

    y += 9;
  });

  const totalPages = pdf.getNumberOfPages();

  for (let page = 1; page <= totalPages; page++) {
    pdf.setPage(page);

    pdf.setFontSize(8);
    pdf.setTextColor(100, 116, 139);

    pdf.text(
      `SwiftDrop | Page ${page} of ${totalPages}`,
      margin,
      pageHeight - 9
    );
  }

  pdf.save(filename);
}

export default function ReportsDashboard() {
  const [summary, setSummary] = useState(null);
  const [dailyDeliveries, setDailyDeliveries] = useState([]);
  const [successRate, setSuccessRate] = useState(null);

  const [selectedPeriod, setSelectedPeriod] = useState(7);
  const [selectedOutcomePeriod, setSelectedOutcomePeriod] = useState(0);
  const [outcomeLoading, setOutcomeLoading] = useState(true);
  const [outcomeError, setOutcomeError] = useState("");

  const [loading, setLoading] = useState(true);
  const [chartLoading, setChartLoading] = useState(true);

  const [error, setError] = useState("");
  const [chartError, setChartError] = useState("");

  const [showDailyReport, setShowDailyReport] = useState(false);
  const [showOutcomeReport, setShowOutcomeReport] = useState(false);

  const [activeSnapshot, setActiveSnapshot] = useState(null);
  const [revision, setRevision] = useState(0);
  const reportViewRef = useRef(null);
  const [openedReportRevision, setOpenedReportRevision] = useState(0);
  const successPeriod = activeSnapshot ? activeSnapshot.data.successPeriod ?? null
    : datePeriod(selectedOutcomePeriod, getSriLankaToday());
  const outcomeStart = successPeriod?.startDate;
  const outcomeEnd = successPeriod?.endDate;
  const outcomePeriodLabel = successPeriod ? `${outcomeStart} to ${outcomeEnd}` : "All time";

  useEffect(() => {
    if (openedReportRevision > 0) {
      reportViewRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [openedReportRevision]);

  function openSnapshot(snapshot) {
    setActiveSnapshot(snapshot);
    setSummary(snapshot.data.summary);
    setSuccessRate(snapshot.data.successRate);
    setDailyDeliveries(snapshot.data.dailyDeliveries);
    setLoading(false);
    setChartLoading(false);
    setError("");
    setChartError("");
    setOutcomeLoading(false);
    setOutcomeError("");
    setShowOutcomeReport(true);
    setShowDailyReport(true);
    setOpenedReportRevision((value) => value + 1);
  }

  function returnToLive() {
    setLoading(true);
    setChartLoading(true);
    setError("");
    setChartError("");
    setActiveSnapshot(null);
    setOutcomeLoading(true);
    setOutcomeError("");
  }

  function retryReports() {
    setLoading(true);
    setChartLoading(true);
    setError("");
    setChartError("");
    setRevision((value) => value + 1);
  }

  // The overview remains overall and loads independently of report date filters.
  useEffect(() => {
    if (activeSnapshot) return;
    const controller = new AbortController();

    async function loadOverview() {
      try {
        const summaryData = await getReportSummary({ signal: controller.signal });

        if (!controller.signal.aborted) {
          setSummary(summaryData);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message);
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    loadOverview();

    return () => controller.abort();
  }, [activeSnapshot, revision]);

  useEffect(() => {
    if (activeSnapshot) return;
    const controller = new AbortController();
    async function loadOutcomes() {
      try {
        const period = outcomeStart ? { startDate: outcomeStart, endDate: outcomeEnd } : null;
        const data = await getSuccessRate({ signal: controller.signal }, period);
        if (!controller.signal.aborted) setSuccessRate(data);
      } catch (error) {
        if (!controller.signal.aborted) setOutcomeError(error.message || "Unable to load delivery outcomes.");
      } finally {
        if (!controller.signal.aborted) setOutcomeLoading(false);
      }
    }
    loadOutcomes();
    return () => controller.abort();
  }, [activeSnapshot, outcomeStart, outcomeEnd, revision]);

  // Load daily deliveries whenever the selected period changes.
  useEffect(() => {
    if (activeSnapshot) return;
    const controller = new AbortController();

    async function loadDailyDeliveries() {
      setChartLoading(true);
      setChartError("");

      try {
        const today = getSriLankaToday();

        const [year, month, day] = today
          .split("-")
          .map(Number);

        const start = new Date(
          Date.UTC(year, month - 1, day)
        );

        start.setUTCDate(
          start.getUTCDate() - (selectedPeriod - 1)
        );

        const data = await getDailyDeliveries(formatDate(start), today, { signal: controller.signal });

        if (!controller.signal.aborted) {
          setDailyDeliveries(data);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setChartError(err.message);
        }
      } finally {
        if (!controller.signal.aborted) {
          setChartLoading(false);
        }
      }
    }

    loadDailyDeliveries();

    return () => controller.abort();
  }, [selectedPeriod, activeSnapshot, revision]);

  // Prepare chart and report values.
  const reportPeriod = activeSnapshot
    ? Math.round((Date.parse(activeSnapshot.endDate) - Date.parse(activeSnapshot.startDate)) / 86400000) + 1
    : selectedPeriod;
  const chartDays = buildChartDays(dailyDeliveries, reportPeriod, activeSnapshot?.endDate);
  const dailyFilename = activeSnapshot
    ? `swiftdrop-daily-deliveries-snapshot-${activeSnapshot.id}`
    : `swiftdrop-daily-deliveries-${selectedPeriod}-days`;
  const outcomeFilename = activeSnapshot
    ? `swiftdrop-success-failure-snapshot-${activeSnapshot.id}`
    : "swiftdrop-success-failure-report";

  const maxDeliveries = Math.max(
    1,
    ...chartDays.map((day) => day.deliveries)
  );

  const delivered = successRate?.deliveredParcels ?? 0;
  const failed = successRate?.failedParcels ?? 0;

  const totalOutcomes = delivered + failed;

  const successPercentage = successRate?.successRate ?? 0;
  const failurePercentage = successRate?.failureRate ?? 0;

  const completedInPeriod = chartDays.reduce(
    (total, day) => total + day.deliveries,
    0
  );

  const generatedAt = getGeneratedTime(activeSnapshot?.createdAt);

  // Shared information for both report exports.
  const commonMetadata = [
    ["System", "SwiftDrop"],
    ["Data Source", activeSnapshot ? "Saved report" : "Live report"],
    ...(activeSnapshot ? [["Snapshot ID", activeSnapshot.id]] : []),
    ["Generated On", `${generatedAt} (Sri Lanka)`],
    ["Total Parcels (Overall)", summary?.totalParcels ?? 0],
    ["Delivered (Overall)", summary?.deliveredParcels ?? 0],
    ["Failed (Overall)", summary?.failedParcels ?? activeSnapshot?.data.successRate.failedParcels ?? 0],
  ];

  // Daily Deliveries report information.
  const dailyMetadata = [
    ["Report Type", "Daily Deliveries Report"],
    ...commonMetadata,
    ["Reporting Period", `${chartDays[0]?.date} to ${chartDays[chartDays.length - 1]?.date}`],
    ["Period Start", chartDays[0]?.date ?? "-"],
    ["Period End", chartDays[chartDays.length - 1]?.date ?? "-"],
    ["Completed in Period", completedInPeriod],
  ];

  // Success & Failure report information.
  // Percentages are kept in the details table only.
  const outcomeMetadata = [
    ["Report Type", "Delivery Success & Failure Report"],
    ...commonMetadata,
    ["Outcome Reporting Period", outcomePeriodLabel],
    ["Delivered in Outcome Period", delivered],
    ["Failed in Outcome Period", failed],
    ["Completed Outcomes", totalOutcomes],
  ];

  const dailyRows = chartDays.map((day) => [
    day.date,
    day.deliveries,
  ]);

  const outcomeRows = [
    ["Successfully Delivered", delivered],
    ["Failed Deliveries", failed],
    ["Total Completed Outcomes", totalOutcomes],
    ["Success Rate", `${successPercentage.toFixed(1)}%`],
    ["Failure Rate", `${failurePercentage.toFixed(1)}%`],
  ];

  // Daily Deliveries CSV.
  function exportDailyCSV() {
    if (chartLoading || chartError) return;

    downloadCSV(
      `${dailyFilename}.csv`,
      [
        ["SwiftDrop Daily Deliveries Report"],
        ...dailyMetadata,
        [],
        ["Date", "Completed Deliveries"],
        ...dailyRows,
      ]
    );
  }

  // Daily Deliveries PDF.
  function exportDailyPDF() {
    if (chartLoading || chartError) return;

    createPDF(
      "Daily Deliveries Report",
      dailyMetadata,
      ["Date", "Completed Deliveries"],
      dailyRows,
      `${dailyFilename}.pdf`
    );
  }

  // Success & Failure CSV.
  function exportOutcomeCSV() {
    downloadCSV(
      `${outcomeFilename}.csv`,
      [
        ["SwiftDrop Delivery Success & Failure Report"],
        ...outcomeMetadata,
        [],
        ["Metric", "Value"],
        ...outcomeRows,
        [],
        [
          "Note",
          "Rates are calculated using delivered and failed parcels; in-progress parcels are excluded.",
        ],
      ]
    );
  }

  // Success & Failure PDF.
  function exportOutcomePDF() {
    createPDF(
      "Delivery Success & Failure Report",
      outcomeMetadata,
      ["Metric", "Value"],
      outcomeRows,
      `${outcomeFilename}.pdf`
    );
  }

  return (
    <div className="reports-dashboard" ref={reportViewRef}>
      <PageHeader
        title="Delivery Reports"
        subtitle="Monitor parcel deliveries and operational performance."
        action={<Button to="/admin/users" variant="secondary">Manage users</Button>}
      />

      {activeSnapshot && (
        <div className="reports-snapshot-status" role="status">
          <div>
            <strong>Viewing saved report</strong>
            <p>Saved {getGeneratedTime(activeSnapshot.createdAt)} (Sri Lanka), {activeSnapshot.startDate} to {activeSnapshot.endDate}.</p>
            <p>The summary cards and reports below show this saved copy.</p>
          </div>
          <Button variant="secondary" onClick={returnToLive}>Return to live reports</Button>
        </div>
      )}

      {loading && (
        <div className="reports-panel"><Spinner label="Loading reports..." /></div>
      )}

      {error && (
        <div className="reports-error" role="alert">
          <p>{error}</p>
          <Button variant="secondary" onClick={retryReports}>Try again</Button>
        </div>
      )}

      {!loading && !error && (
        <>
          {/* Parcel overview */}
          <section className="reports-section">
            <h2>Parcel Overview</h2>

            <div className="reports-card-grid">
              <div className="reports-card total">
                <h3>Total Parcels</h3>
                <p className="reports-value">
                  {summary?.totalParcels ?? 0}
                </p>
              </div>

              <div className="reports-card delivered">
                <h3>Delivered</h3>
                <p className="reports-value">
                  {summary?.deliveredParcels ?? 0}
                </p>
              </div>

              <div className="reports-card in-transit">
                <h3>In Transit</h3>
                <p className="reports-value">
                  {summary?.inTransitParcels ?? 0}
                </p>
              </div>

              <div className="reports-card failed">
                <h3>Failed</h3>
                <p className="reports-value">
                  {summary?.failedParcels ?? activeSnapshot?.data.successRate.failedParcels ?? 0}
                </p>
              </div>
            </div>
          </section>

          {/* Delivery performance charts */}
          <section className="reports-section">
            <h2>Delivery Performance</h2>

            <div className="reports-charts-grid">
              {/* Success & Failure chart and report */}
              <div className="reports-panel">
                <div className="reports-chart-header">
                  <h3>Success &amp; Failure</h3>
                  <div className="reports-chart-actions">
                    <select className="reports-period-select" aria-label="Select success and failure report period"
                      disabled={Boolean(activeSnapshot)}
                      value={activeSnapshot ? (successPeriod ? Math.round((Date.parse(outcomeEnd) - Date.parse(outcomeStart)) / 86400000) + 1 : 0) : selectedOutcomePeriod}
                      onChange={(event) => {
                        setOutcomeLoading(true);
                        setOutcomeError("");
                        setSelectedOutcomePeriod(Number(event.target.value));
                      }}>
                      <option value={0}>All Time</option>
                      <option value={7}>Last 7 Days</option>
                      <option value={30}>Last 30 Days</option>
                    </select>
                  <button
                    type="button"
                    className="reports-view-button"
                    onClick={() =>
                      setShowOutcomeReport((current) => !current)
                    }
                    aria-expanded={showOutcomeReport}
                  >
                    {showOutcomeReport
                      ? "Hide Report"
                      : "View Report"}
                  </button>
                  </div>
                </div>

                <p className="reports-note">Outcome period: {outcomePeriodLabel}. {successPeriod
                  ? "Current delivered/failed parcels whose latest completed outcome was recorded in this period."
                  : "Current delivered and failed parcels across all dates."}</p>
                {outcomeLoading && <Spinner label="Loading success and failure report..." />}
                {outcomeError && <div className="reports-error" role="alert">{outcomeError}
                  <Button variant="secondary" onClick={() => { setOutcomeLoading(true); setOutcomeError(""); setRevision((value) => value + 1); }}>Try again</Button>
                </div>}
                <div hidden={outcomeLoading || Boolean(outcomeError)}>

                <div className="reports-donut-layout">
                  <div
                    className="reports-donut"
                    role="img"
                    aria-label={
                      totalOutcomes === 0
                        ? "No delivery outcomes recorded"
                        : `${successPercentage.toFixed(1)}% delivered and ${failurePercentage.toFixed(1)}% failed`
                    }
                    style={{
                      background:
                        totalOutcomes === 0
                          ? "var(--color-border)"
                          : `conic-gradient(
                              var(--color-success) 0% ${successPercentage}%,
                              var(--color-error) ${successPercentage}% 100%
                            )`,
                    }}
                  >
                    <div className="reports-donut-center">
                      <strong>{totalOutcomes}</strong>
                      <span>Outcomes</span>
                    </div>
                  </div>

                  <div className="reports-donut-details">
                    <div className="reports-donut-stat">
                      <span className="reports-stat-label">
                        <i className="reports-legend-dot delivered-dot" />
                        Delivered
                      </span>

                      <strong className="reports-success-text">
                        {successPercentage.toFixed(1)}%
                      </strong>

                      <small>{delivered} parcels</small>
                    </div>

                    <div className="reports-donut-stat">
                      <span className="reports-stat-label">
                        <i className="reports-legend-dot failed-dot" />
                        Failed
                      </span>

                      <strong className="reports-failure-text">
                        {failurePercentage.toFixed(1)}%
                      </strong>

                      <small>{failed} parcels</small>
                    </div>
                  </div>
                </div>

                {/* Only display a note when there are no outcomes. */}
                {totalOutcomes === 0 && (
                  <p className="reports-note">
                    No completed delivery outcomes yet.
                  </p>
                )}

                {showOutcomeReport && (
                  <div className="reports-detail">
                    <div className="reports-detail-heading">
                      <h4>SwiftDrop</h4>
                      <p>
                        Delivery Success &amp; Failure Report
                      </p>
                    </div>

                    <div className="reports-detail-meta">
                      <p>
                        <strong>Report Type:</strong>{" "}
                        Delivery Success &amp; Failure Report
                      </p>

                      <p>
                        <strong>Generated:</strong>{" "}
                        {generatedAt} (Sri Lanka)
                      </p>

                      <p>
                        <strong>Total Parcels:</strong>{" "}
                        {summary?.totalParcels ?? 0}
                      </p>

                      <p>
                        <strong>Delivered:</strong> {delivered}
                      </p>

                      <p>
                        <strong>Failed:</strong> {failed}
                      </p>

                      <p>
                        <strong>Outcome period:</strong> {outcomePeriodLabel}
                      </p>
                      <p>
                        <strong>Completed Outcomes:</strong>{" "}
                        {totalOutcomes}
                      </p>
                    </div>

                    <h5 className="reports-detail-subtitle">
                      Delivery Outcome Details
                    </h5>

                    <div className="reports-table-wrapper" tabIndex={0} role="region" aria-label="Report details">
                      <table className="reports-table">
                        <thead>
                          <tr>
                            <th>Metric</th>
                            <th>Value</th>
                          </tr>
                        </thead>

                        <tbody>
                          {outcomeRows.map(([metric, value]) => (
                            <tr key={metric}>
                              <td>{metric}</td>
                              <td>{value}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <p className="reports-note">
                      Success and failure rates are calculated
                      using delivered and failed parcels.
                      In-progress parcels are excluded.
                    </p>

                    <div className="reports-download-actions">
                      <button
                        type="button"
                        className="reports-export-button"
                        onClick={exportOutcomePDF}
                      >
                        Download PDF
                      </button>

                      <button
                        type="button"
                        className="reports-secondary-button"
                        onClick={exportOutcomeCSV}
                      >
                        Download CSV
                      </button>
                    </div>
                  </div>
                )}
              </div>

              </div>

              {/* Daily Deliveries chart and report */}
              <div className="reports-panel">
                <div className="reports-chart-header">
                  <h3>Daily Deliveries</h3>

                  <div className="reports-chart-actions">
                    <select
                      className="reports-period-select"
                      aria-label="Select delivery report period"
                      disabled={Boolean(activeSnapshot)}
                      value={reportPeriod}
                      onChange={(event) =>
                        setSelectedPeriod(
                          Number(event.target.value)
                        )
                      }
                    >
                      <option value={7}>Last 7 Days</option>
                      <option value={30}>Last 30 Days</option>
                    </select>

                    <button
                      type="button"
                      className="reports-view-button"
                      onClick={() =>
                        setShowDailyReport((current) => !current)
                      }
                      aria-expanded={showDailyReport}
                    >
                      {showDailyReport
                        ? "Hide Report"
                        : "View Report"}
                    </button>
                  </div>
                </div>

                {chartError && (
                  <div className="reports-error" role="alert">
                    <p>{chartError}</p>
                    <Button variant="secondary" onClick={retryReports}>Try again</Button>
                  </div>
                )}

                {chartLoading ? (
                  <Spinner label="Loading daily deliveries..." />
                ) : !chartError ? (
                  <div
                    className="reports-mini-chart"
                    tabIndex={0}
                    role="img"
                    aria-label={`Completed deliveries from ${chartDays[0]?.date} to ${chartDays[chartDays.length - 1]?.date}`}
                  >
                    {chartDays.map((day) => (
                      <div
                        className="reports-mini-chart-item"
                        key={day.date}
                        title={`${day.date}: ${day.deliveries} deliveries`}
                      >
                        <span className="reports-mini-chart-count">
                          {day.deliveries}
                        </span>

                        <div className="reports-mini-chart-track">
                          <div
                            className="reports-mini-chart-bar"
                            style={{
                              height: `${
                                (day.deliveries / maxDeliveries) * 100
                              }%`,
                            }}
                          />
                        </div>

                        <span className="reports-mini-chart-label">
                          {day.label}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : null}

                {showDailyReport &&
                  !chartLoading &&
                  !chartError && (
                    <div className="reports-detail">
                      <div className="reports-detail-heading">
                        <h4>SwiftDrop</h4>
                        <p>Daily Deliveries Report</p>
                      </div>

                      <div className="reports-detail-meta">
                        <p>
                          <strong>Report Type:</strong>{" "}
                          Daily Deliveries Report
                        </p>

                        <p>
                          <strong>Generated:</strong>{" "}
                          {generatedAt} (Sri Lanka)
                        </p>

                        <p>
                          <strong>Reporting Period:</strong>{" "}
                          {chartDays[0]?.date} to {chartDays[chartDays.length - 1]?.date}
                        </p>

                        <p>
                          <strong>Period Start:</strong>{" "}
                          {chartDays[0]?.date}
                        </p>

                        <p>
                          <strong>Period End:</strong>{" "}
                          {chartDays[chartDays.length - 1]?.date}
                        </p>

                        <p>
                          <strong>Total Parcels:</strong>{" "}
                          {summary?.totalParcels ?? 0}
                        </p>

                        <p>
                          <strong>Delivered (overall):</strong> {summary?.deliveredParcels ?? 0}
                        </p>

                        <p>
                          <strong>Failed (overall):</strong> {summary?.failedParcels ?? activeSnapshot?.data.successRate.failedParcels ?? 0}
                        </p>

                        <p>
                          <strong>Completed in Period:</strong>{" "}
                          {completedInPeriod}
                        </p>
                      </div>

                      <h5 className="reports-detail-subtitle">
                        Daily Delivery Details
                      </h5>

                      <div className="reports-table-wrapper" tabIndex={0} role="region" aria-label="Report details">
                        <table className="reports-table">
                          <thead>
                            <tr>
                              <th>Date</th>
                              <th>Completed Deliveries</th>
                            </tr>
                          </thead>

                          <tbody>
                            {dailyRows.map(([date, count]) => (
                              <tr key={date}>
                                <td>{date}</td>
                                <td>{count}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>

                      <div className="reports-download-actions">
                        <button
                          type="button"
                          className="reports-export-button"
                          onClick={exportDailyPDF}
                        >
                          Download PDF
                        </button>

                        <button
                          type="button"
                          className="reports-secondary-button"
                          onClick={exportDailyCSV}
                        >
                          Download CSV
                        </button>
                      </div>
                    </div>
                  )}
              </div>
            </div>
          </section>

          {/* Recorded parcel fees */}
          <section className="reports-section">
            <div className="reports-panel reports-fees-panel">
              <h3>Recorded Parcel Fees</h3>

              <p className="reports-fees-value">
                LKR{" "}
                {Number(
                  summary?.totalFees ?? 0
                ).toLocaleString("en-LK", {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                })}
              </p>
            </div>
          </section>
        </>
      )}

      <ReportSnapshots
        startDate={chartDays[0]?.date}
        endDate={chartDays[chartDays.length - 1]?.date}
        successPeriod={successPeriod}
        canSave={!loading && !chartLoading && !outcomeLoading && !error && !chartError && !outcomeError}
        activeSnapshot={activeSnapshot}
        onOpen={openSnapshot}
      />
    </div>
  );
}
