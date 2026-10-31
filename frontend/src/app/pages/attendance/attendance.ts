import {TranslatePipe} from '../../shared/translate.pipe';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({ selector: 'app-attendance', standalone: true, imports: [RouterLink,TranslatePipe], templateUrl: './attendance.html', styleUrl: '../work-module.css' })
export class AttendancePage { }
