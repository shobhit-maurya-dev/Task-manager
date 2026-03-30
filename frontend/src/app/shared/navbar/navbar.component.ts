import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { NotificationService, Notification } from '../../services/notification.service';
import { User } from '../../models/user.model';
import { HasRoleDirective } from '../../directives/has-role.directive';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, HasRoleDirective],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent implements OnInit, OnDestroy {
  isLoggedIn = false;
  currentUser: User | null = null;
  isMenuOpen = false;
  isUserDropdownOpen = false;
  isNotificationsOpen = false;
  isDarkMode = false;
  notifications: Notification[] = [];
  unreadCount = 0;
  private subs: Subscription[] = [];

  constructor(
    private authService: AuthService,
    private toastService: ToastService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.subs.push(
      this.authService.isLoggedIn$.subscribe(
        (loggedIn) => (this.isLoggedIn = loggedIn)
      )
    );
    this.subs.push(
      this.authService.currentUser$.subscribe(
        (user) => (this.currentUser = user)
      )
    );

    this.subs.push(
      this.notificationService.notifications$.subscribe(
        (notes) => (this.notifications = notes)
      )
    );

    this.subs.push(
      this.notificationService.unreadCount$.subscribe(
        (count) => (this.unreadCount = count)
      )
    );

    // Initialize theme from tf-theme (consistent with Settings/App component)
    const saved = localStorage.getItem('tf-theme') || 'light';
    this.isDarkMode = saved === 'dark';
    this.applyTheme(saved);
  }

  private applyTheme(theme: string): void {
    document.documentElement.setAttribute('data-theme', theme);
    this.isDarkMode = theme === 'dark';
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  toggleMenu(): void {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu(): void {
    this.isMenuOpen = false;
  }

  toggleUserDropdown(): void {
    this.isUserDropdownOpen = !this.isUserDropdownOpen;
    if (this.isUserDropdownOpen) this.isNotificationsOpen = false;
  }

  toggleNotifications(): void {
    this.isNotificationsOpen = !this.isNotificationsOpen;
    if (this.isNotificationsOpen) {
      this.isUserDropdownOpen = false;
      // Mark as read when opening? Or let user do it. Let's mark all as read for simplicity if they open it.
      // this.notificationService.markAllAsRead(); 
    }
  }

  markAsRead(id: string, event: Event): void {
    event.stopPropagation();
    this.notificationService.markAsRead(id);
  }

  clearAllNotifications(): void {
    this.notificationService.clearAll();
  }

  closeDropdowns(): void {
    this.isUserDropdownOpen = false;
    this.isNotificationsOpen = false;
    this.isMenuOpen = false;
  }

  toggleDarkMode(): void {
    const newTheme = this.isDarkMode ? 'light' : 'dark';
    localStorage.setItem('tf-theme', newTheme);
    this.applyTheme(newTheme);
    // Notify settings component if open (via event or service would be better, but attribute works for now)
  }

  getRoleBadgeClass(role?: string): string {
    switch (role) {
      case 'ADMIN':
        return 'badge bg-danger text-uppercase';
      case 'MANAGER':
        return 'badge bg-purple text-uppercase';
      case 'MEMBER':
        return 'badge bg-primary text-uppercase';
      case 'VIEWER':
        return 'badge bg-secondary text-uppercase';
      default:
        return 'badge bg-secondary text-uppercase';
    }
  }

  getInitials(name?: string): string {
    if (!name) return '?';
    return name.trim().split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2);
  }

  logout(): void {
    this.authService.logout();
    this.toastService.success('Successfully logged out. See you soon!');
    this.closeMenu();
  }
}
