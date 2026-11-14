import { RouterLink, ActivatedRoute } from '@angular/router';
import { LeaveEvidence } from '../../shared/leave-evidence';
import { MyLeaveRequests } from '../../shared/my-leave-requests';
import { RowViewDirective } from '../../shared/row-view.directive';
import { Feedback } from '../../shared/ui/feedback';
import { HrmButton } from '../../shared/ui/button';
import { LeaveWorkflowGuide } from '../../shared/leave-workflow-guide';
import { Pagination } from '../../shared/pagination';
import { Component, inject, DestroyRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, formatDate } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PageHeader } from '../../shared/page-header';
import { RecordWorkspace } from '../../shared/record-workspace';
import { ActionDialogService } from '../../shared/ui/action-dialog';
import { HeadRequestsService, HeadRequest, HeadRequestDetail, HeadFilters } from '../../services/head-requests.service';
import { errorMessage } from '../../services/employee.service';
@Component({ standalone: true, imports: [RouterLink, LeaveEvidence, MyLeaveRequests, RowViewDirective, Feedback, HrmButton, LeaveWorkflowGuide, Pagination, FormsModule, DatePipe, PageHeader, RecordWorkspace], templateUrl: './head-requests.html', host: { class: 'management-page' }, styles: [`.head-filters{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.head-filters label{display:grid;gap:6px}.head-filter-actions{grid-column:1/-1;display:flex;gap:8px;justify-content:flex-end}.state{display:inline-block;padding:5px 9px;border-radius:6px;background:#eef2f7;color:#34465c;font-weight:600;font-size:12px}.approved{background:#d9f0e3;color:#135b39}.rejected{background:#fde0e5;color:#922035}.pending{background:#fff0c7;color:#795300}.audit-entry{padding:12px 0;border-top:1px solid #dde5ef}.audit-entry p{margin:5px 0}.audit-fields{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,3fr);column-gap:12px;row-gap:6px}.audit-fields dt,.audit-fields dd{margin:0;min-width:0;overflow-wrap:anywhere}.audit-fields dt{font-weight:600}.selected-row{background:#e6f1ff}.list-toolbar{display:flex;justify-content:space-between;align-items:center;gap:8px}.section-gap{margin-bottom:20px}@media(max-width:700px){.head-filters{grid-template-columns:1fr}}`] })
export class HeadRequestsPage {
  private route = inject(ActivatedRoute); private api = inject(HeadRequestsService); private destroy = inject(DestroyRef); private dialog = inject(ActionDialogService); private listVersion = 0; private detailVersion = 0;
  rows: HeadRequest[] = []; selected: HeadRequest | null = null; detail: HeadRequestDetail | null = null;
  page = 0; pages = 0; total = 0; loading = false; saving = false; error = ''; message = ''; note = ''; detailLoading = false; detailError = '';
  requestView: 'department' | 'mine' = 'department'; canReview = false; checkingAccess = true;
  filters: HeadFilters = { stageStatus: '', status: '', type: '', keyword: '', fromDate: '', toDate: '' }; private applied = { ...this.filters };
  constructor() { this.destroy.onDestroy(() => { ++this.listVersion; ++this.detailVersion; }); this.api.capabilities().pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: c => { this.checkingAccess = false; this.canReview = c.departmentHead; if (this.canReview) { this.load(); this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(params => { const id = Number(params.get('notificationRequest')); const hasRequest = Number.isSafeInteger(id) && id > 0; const view = params.get('view'); this.requestView = !hasRequest && view === 'mine' ? 'mine' : 'department'; if (hasRequest) { this.open({ requestId: id } as HeadRequest); } }); } else this.error = 'Trang dành cho trưởng phòng đang có nhiệm kỳ. ADMIN/HR xử lý duyệt cuối tại Quản lý đơn từ.'; }, error: e => { this.checkingAccess = false; this.error = errorMessage(e); } }); }
  load(page = this.page) { if (this.saving || !this.canReview) return; const version = ++this.listVersion; this.loading = true; this.error = ''; this.api.list(page, this.applied).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: r => { if (version !== this.listVersion) return; this.rows = r.content; this.page = r.number ?? page; this.pages = r.totalPages; this.total = r.totalElements; this.loading = false; }, error: e => { if (version !== this.listVersion) return; this.loading = false; this.error = errorMessage(e); this.rows = []; this.total = this.pages = 0; } }); }
  search() { if (this.saving) return; if (this.filters.fromDate && this.filters.toDate && this.filters.fromDate > this.filters.toDate) { this.error = 'Từ ngày phải trước hoặc bằng đến ngày.'; return; } this.applied = { ...this.filters, keyword: this.filters.keyword.trim() }; this.close(); this.load(0); }
  reset() { this.filters = { stageStatus: '', status: '', type: '', keyword: '', fromDate: '', toDate: '' }; this.search(); }
  open(row: HeadRequest) { if (this.saving) return; const version = ++this.detailVersion; this.selected = row; this.note = ''; this.detail = null; this.detailError = ''; this.detailLoading = true; this.api.detail(row.requestId).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: r => { if (version !== this.detailVersion || this.selected?.requestId !== row.requestId) return; this.detail = r; this.selected = { ...this.selected, ...r } as HeadRequest; this.detailLoading = false; }, error: e => { if (version !== this.detailVersion) return; this.detailLoading = false; this.detailError = errorMessage(e); } }); }
  close() { if (this.saving) return; ++this.detailVersion; this.selected = null; this.detail = null; this.detailLoading = false; this.detailError = ''; }
  get canDecide() { return !!this.detail && this.detail.approvalStage?.stageStatus === 'PENDING' && this.detail.status === 'CHO_DUYET' && !this.detail.withdrawnAt; }
  stageLabel(status: string | undefined) { return ({ PENDING: 'Chờ duyệt cấp 1', APPROVED: 'Đã duyệt cấp 1', REJECTED: 'Đã từ chối cấp 1', SKIPPED: 'Chuyển thẳng HR/ADMIN' } as Record<string, string>)[status || ''] || status || '—'; }
  auditChanges(beforeJson?: string, afterJson?: string) {
    const before = this.auditSnapshot(beforeJson);
    const after = this.auditSnapshot(afterJson);
    const fields = [
      ['requestCode', 'Mã đơn'], ['requestType', 'Loại đơn'],
      ['status', 'Trạng thái đơn'], ['stageStatus', 'Kết quả cấp phòng'],
      ['leaveStart', 'Bắt đầu nghỉ'], ['leaveEnd', 'Kết thúc nghỉ'],
      ['requestedLastWorkingDate', 'Ngày làm việc cuối đề nghị'],
      ['approvedLastWorkingDate', 'Ngày làm việc cuối đã duyệt'],
      ['submittedAt', 'Ngày nộp'], ['processedAt', 'Ngày xử lý'],
      ['decidedAt', 'Ngày duyệt cấp phòng'], ['executedAt', 'Ngày áp dụng nghỉ việc'],
      ['withdrawnAt', 'Ngày rút hoặc hủy đơn'],
      ['reason', 'Lý do nộp đơn'], ['note', 'Ghi chú cấp phòng'],
      ['rejectionReason', 'Lý do từ chối'], ['withdrawalReason', 'Lý do rút hoặc hủy đơn']
    ];
    return fields.flatMap(([key, label]) => {
      const previous = this.auditValue(key, before[key]);
      const current = this.auditValue(key, after[key]);
      return previous === current ? [] : [{ key, label, previous, current }];
    });
  }

  private auditSnapshot(json?: string): Record<string, unknown> {
    try {
      const parsed: unknown = JSON.parse(json || '{}');
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed as Record<string, unknown> : {};
    } catch {
      return {};
    }
  }

  private auditValue(key: string, value: unknown): string {
    if (typeof value !== 'string' || !value.trim()) return '';
    if (key === 'status') return this.statusLabel(value);
    if (key === 'stageStatus') return this.stageLabel(value);
    if (key === 'requestType') {
      return ({ NGHI_PHEP: 'Nghỉ phép', NGHI_VIEC: 'Nghỉ việc' } as Record<string, string>)[value] ?? '';
    }
    if (/^\d{4}-\d{2}-\d{2}/.test(value)) {
      const local = /Z$|[+-]\d{2}:\d{2}$/.test(value);
      if (local && Number.isFinite(Date.parse(value))) return formatDate(value, 'dd/MM/yyyy HH:mm', 'en-US', '+0700');
      const [year, month, day] = value.slice(0, 10).split('-');
      const time = value.slice(11, 16);
      return `${day}/${month}/${year}${time ? ' ' + time : ''}`;
    }
    return value;
  }
  statusLabel(status: string | undefined) { return ({ CHO_DUYET: 'Chờ duyệt', DA_DUYET: 'Đã duyệt cuối', TU_CHOI: 'Đã từ chối', DA_HUY: 'Đã rút / hủy' } as Record<string, string>)[status || ''] || status || '—'; }
  actionLabel(action: string) { return ({ LEAVE_REQUEST_CANCEL: 'Hủy đơn nghỉ phép', REQUEST_HEAD_APPROVE: 'Duyệt cấp 1', REQUEST_HEAD_REJECT: 'Từ chối cấp 1', REQUEST_HEAD_SKIP: 'Chuyển thẳng duyệt cuối', LEAVE_REQUEST_CREATE: 'Nộp đơn nghỉ phép', RESIGNATION_REQUEST_CREATE: 'Nộp đơn nghỉ việc', EMPLOYEE_TERMINATION_DECISION: 'Ghi quyết định chấm dứt', RESIGNATION_EXECUTE: 'Áp dụng ngày nghỉ việc', REQUEST_APPROVE: 'Duyệt cuối', REQUEST_REJECT: 'Từ chối đơn', REQUEST_WITHDRAW: 'Rút đơn', LEAVE_APPROVAL_CANCEL: 'Hủy đơn đã duyệt' } as Record<string, string>)[action] || action; }
  async cancel() { const row = this.selected; if (!row || !this.detail?.canCancelLeave || this.saving) return; const reason = await this.dialog.prompt('Nhập lý do hủy đơn ' + row.requestCode + ' của ' + row.employeeName + '.'); if (!reason || this.saving || this.selected?.requestId !== row.requestId) return; this.saving = true; this.api.cancel(row.requestId, reason).pipe(takeUntilDestroyed(this.destroy)).subscribe({ next: () => { this.saving = false; this.message = 'Đã hủy đơn nghỉ phép ' + row.requestCode + '.'; this.open(row); this.load(); }, error: e => { this.saving = false; this.detailError = errorMessage(e); } }); }
  async decide(approve: boolean) {
    const row = this.selected; if (!row || !this.canDecide || this.saving || this.detailLoading) return; if (!approve && !this.note.trim()) { this.detailError = 'Nhập lý do từ chối.'; return; } if (this.note.length > 1000) { this.detailError = 'Ghi chú tối đa 1000 ký tự.'; return; } const note = this.note.trim(); const subject = row.employeeName ? row.requestCode + ' của ' + row.employeeName : row.requestCode; if (!(await this.dialog.confirm((approve ? 'Duyệt cấp 1' : 'Từ chối') + ' đơn ' + subject + '?'))) return; if (this.saving || this.selected?.requestId !== row.requestId || !this.canDecide) return; this.saving = true; this.detailError = ''; this.api.decide(row.requestId, approve, note).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.saving = false; this.message = approve ? 'Đã duyệt cấp 1 đơn ' + subject + '. Đơn chờ HR/ADMIN duyệt cuối.' : 'Đã từ chối đơn ' + subject + '.'; const stageStatus = approve ? 'APPROVED' : 'REJECTED';
        this.rows = this.rows.map(item => item.requestId === row.requestId ? { ...item, stageStatus } : item);
        const stageFilterExcludesResult = !!this.applied.stageStatus && this.applied.stageStatus !== stageStatus;
        const statusFilterExcludesRejection = !approve && !!this.applied.status && this.applied.status !== 'TU_CHOI';
        if (stageFilterExcludesResult) {
          this.filters.stageStatus = this.applied.stageStatus = '';
        }
        if (statusFilterExcludesRejection) {
          this.filters.status = this.applied.status = '';
        }
        this.open(row);
        this.load(stageFilterExcludesResult || statusFilterExcludesRejection ? 0 : this.page);
      }, error: e => { this.saving = false; this.detailError = errorMessage(e); }
    });
  }
}
