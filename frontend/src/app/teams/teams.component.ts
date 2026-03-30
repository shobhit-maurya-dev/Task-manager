import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { TeamService } from '../services/team.service';
import { Team, TeamMember } from '../models/team.model';
import { ToastService } from '../services/toast.service';
import { HasRoleDirective } from '../directives/has-role.directive';
import { AuthService } from '../services/auth.service';
import { UserService } from '../services/user.service';
import { User } from '../models/user.model';

@Component({
  selector: 'app-teams',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, HasRoleDirective],
  templateUrl: './teams.component.html'
})
export class TeamsComponent implements OnInit {
  teams: Team[] = [];
  selectedTeam?: Team;
  selectedTeamAccess: 'private' | 'public' = 'private';
  allUsers: User[] = [];
  availableUsers: User[] = [];
  isAddingMember = false;
  addMemberUserId?: number;
  isLoading = false;
  errorMessage = '';

  // Summary Stats
  totalTeams = 0;
  totalMembers = 0;
  totalActiveTasks = 0;
  totalInactiveTeams = 0;

  // New Modal Filtering State
  searchQuery = '';
  selectedDepartment = 'ALL';
  departments = ['ALL', 'DEVELOPER', 'TESTER', 'DESIGNER'];
  currentUserRole = 'MEMBER';
  isAdminOrManager = false;

  // Bulk Selection
  selectedTeamIds = new Set<number>();
  isSelectionMode = false;
  // Test helpers for live-reload verification
  testClicks = 0;
  testVisible = false;

  constructor(
    private teamService: TeamService,
    private toastService: ToastService,
    private router: Router,
    private authService: AuthService,
    private userService: UserService
  ) { }

  ngOnInit(): void {
    this.currentUserRole = this.authService.getUserRole() || 'MEMBER';
    this.isAdminOrManager = this.currentUserRole === 'ADMIN' || this.currentUserRole === 'MANAGER';
    this.loadTeams();
    this.loadUsers();
  }

  // For debugging: force sample teams to ensure cards render regardless of API issues
  private staticSampleTeams(): Team[] {
    return [
      { id: 1, name: 'Backend Team', memberCount: 3, managerName: 'Alex J.', access: 'Private', role: 'MANAGER', activeTaskCount: 8, members: [{ id: 1, username: 'Alex Johnson', role: 'MANAGER', email: 'alex@taskflow.com', tasksAssigned: 5, joinedAt: '2026-03-01' }, { id: 2, username: 'Sam Kim', role: 'MEMBER', email: 'sam@taskflow.com', tasksAssigned: 3, joinedAt: '2026-03-02' }] },
      { id: 2, name: 'Frontend Team', memberCount: 4, managerName: 'Alex J.', access: 'Private', role: 'MANAGER', activeTaskCount: 10, members: [] },
      { id: 3, name: 'QA Team', memberCount: 4, managerName: 'Sam K.', access: 'Private', role: 'MANAGER', activeTaskCount: 6, members: [] }
    ];
  }

  onTestClick(): void {
    this.testClicks += 1;
    this.testVisible = !this.testVisible;
    console.log('VENOM test click', this.testClicks, 'visible=', this.testVisible);
  }

  getCardColorClass(idx: number): string {
    const classes = ['green', 'blue', 'purple'];
    return classes[idx % classes.length];
  }

  getCardColor(idx: number): string {
    const colors = ['#16A34A', '#2563EB', '#8B5CF6'];
    return colors[idx % colors.length];
  }

  getInitials(name: string): string {
    if (!name) return '';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
  }

  loadUsers(): void {
    this.userService.getAssignableUsers().subscribe({
      next: (users) => {
        this.allUsers = users;
        this.updateAvailableUsers();
      },
      error: () => {
        // partial failure is okay
      }
    });
  }

