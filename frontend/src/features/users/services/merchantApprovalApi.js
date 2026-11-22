import { request } from "@/shared/api/httpClient";

export async function getMerchantApplications() {
  const merchants = await request("/admin/merchants");
  if (!Array.isArray(merchants)) throw new Error("The merchant API returned an unexpected response.");
  return merchants;
}

export function approveMerchantApplication(merchantId, notes = "") {
  return request(`/admin/merchants/${encodeURIComponent(merchantId)}/approve`, {
    method: "POST", json: { notes },
  });
}

export function rejectMerchantApplication(merchantId, notes = "") {
  return request(`/admin/merchants/${encodeURIComponent(merchantId)}/reject`, {
    method: "POST", json: { notes },
  });
}
