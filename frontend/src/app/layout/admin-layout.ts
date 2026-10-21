import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { navigation } from '../navigation';
@Component({ selector: 'app-admin-layout', standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet], templateUrl: './admin-layout.html', styleUrl: './admin-layout.css' })
export class AdminLayout {
  menu = navigation;
  menuOpen = false;
}
