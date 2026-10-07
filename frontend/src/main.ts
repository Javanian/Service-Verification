import { Component, signal } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { FormsModule } from "@angular/forms";
import { DatePipe } from "@angular/common";
import { Job, Unit, complete, editable } from "./model";

@Component({
  selector: "proof-app",
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: "./app.html",
})
class App {
  session = signal<{ username: string | null; owner: boolean; csrf: string }>({
    username: null,
    owner: false,
    csrf: "",
  });
  jobs = signal<Job[]>([]);
  selected = signal<Job | null>(null);
  report = signal<{ job: Job; approvedBy: string; approvedAt: string } | null>(
    null,
  );
  dirty = signal(new Set<string>());
  mark(id: string) {
    this.dirty.update((s) => new Set([...s, id]));
  }
  busy = signal(false);
  error = signal("");
  notice = signal("");
  creating = signal(false);
  techs = signal<{ username: string }[]>([]);
  username = "";
  password = "";
  techName = "";
  techPassword = "";
  customer = "";
  location = "";
  invoice = "";
  technician = "";
  unitLabels = "";
  reason = "";
  failed = signal<{ unit: Unit; kind: string; file: File; key: string } | null>(
    null,
  );
  complete = complete;
  editable = editable;
  constructor() {
    void this.run(async () => {
      await this.refreshSession();
      if (this.session().username) await this.load();
    });
  }
  async api(path: string, method = "GET", body?: unknown): Promise<any> {
    const headers: Record<string, string> = {};
    if (method !== "GET") headers["X-CSRF-TOKEN"] = this.session().csrf;
    let payload: BodyInit | undefined;
    if (body instanceof FormData || body instanceof URLSearchParams)
      payload = body;
    else if (body !== undefined) {
      headers["Content-Type"] = "application/json";
      payload = JSON.stringify(body);
    }
    const r = await fetch("/api" + path, { method, headers, body: payload });
    if (!r.ok) {
      let text = "Request failed. Please try again.";
      try {
        text = (await r.json()).message || text;
      } catch {}
      if (r.status === 401) {
        await this.refreshSession();
        if (!this.session().username) {
          this.selected.set(null);
          this.report.set(null);
          this.jobs.set([]);
        }
        text = "Sign in again to continue.";
      }
      if (r.status === 403)
        text = "Access denied or session expired. Sign in again.";
      if (r.status === 409)
        text += " Reload this job to get the latest saved version.";
      throw new Error(text);
    }
    return r.status === 204 ? null : r.json();
  }
  async run(action: () => Promise<void>) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.notice.set("");
    try {
      await action();
    } catch (e) {
      this.error.set(
        e instanceof TypeError
          ? "Connection failed. Your unsaved inputs remain here. Retry when connected."
          : e instanceof Error
            ? e.message
            : "Connection failed. Your unsaved inputs remain here. Retry when connected.",
      );
    } finally {
      this.busy.set(false);
    }
  }
  async refreshSession() {
    this.session.set(await this.api("/session"));
  }
  async load() {
    this.jobs.set(await this.api("/jobs"));
    if (this.session().owner) this.techs.set(await this.api("/technicians"));
  }
  login() {
    void this.run(async () => {
      await this.api(
        "/login",
        "POST",
        new URLSearchParams({
          username: this.username,
          password: this.password,
        }),
      );
      this.password = "";
      await this.refreshSession();
      await this.load();
    });
  }
  logout() {
    void this.run(async () => {
      await this.api("/logout", "POST");
      this.selected.set(null);
      this.report.set(null);
      this.jobs.set([]);
      await this.refreshSession();
    });
  }
  open(id: string) {
    void this.run(async () => {
      this.selected.set(await this.api("/jobs/" + id));
      this.dirty.set(new Set());
      this.report.set(null);
      this.failed.set(null);
      this.reason = "";
    });
  }
  addTech() {
    void this.run(async () => {
      await this.api("/technicians", "POST", {
        username: this.techName,
        password: this.techPassword,
      });
      this.techName = "";
      this.techPassword = "";
      await this.load();
      this.notice.set("Technician created. Share credentials privately.");
    });
  }
  create() {
    void this.run(async () => {
      const job = await this.api("/jobs", "POST", {
        customer: this.customer,
        location: this.location,
        invoice: this.invoice,
        technician: this.technician,
        units: this.unitLabels
          .split("\n")
          .map((x) => x.trim())
          .filter(Boolean),
      });
      this.selected.set(job);
      this.creating.set(false);
      this.customer = this.location = this.invoice = this.unitLabels = "";
      await this.load();
    });
  }
  save(unit: Unit) {
    void this.run(async () => {
      const j = this.selected()!;
      const result = await this.api(`/jobs/${j.id}/units/${unit.id}`, "PUT", {
        version: j.version,
        cleaned: unit.cleaned,
        drainChecked: unit.drain_checked,
        coolingChecked: unit.cooling_checked,
        actions: unit.actions,
      });
      result.units = result.units.map((u: Unit) =>
        u.id === unit.id ? u : (j.units.find((x) => x.id === u.id) ?? u),
      );
      this.selected.set(result);
      this.dirty.update((s) => new Set([...s].filter((id) => id !== unit.id)));
      this.notice.set("Unit checklist saved.");
    });
  }
  choose(event: Event, unit: Unit, kind: string) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      void this.upload({ unit, kind, file, key: crypto.randomUUID() });
    }
    input.value = "";
  }
  async upload(item: { unit: Unit; kind: string; file: File; key: string }) {
    await this.run(async () => {
      this.failed.set(item);
      const j = this.selected()!;
      const body = new FormData();
      body.append("file", item.file);
      body.append("key", item.key);
      body.append("version", String(j.version));
      const result = await this.api(
        `/jobs/${j.id}/units/${item.unit.id}/photos/${item.kind}`,
        "POST",
        body,
      );
      // Preserve unsaved checklist inputs when only a photo changes.
      result.units = result.units.map((u: Unit) => {
        const local = j.units.find((x) => x.id === u.id);
        return local
          ? { ...local, before_id: u.before_id, after_id: u.after_id }
          : u;
      });
      this.selected.set(result);
      this.failed.set(null);
      this.notice.set("Photo saved privately.");
    });
  }
  transition(action: string) {
    void this.run(async () => {
      const j = this.selected()!;
      this.selected.set(
        await this.api(`/jobs/${j.id}/${action}`, "POST", {
          version: j.version,
          reason: this.reason,
        }),
      );
      this.reason = "";
      await this.load();
      this.notice.set(
        action === "approve"
          ? "Approved. Your immutable report is ready."
          : "Job updated.",
      );
    });
  }
  showReport(revision: number) {
    void this.run(async () => {
      this.report.set(
        await this.api(`/jobs/${this.selected()!.id}/reports/${revision}`),
      );
    });
  }
  print() {
    window.print();
  }
  done(j: Job) {
    return j.units.filter(complete).length;
  }
}
bootstrapApplication(App).catch(console.error);
