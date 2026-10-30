import {Pagination} from '../../shared/pagination';
import {RouterLink} from '@angular/router';
import { RecordWorkspace } from '../../shared/record-workspace';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Subject, expand, reduce, takeUntil } from 'rxjs';
import { DepartmentService, Department, Head, Assignment, Position } from '../../services/department.service';

@Component({ selector: 'app-departments', standalone: true, imports: [Pagination,RecordWorkspace,RouterLink], templateUrl: './departments.html' })
export class DepartmentsPage implements OnInit, OnDestroy {
  private api = inject(DepartmentService);
  private destroyed = new Subject<void>();
  private selectionChanged = new Subject<void>();
  rows: Department[] = []; selected: Department | null = null;
  heads: Head[] = []; candidates: Assignment[] = []; positions: Position[] = [];
  page = 0; pages = 0; historyPage = 0; historyPages = 0; candidatePage = 0; candidatePages = 0;
  loading = false; saving = false; error = ''; message = '';
  positionsLoading = false; positionsError = '';
  positionsLoaded = false;
  today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());

  ngOnInit() { this.load(); }
  ngOnDestroy() { this.selectionChanged.next(); this.selectionChanged.complete(); this.destroyed.next(); this.destroyed.complete(); }
  load(page = this.page) {
    if (this.loading || this.saving) return;
    this.loading = true; this.error = '';
    this.api.list(page).pipe(takeUntil(this.destroyed)).subscribe({
      next: result => {
        this.rows = result.content; this.pages = result.totalPages; this.page = page; this.loading = false;
        const current = this.selected;
        if (current) {
          this.selected = this.rows.find(d => d.departmentId === current.departmentId) ?? null;
          if (this.selected) this.select(this.selected); else this.clearSelection();
        }
      }, error: e => { this.loading = false; this.fail(e); }
    });
  }
  select(department: Department) {
    if (this.saving) return;
    this.selectionChanged.next(); this.selected = department;
    this.heads = []; this.candidates = []; this.historyPages = 0; this.candidatePages = 0;
    this.historyPage = 0; this.candidatePage = 0;
    this.loadHistory(0); if (department.isActive) this.loadCandidates(0);
    if (department.headEmployeeId) this.loadPositions();
  }
  loadPositions() {
    if (this.positionsLoaded || this.positionsLoading) return;
    this.positionsLoading = true; this.positionsError = '';
    this.api.positions(0).pipe(
      expand((result, index) => index + 1 < result.totalPages ? this.api.positions(index + 1) : EMPTY),
      reduce((all, result) => [...all, ...result.content], [] as Position[]), takeUntil(this.destroyed)
    ).subscribe({
      next: rows => { this.positions = rows.filter(p => p.isActive && p.positionCode !== 'TRUONG_PHONG'); this.positionsLoaded = true; this.positionsLoading = false; },
      error: e => {
        this.positionsLoading = false;
        this.positionsError = e instanceof HttpErrorResponse && e.status === 403
          ? 'Tài khoản chưa có quyền xem danh sách chức vụ. Hãy liên hệ quản trị viên.'
          : 'Không tải được danh sách chức vụ. Hãy thử lại.';
      }
    });
  }
  clearSelection() { this.selectionChanged.next(); this.selected = null; this.heads = []; this.candidates = []; }
  loadHistory(page: number) {
    if (!this.selected) return;
    this.api.history(this.selected.departmentId, page).pipe(takeUntil(this.selectionChanged), takeUntil(this.destroyed)).subscribe({
      next: r => { this.heads = r.items; this.historyPages = r.totalPages; this.historyPage = page; }, error: e => this.fail(e)
    });
  }
  loadCandidates(page: number) {
    if (!this.selected) return;
    this.api.candidates(this.selected.departmentId, page).pipe(takeUntil(this.selectionChanged), takeUntil(this.destroyed)).subscribe({
      next: r => { this.candidates = r.items; this.candidatePages = r.totalPages; this.candidatePage = page; }, error: e => this.fail(e)
    });
  }
  appoint(event: Event, employee: string, date: string, position: string, reason: string) {
    event.preventDefault();
    if (!this.selected || this.saving) return;
    this.saving = true; this.error = ''; this.message = '';
    this.api.appoint(this.selected.departmentId, Number(employee), date, position ? Number(position) : null, reason)
      .pipe(takeUntil(this.destroyed)).subscribe({ next: () => this.saved('Đã cập nhật trưởng phòng và lưu lịch sử.'), error: e => { this.saving = false; this.fail(e); } });
  }
  end(event: Event, date: string, position: string, reason: string) {
    event.preventDefault();
    if (!this.selected || this.saving) return;
    this.saving = true; this.error = ''; this.message = '';
    this.api.end(this.selected.departmentId, date, Number(position), reason).pipe(takeUntil(this.destroyed))
      .subscribe({ next: () => this.saved('Đã kết thúc nhiệm kỳ và lưu phân công tiếp theo.'), error: e => { this.saving = false; this.fail(e); } });
  }
  private saved(message: string) { this.saving = false; this.message = message; this.load(); }
  private fail(error: unknown) {
    this.error = error instanceof HttpErrorResponse && error.status === 401
      ? 'Cần đăng nhập ADMIN/HR. Phần đăng nhập của backend phải cung cấp phiên đã xác thực trước khi dùng chức năng này.'
      : error instanceof HttpErrorResponse && error.status === 403 ? 'Tài khoản không có quyền quản lý nhân sự.'
      : error instanceof HttpErrorResponse && error.error?.loi_chi_tiet ? error.error.loi_chi_tiet
      : 'Không tải được dữ liệu. Hãy kiểm tra kết nối rồi thử lại.';
  }
}
