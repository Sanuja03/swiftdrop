import { request } from "@/shared/api/httpClient";

export const REPORT_SNAPSHOT_PAGE_SIZE = 5;

export function getReportSummary(options = {}) {
  return request("/admin/reports/summary", options);
}

export function getSuccessRate(options = {}, period = null) {
  const query = period ? `?${new URLSearchParams(period)}` : "";
  return request(`/admin/reports/success-rate${query}`, options);
}

export function getDailyDeliveries(startDate, endDate, options = {}) {
  const params = new URLSearchParams({ startDate, endDate });
  return request(`/admin/reports/daily-deliveries?${params}`, options);
}

export function getReportSnapshots(page = 0, options = {}, period = null) {
  const query = period ? `&${new URLSearchParams(period)}` : "";
  return request(`/admin/reports/snapshots?page=${page}&size=${REPORT_SNAPSHOT_PAGE_SIZE}${query}`, options);
}

export function getReportSnapshot(id) {
  return request(`/admin/reports/snapshots/${encodeURIComponent(id)}`);
}

export function saveReportSnapshot(snapshotRequest) {
  return request("/admin/reports/snapshots", { method: "POST", json: snapshotRequest });
}
