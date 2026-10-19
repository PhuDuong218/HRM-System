import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AsyncPipe } from '@angular/common';
@Component({ selector: 'app-placeholder', standalone: true, imports: [AsyncPipe, RouterLink], templateUrl: './placeholder.html' })
export class PlaceholderPage {
  // Dữ liệu tiêu đề lấy từ app.routes.ts; một trang dùng chung cho phần chưa triển khai.
  data$ = inject(ActivatedRoute).data;
}
