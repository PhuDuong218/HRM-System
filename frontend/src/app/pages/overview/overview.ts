import { LanguageService } from '../../services/language.service';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({ selector: 'app-overview', standalone: true, imports: [RouterLink], templateUrl: './overview.html' })
export class OverviewPage { language = inject(LanguageService); }