  updateAvailableUsers(): void {
    if (!this.selectedTeam) {
      this.availableUsers = this.allUsers;
      return;
    }
    const existing = new Set(this.selectedTeam.members?.map(m => m.id));
    this.availableUsers = this.allUsers.filter(u => u.id != null && !existing.has(u.id) && u.role !== 'ADMIN');
  }

  get filteredAvailableUsers(): User[] {
    return this.availableUsers.filter(user => {
      const matchesSearch = !this.searchQuery ||
        user.username.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        user.email.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchesDept = this.selectedDepartment === 'ALL' ||
        user.memberType === this.selectedDepartment;

      return matchesSearch && matchesDept;
    });
  }

  selectTeam(team: Team, shouldScroll = true): void {
    if (!team.id) return;

    // Fetch full details (including members) when selecting a team
    this.teamService.getTeamById(team.id).subscribe({
      next: (fullTeam) => {
        // FILTER: Don't show ADMIN in the member list as an explicit member
        if (fullTeam.members) {
          fullTeam.members = fullTeam.members.filter(m => m.role !== 'ADMIN');
        }

        this.selectedTeam = fullTeam;
        this.isAddingMember = false;
        this.updateAvailableUsers();

        // Auto-scroll ONLY if requested (usually via click)
        if (shouldScroll) {
          setTimeout(() => {
            const detailElement = document.getElementById('teamDetail');
            if (detailElement) {
              detailElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
          }, 100);
        }
      },
      error: () => {
        this.selectedTeam = team;
        this.isAddingMember = false;
        this.updateAvailableUsers();
      }
    });
  }

  updateTeamAccess(access: 'private' | 'public'): void {
    if (this.isAdminOrManager) {
      this.toastService.warning('Admin and Manager cannot modify group access.');
      return;
    }
    this.selectedTeamAccess = access;
    this.toastService.success(`Group access now ${access}.`);
  }

  onAccessToggle(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.updateTeamAccess(input.checked ? 'public' : 'private');
  }

  addMember(): void {
    if (!this.selectedTeam?.id || !this.addMemberUserId) {
      this.toastService.error('Select a member first.');
      return;
    }
    this.teamService.addMember(this.selectedTeam.id, this.addMemberUserId).subscribe({
      next: () => {
        this.toastService.success('Member added successfully.');
        this.addMemberUserId = undefined;
        this.isAddingMember = false;
        this.loadTeams();
      },
      error: () => {
        this.toastService.error('Failed to add member.');
      }
    });
  }

  removeMember(member: TeamMember): void {
    if (!this.selectedTeam?.id) return;

    this.toastService.confirm(`Remove ${member.username} from team ${this.selectedTeam?.name}?`, () => {
      this.teamService.removeMember(this.selectedTeam!.id!, member.id).subscribe({
        next: () => {
          this.toastService.success(`Member ${member.username} removed from team ${this.selectedTeam?.name}.`);
          this.loadTeams();
        },
        error: () => {
          this.toastService.error('Failed to remove member.');
        }
      });
    });
  }
  toggleLeader(member: TeamMember): void {
    if (!this.selectedTeam?.id || !member.id) return;

    const newStatus = !member.isLeader;
    this.teamService.toggleLeader(this.selectedTeam.id, member.id, newStatus).subscribe({
      next: () => {
        member.isLeader = newStatus;
        this.toastService.success(`${member.username} is now ${newStatus ? 'a Team Leader' : 'a regular Member'}.`);
        this.loadTeams(); // Refresh to ensure UI stays in sync
      },
      error: () => {
        this.toastService.error('Failed to update leadership status.');
      }
    });
  }

  loadTeams(): void {
    const selectedId = this.selectedTeam?.id;
    this.isLoading = true;
    this.teamService.getMyTeams().subscribe({
      next: (data) => {
        this.teams = data;

        // Update summary totals
        this.totalTeams = this.teams.length;
        this.totalMembers = this.teams.reduce((sum, t) => sum + (t.members?.length || 0), 0);
        this.totalActiveTasks = this.teams.reduce((sum, t) => {
          const activeCount = t.tasks?.filter((task: any) => task.status !== 'COMPLETED').length || 0;
          return sum + activeCount;
        }, 0);
        this.totalInactiveTeams = this.teams.filter(t => {
          const activeCount = t.tasks?.filter((task: any) => task.status !== 'COMPLETED').length || 0;
          return activeCount === 0;
        }).length;

        // Re-sync selected team with updated data
        if (selectedId) {
          const updated = this.teams.find(t => t.id === selectedId);
          if (updated) {
            this.selectTeam(updated, false);
          } else if (this.teams.length > 0) {
            this.selectTeam(this.teams[0], false);
          }
        } else if (this.teams.length > 0) {
          this.selectTeam(this.teams[0], false);
        }

        this.isLoading = false;
      },
      error: () => {
        this.teams = this.staticSampleTeams();
        this.selectedTeam = this.teams[0];
        this.isLoading = false;
        this.errorMessage = 'Unable to load teams from API, showing sample data for debug.';
      }
    });
  }

  deleteTeam(teamId?: number): void {
    if (!teamId) return;
    this.toastService.confirm('Delete this team? This cannot be undone.', () => {
      this.teamService.deleteTeam(teamId).subscribe({
        next: () => {
          this.toastService.success('Team deleted successfully.');
          this.loadTeams();
        },
        error: () => this.toastService.error('Failed to delete team.')
      });
    });
  }

  toggleTeamSelection(id?: number): void {
    if (!id) return;
    if (this.selectedTeamIds.has(id)) {
      this.selectedTeamIds.delete(id);
    } else {
      this.selectedTeamIds.add(id);
    }
  }

  deleteSelectedTeams(): void {
    if (this.selectedTeamIds.size === 0) {
      this.toastService.warning('Please select at least one team.');
      return;
    }

    this.toastService.confirm(`Delete ${this.selectedTeamIds.size} selected teams? This cannot be undone.`, () => {
      this.isLoading = true;
      const ids = Array.from(this.selectedTeamIds);
      let count = 0;
      let errors = 0;

      ids.forEach(id => {
        this.teamService.deleteTeam(id).subscribe({
          next: () => {
            count++;
            if (count + errors === ids.length) this.finalizeBulkDelete(count, errors);
          },
          error: () => {
            errors++;
            if (count + errors === ids.length) this.finalizeBulkDelete(count, errors);
          }
        });
      });
    });
  }

  private finalizeBulkDelete(count: number, errors: number): void {
    this.isLoading = false;
    this.selectedTeamIds.clear();
    this.isSelectionMode = false;
    if (count > 0) this.toastService.success(`${count} teams deleted successfully.`);
    if (errors > 0) this.toastService.error(`${errors} teams failed to delete.`);
    this.loadTeams();
  }

  goToEdit(teamId?: number): void {
    if (!teamId) return;
    this.router.navigate(['/teams', teamId, 'edit']);
  }

  getRoleBadgeClass(role?: string): string {
    switch ((role || '').toUpperCase()) {
      case 'ADMIN':
        return 'tf-role-badge tf-role-badge-admin';
      case 'MANAGER':
        return 'tf-role-badge tf-role-badge-manager';
      case 'LEAD':
      case 'TEAM_LEAD':
        return 'tf-role-badge tf-role-badge-lead';
      default:
        return 'tf-role-badge tf-role-badge-member';
    }
  }

  isTaskCountPositive(value?: number): boolean {
    return (value ?? 0) > 0;
  }

  taskCountDisplay(value?: number): number {
    return value ?? 0;
  }

  getTeamLeadName(team: Team): string {
    // Look for a member with 'LEAD' role if it exists, otherwise empty string for now
    const lead = team.members?.find(m => m.role === 'LEAD' || m.role === 'TEAM_LEAD');
    return lead ? lead.username : '';
  }
}
