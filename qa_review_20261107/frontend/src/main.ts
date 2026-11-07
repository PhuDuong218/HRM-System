import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
// Khởi động component gốc và cung cấp cấu hình chung.
bootstrapApplication(App, appConfig).catch(error => console.error(error));
