import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './guards/auth.guard';
import { roleGuard } from './guards/role.guard';

export const routes: Routes = [
  // Default redirect
  { path: '', redirectTo: '/teams', pathMatch: 'full' },

  // Guest-only routes (login, register)
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.component').then(m => m.LoginComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register/register.component').then(m => m.RegisterComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./auth/otp/otp.component').then(m => m.OtpComponent)
    // No guard here — accessible regardless of auth state since it bridges registration→login
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./auth/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./auth/reset-password/reset-password.component').then(m => m.ResetPasswordComponent),
    canActivate: [guestGuard]
  },

  // Authenticated routes
  {
    path: 'dashboard',
    loadComponent: () => import('./tasks/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'teams',
    loadComponent: () => import('./teams/teams.component').then(m => m.TeamsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'teams/new',
    loadComponent: () => import('./teams/team-form.component').then(m => m.TeamFormComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER'] }
  },
  {
    path: 'teams/:id',
    loadComponent: () => import('./teams/team-detail.component').then(m => m.TeamDetailComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'MEMBER', 'VIEWER'] }
  },
  {
    path: 'teams/:id/edit',
    loadComponent: () => import('./teams/team-form.component').then(m => m.TeamFormComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER'] }
  },
  {
    path: 'admin',
    loadComponent: () => import('./admin/admin.component').then(m => m.AdminComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'tasks/new',
    loadComponent: () => import('./tasks/task-form/task-form.component').then(m => m.TaskFormComponent),
    canActivate: [authGuard]
  },
  {
    path: 'tasks/:id/edit',
    loadComponent: () => import('./tasks/task-form/task-form.component').then(m => m.TaskFormComponent),
    canActivate: [authGuard]
  },
  {
    path: 'settings',
    loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent),
    canActivate: [authGuard]
  },

  // 404 Wildcard
  {
    path: '**',
    loadComponent: () => import('./shared/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];
