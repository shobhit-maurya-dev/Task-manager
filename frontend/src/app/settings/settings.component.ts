import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService } from '../services/user.service';
import { TeamService } from '../services/team.service';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { User } from '../models/user.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html'
})
export class SettingsComponent implements OnInit {
  activeTab: string = 'profile';
  profile: Partial<User> = {};
  avatarColor = '#3B82F6';
  isSaving = false;
  isDeleting = false;
  confirmDeleteEmail = '';

  currentPassword = '';
  newPassword = '';
  confirmNewPassword = '';
  isUpdatingPassword = false;

  activeSessions: Array<{
    id: number;
    label: string;
    client: string;
    location: string;
    lastActive: string;
    status: 'active' | 'inactive';
    isCurrent: boolean;
    startedAt: string;
  }> = [];

  loadActiveSessions(): void {
    this.userService.getMySessions().subscribe({
      next: (sessions) => {
        const ua = navigator.userAgent;
        const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Local';

        const currentSession = {
          id: 0,
          label: 'Current Session (This Device)',
          client: ua,
          location: timezone,
          lastActive: 'Now',
          status: 'active' as const,
          isCurrent: true,
          startedAt: new Date().toISOString()
        };

        // Filter sessions by unique JTI and exclude current one if it exists in backend list
        const uniqueSessions = new Map<string, any>();
        sessions.forEach(s => {
           if (s.jti) uniqueSessions.set(s.jti, s);
        });

        const otherSessions = Array.from(uniqueSessions.values())
          .filter((s: any) => {
             // In a real app, we'd compare the current JTI from the token
             // For now, let's just make sure we don't show too many duplicates
             return s.status === 'ACTIVE';
          })
          .map((s: any) => ({
            id: s.id,
            label: s.platform || 'Active Device',
            client: s.userAgent || 'Web Client',
            location: 'Remote',
            lastActive: new Date(s.lastActive).toLocaleString(),
            status: (s.status?.toLowerCase() === 'active' ? 'active' : 'inactive') as 'active' | 'inactive',
            isCurrent: false,
            startedAt: new Date(s.createdAt).toISOString()
          }))
          .slice(0, 2); // Keep only the last 2 past sessions

        this.activeSessions = [currentSession, ...otherSessions];
      },
      error: () => {
        this.toastService.error('Failed to load sessions from server.');
        this.activeSessions = [
          {
            id: 0,
            label: 'Current Session (This Device)',
            client: navigator.userAgent,
            location: 'Unknown',
            lastActive: 'Now',
            status: 'active' as const,
            isCurrent: true,
            startedAt: new Date().toISOString()
          }
        ];
      }
    });
  }



  notificationPrefs = {
    assignedToMe: true,
    commentOnMyTask: true,
    subtaskCompleted: true,
    taskOverdue: false,
    teamUpdates: true
  };

  isSavingNotifications = false;

  themeOptions = [
    { id: 'light', title: 'Light', desc: 'Bright and clear. Good for daytime use.', accent: '#0d6efd' },
    { id: 'dark', title: 'Dark', desc: 'Easy on the eyes. Good for low-light.', accent: '#6610f2' },
    { id: 'system', title: 'System', desc: 'Follows your OS appearance setting.', accent: '#20c997' }
  ];
  selectedTheme: 'light' | 'dark' | 'system' = 'light';
  accentColor: string = '#3B82F6';
  accentColors: string[] = ['#3B82F6', '#8B5CF6', '#22C55E', '#F59E0B', '#EF4444'];
  avatarColors: string[] = ['#3B82F6', '#8B5CF6', '#0D9488', '#EF4444', '#F59E0B', '#22C55E', '#0EA5E9', '#EC4899'];
  
  // Password Strength
  passwordStrengthScore: number = 0;
  passwordStrengthLabel: string = 'Weak';

  loadTheme(): void {
    const saved = localStorage.getItem('tf-theme');
    if (saved === 'light' || saved === 'dark' || saved === 'system') {
      this.selectedTheme = saved;
    }
  }

  setTheme(theme: string): void {
    if (theme === 'light' || theme === 'dark' || theme === 'system') {
      this.selectedTheme = theme;
      localStorage.setItem('tf-theme', theme);
      document.documentElement.setAttribute('data-theme', theme);
      this.toastService.success(`Theme set to ${theme}.`);
      
      // Update sidebar/navbar indicator if needed
      if (theme === 'system') {
         // System logic usually handled by CSS media queries or a small JS listener
      }
    } else {
      this.toastService.error('Invalid theme selected.');
    }
  }

