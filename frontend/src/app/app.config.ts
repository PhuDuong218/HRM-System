import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { PreloadAllModules, provideRouter, withPreloading, withRouterConfig } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './services/auth.interceptor';
import { viewCacheInvalidationInterceptor } from './services/view-cache.service';
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withPreloading(PreloadAllModules), withRouterConfig({ canceledNavigationResolution: 'computed' })),
    provideHttpClient(withInterceptors([authInterceptor, viewCacheInvalidationInterceptor]))
  ]
};
