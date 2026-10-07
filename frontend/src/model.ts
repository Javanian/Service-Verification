export interface Unit {
  id: string;
  label: string;
  cleaned: boolean;
  drain_checked: boolean;
  cooling_checked: boolean;
  actions: string;
  before_id: string | null;
  after_id: string | null;
}
export interface Job {
  id: string;
  customer: string;
  location: string;
  invoice: string;
  technician: string;
  status: string;
  reason: string;
  version: number;
  revision: number;
  units: Unit[];
  reports: { revision: number; approved_by: string; approved_at: string }[];
  events: {
    actor: string;
    action: string;
    detail: string;
    created_at: string;
  }[];
}
export function complete(unit: Unit): boolean {
  return (
    unit.cleaned &&
    unit.drain_checked &&
    unit.cooling_checked &&
    !!unit.actions.trim() &&
    !!unit.before_id &&
    !!unit.after_id
  );
}
export function editable(job: Job): boolean {
  return ["DRAFT", "CHANGES_REQUESTED"].includes(job.status);
}