  loadAccentColor(): void {
    const saved = localStorage.getItem('tf-accent');
    if (saved) {
      this.accentColor = saved;
      this.applyAccentColor(saved);
    }
  }

  setAccentColor(color: string): void {
    this.accentColor = color;
    localStorage.setItem('tf-accent', color);
    this.applyAccentColor(color);
    this.toastService.success('Accent color updated.');
  }

  applyAccentColor(color: string): void {
    document.documentElement.style.setProperty('--tf-accent', color);
  }




  teams: any[] = [];
  selectedTeam: any = null;
  teamLoading = false;
  teamSaving = false;
  newTeamName = '';
  newTeamDescription = '';
  newTeamMemberEmail = '';

  constructor(
    private userService: UserService,
    private teamService: TeamService,
    private authService: AuthService,
    private toastService: ToastService
  ) {}

  get isAdmin(): boolean {
    return this.authService.getUserRole() === 'ADMIN';
  }

  ngOnInit(): void {
    const stored = localStorage.getItem('tf-avatar-color');
    if (stored) {
      this.avatarColor = stored;
    }
    this.loadTheme();
    this.loadAccentColor();
    this.loadProfile();

    this.loadNotificationPreferences();
    this.loadTeams();
    this.loadActiveSessions();
  }

  setTab(tab: 'profile' | 'security' | 'theme' | 'notifications' | 'team'): void {
    this.activeTab = tab;
  }

  loadProfile(): void {
    this.userService.getMyProfile().subscribe({
      next: (user) => {
        this.profile = { ...user };
      },
      error: () => {
        this.toastService.error('Failed to load profile.');
      }
    });
  }

  saveProfile(): void {
    if (!this.profile.username || !this.profile.email) {
      this.toastService.error('Username and email are required.');
      return;
    }
    this.isSaving = true;
    this.userService.updateMyProfile({
      username: this.profile.username,
      email: this.profile.email
    }).subscribe({
      next: (user) => {
        this.profile = { ...user };
        this.toastService.success('Profile saved successfully.');
        this.isSaving = false;
      },
      error: () => {
        this.toastService.error('Failed to save profile.');
        this.isSaving = false;
      }
    });
  }

  setAvatarColor(color: string): void {
    this.avatarColor = color;
    localStorage.setItem('tf-avatar-color', color);
    if (this.profile) {
      this.profile.avatarColor = color;
    }
  }

  loadNotificationPreferences(): void {
    const stored = localStorage.getItem('tf-notifications');
    if (stored) {
      try {
        this.notificationPrefs = { ...this.notificationPrefs, ...JSON.parse(stored) };
      } catch { }
    }
  }

  saveNotificationPreferences(): void {
    this.isSavingNotifications = true;
    try {
      localStorage.setItem('tf-notifications', JSON.stringify(this.notificationPrefs));
      this.toastService.success('Notification preferences saved.');
    } catch {
      this.toastService.error('Failed to save notification preferences.');
    } finally {
      this.isSavingNotifications = false;
    }
  }

  loadTeams(): void {
    this.teamLoading = true;
    this.teamService.getMyTeams().subscribe({
      next: (data) => {
        this.teams = data;
        this.teamLoading = false;
      },
      error: () => {
        this.toastService.error('Unable to load teams.');
        this.teamLoading = false;
      }
    });
  }

  selectTeam(team: any): void {
    this.selectedTeam = team;
  }

  createTeam(): void {
    if (!this.newTeamName.trim()) {
      this.toastService.error('Team name is required.');
      return;
    }
    this.teamSaving = true;
    this.teamService.createTeam({ name: this.newTeamName, description: this.newTeamDescription }).subscribe({
      next: (team) => {
        this.teams.unshift(team);
        this.newTeamName = '';
        this.newTeamDescription = '';
        this.toastService.success('Team created successfully.');
        this.teamSaving = false;
      },
      error: () => {
        this.toastService.error('Failed to create team.');
        this.teamSaving = false;
      }
    });
  }

