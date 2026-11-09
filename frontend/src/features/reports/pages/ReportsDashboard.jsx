
import { useEffect, useState } from "react";
import { jsPDF } from "jspdf";
import "./ReportsDashboard.css";

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
function getGeneratedTime() {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: TIME_ZONE,
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date());
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
function buildChartDays(dailyDeliveries, numberOfDays) {
  const [year, month, day] = getSriLankaToday()
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
    pdf.setFillColor(16, 42, 76);
    pdf.rect(0, 0, pageWidth, 34, "F");

    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(20);
    pdf.setTextColor(255, 255, 255);
    pdf.text("SwiftDrop", margin, 16);

    pdf.setFontSize(11);
    pdf.text(title, margin, 25);

    pdf.setTextColor(23, 43, 77);
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
    pdf.setTextColor(16, 42, 76);

    const colWidth = contentWidth / columns.length;

    columns.forEach((column, index) => {
      pdf.text(
        String(column),
        margin + 3 + index * colWidth,
        y
      );
    });

    y += 9;

    pdf.setTextColor(23, 43, 77);
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

  const [loading, setLoading] = useState(true);
  const [chartLoading, setChartLoading] = useState(true);

  const [error, setError] = useState("");
  const [chartError, setChartError] = useState("");

  const [showDailyReport, setShowDailyReport] = useState(false);
  const [showOutcomeReport, setShowOutcomeReport] = useState(false);

  // Attach the saved login token to Reports API requests.
  function getAuthHeaders() {
    const token = localStorage.getItem("swiftdrop_token");

    return token
      ? { Authorization: `Bearer ${token}` }
      : {};
  }

  // Load parcel overview and delivery outcome statistics.
  useEffect(() => {
    const controller = new AbortController();

    async function loadOverview() {
      try {
        const responses = await Promise.all([
          fetch("/api/reports/summary", {
            signal: controller.signal,
            headers: getAuthHeaders(),
          }),
          fetch("/api/reports/success-rate", {
            signal: controller.signal,
            headers: getAuthHeaders(),
          }),
        ]);

        if (responses.some((response) => !response.ok)) {
          throw new Error("Unable to load parcel statistics.");
        }

        const [summaryData, rateData] = await Promise.all(
          responses.map((response) => response.json())
        );

        if (!controller.signal.aborted) {
          setSummary(summaryData);
          setSuccessRate(rateData);
        }
      } catch (err) {
        if (err.name !== "AbortError") {
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
  }, []);

  // Load daily deliveries whenever the selected period changes.
  useEffect(() => {
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

        const params = new URLSearchParams({
          startDate: formatDate(start),
          endDate: today,
        });

        const response = await fetch(
          `/api/reports/daily-deliveries?${params.toString()}`,
          {
            signal: controller.signal,
            headers: getAuthHeaders(),
          }
        );

        if (!response.ok) {
          throw new Error("Unable to load daily deliveries.");
        }

        const data = await response.json();

        if (!controller.signal.aborted) {
          setDailyDeliveries(data);
        }
      } catch (err) {
        if (err.name !== "AbortError") {
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
  }, [selectedPeriod]);

  // Prepare chart and report values.
  const chartDays = buildChartDays(
    dailyDeliveries,
    selectedPeriod
  );

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

  const generatedAt = getGeneratedTime();

  // Shared information for both report exports.
  const commonMetadata = [
    ["System", "SwiftDrop"],
    ["Generated On", `${generatedAt} (Sri Lanka)`],
    ["Total Parcels (Overall)", summary?.totalParcels ?? 0],
    ["Delivered", delivered],
    ["Failed", failed],
  ];

  // Daily Deliveries report information.
  const dailyMetadata = [
    ["Report Type", "Daily Deliveries Report"],
    ...commonMetadata,
    ["Reporting Period", `Last ${selectedPeriod} Days`],
    ["Period Start", chartDays[0]?.date ?? "-"],
    ["Period End", chartDays[chartDays.length - 1]?.date ?? "-"],
    ["Completed in Period", completedInPeriod],
  ];

  // Success & Failure report information.
  // Percentages are kept in the details table only.
  const outcomeMetadata = [
    ["Report Type", "Delivery Success & Failure Report"],
    ...commonMetadata,
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
      `swiftdrop-daily-deliveries-${selectedPeriod}-days.csv`,
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
      `swiftdrop-daily-deliveries-${selectedPeriod}-days.pdf`
    );
  }

  // Success & Failure CSV.
  function exportOutcomeCSV() {
    downloadCSV(
      "swiftdrop-success-failure-report.csv",
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
      "swiftdrop-success-failure-report.pdf"
    );
  }

  return (
    <main className="reports-dashboard">
      <header className="reports-header">
        <h1>Delivery Reports</h1>
        <p>
          Monitor parcel deliveries and operational performance.
        </p>
      </header>

      {loading && (
        <p className="reports-loading">
          Loading reports...
        </p>
      )}

      {error && (
        <p className="reports-error" role="alert">
          {error}
        </p>
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
                  {failed}
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
                          ? "#e2e8f0"
                          : `conic-gradient(
                              #16a34a 0% ${successPercentage}%,
                              #dc2626 ${successPercentage}% 100%
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
                        <strong>Completed Outcomes:</strong>{" "}
                        {totalOutcomes}
                      </p>
                    </div>

                    <h5 className="reports-detail-subtitle">
                      Delivery Outcome Details
                    </h5>

                    <div className="reports-table-wrapper">
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

              {/* Daily Deliveries chart and report */}
              <div className="reports-panel">
                <div className="reports-chart-header">
                  <h3>Daily Deliveries</h3>

                  <div className="reports-chart-actions">
                    <select
                      className="reports-period-select"
                      aria-label="Select delivery report period"
                      value={selectedPeriod}
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
                  <p className="reports-error" role="alert">
                    {chartError}
                  </p>
                )}

                {chartLoading ? (
                  <p className="reports-loading">
                    Loading daily deliveries...
                  </p>
                ) : !chartError ? (
                  <div
                    className="reports-mini-chart"
                    role="img"
                    aria-label={`Completed deliveries over the last ${selectedPeriod} days`}
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
                          Last {selectedPeriod} Days
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
                          <strong>Delivered:</strong> {delivered}
                        </p>

                        <p>
                          <strong>Failed:</strong> {failed}
                        </p>

                        <p>
                          <strong>Completed in Period:</strong>{" "}
                          {completedInPeriod}
                        </p>
                      </div>

                      <h5 className="reports-detail-subtitle">
                        Daily Delivery Details
                      </h5>

                      <div className="reports-table-wrapper">
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
    </main>
  );
}