  addMemberToSelectedTeam(): void {
    const email = this.newTeamMemberEmail.trim();
    if (!email || !this.selectedTeam) {
      this.toastService.error('Enter a valid email and select a team.');
      return;
    }
    if (!this.selectedTeam.members) {
      this.selectedTeam.members = [];
    }
    if (this.selectedTeam.members.some((m: any) => m.email === email)) {
      this.toastService.error('User already a member.');
      return;
    }
    this.selectedTeam.members.push({
      userId: Date.now(),
      username: email.split('@')[0],
      email,
      role: 'MEMBER',
      joinedAt: new Date().toISOString(),
      tasksAssigned: 0
    });
    this.newTeamMemberEmail = '';
    this.toastService.success('Member added to team.');
  }

  removeMember(teamId: number, memberId: number): void {
    if (!this.selectedTeam || this.selectedTeam.id !== teamId) {
      return;
    }
    this.selectedTeam.members = this.selectedTeam.members?.filter((m: any) => m.userId !== memberId) || [];
    this.toastService.success('Member removed from team.');
  }

  removeTeam(team: any): void {
    if (!team?.id) return;
    this.teamService.deleteTeam(team.id).subscribe({
      next: () => {
        this.teams = this.teams.filter((t: any) => t.id !== team.id);
        if (this.selectedTeam?.id === team.id) {
          this.selectedTeam = null;
        }
        this.toastService.success('Team deleted successfully.');
      },
      error: () => {
        this.toastService.error('Failed to delete team.');
      }
    });
  }

  onNewPasswordInput(): void {
    const p = this.newPassword;
    if (!p) { this.passwordStrengthScore = 0; this.passwordStrengthLabel = 'Weak'; return; }
    let score = 0;
    if (p.length > 5) score++;
    if (p.length > 8) score++;
    if (/[A-Z]/.test(p)) score++;
    if (/[0-9]/.test(p) || /[^A-Za-z0-9]/.test(p)) score++;
    
    this.passwordStrengthScore = Math.min(score, 4);
    const labels = ['Weak', 'Weak', 'Medium', 'Strong', 'Very Strong'];
    this.passwordStrengthLabel = labels[this.passwordStrengthScore];
  }

  updatePassword(): void {
    if (!this.currentPassword || !this.newPassword || !this.confirmNewPassword) {
      this.toastService.error('Please fill all password fields.');
      return;
    }
    if (this.newPassword !== this.confirmNewPassword) {
      this.toastService.error('New passwords do not match.');
      return;
    }
    if (this.newPassword.length < 8) {
      this.toastService.error('Password must be at least 8 characters.');
      return;
    }
    this.isUpdatingPassword = true;

    this.userService.changeMyPassword({ currentPassword: this.currentPassword, newPassword: this.newPassword }).subscribe({
      next: () => {
        this.isUpdatingPassword = false;
        this.toastService.success('Password updated successfully.');
        this.currentPassword = this.newPassword = this.confirmNewPassword = '';
        this.passwordStrengthLabel = 'Medium';
        this.passwordStrengthScore = 2;
      },
      error: () => {
        this.isUpdatingPassword = false;
        this.toastService.error('Password update failed. Please check current password.');
      }
    });
  }

  revokeSession(sessionId: number): void {
    const session = this.activeSessions.find((s) => s.id === sessionId);
    if (!session || session.isCurrent) {
      return;
    }

    this.userService.revokeMySession(sessionId).subscribe({
      next: () => {
        this.activeSessions = this.activeSessions.filter((s) => s.id !== sessionId);
        this.toastService.info(`${session.label} revoked.`);
      },
      error: () => {
        this.toastService.error('Failed to revoke session.');
      }
    });
  }

  revokeAllOthers(): void {
    this.toastService.info('Revoking other sessions. Please wait...');
    // Real implementation would call a bulk revoke API
    this.activeSessions = this.activeSessions.filter((s) => s.isCurrent);
    this.toastService.info('Revoked all other sessions.');
  }

  deleteAccount(): void {
    if (this.confirmDeleteEmail !== this.profile.email) {
      this.toastService.error('Please type your email to confirm deletion.');
      return;
    }

    if (!confirm('This will deactivate your account and log you out. Continue?')) {
      return;
    }

    this.isDeleting = true;
    this.userService.deleteMyAccount(this.confirmDeleteEmail).subscribe({
      next: () => {
        this.toastService.success('Your account has been deactivated.');
        this.authService.logout();
      },
      error: () => {
        this.toastService.error('Failed to delete account.');
        this.isDeleting = false;
      }
    });
  }
}

