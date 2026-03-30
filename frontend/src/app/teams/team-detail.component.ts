import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { TeamService } from '../services/team.service';
import { TeamDetail, TeamMember } from '../models/team.model';
import { ToastService } from '../services/toast.service';
import { RelativeTimePipe } from '../pipes/relative-time.pipe';
import { FormsModule } from '@angular/forms';
import { UserService } from '../services/user.service';
import { User } from '../models/user.model';
import { HasRoleDirective } from '../directives/has-role.directive';

@Component({
  selector: 'app-team-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, RelativeTimePipe, FormsModule, HasRoleDirective],
  templateUrl: './team-detail.component.html'
})
export class TeamDetailComponent implements OnInit {
  team?: TeamDetail;
  teamId?: number;
  isLoading = false;
  isAddingMember = false;
  addMemberUserId?: number;
  allUsers: User[] = [];
  availableUsers: User[] = [];
  searchTerm = '';

  // Bulk Selection
  selectedMemberIds = new Set<number>();
  isSelectionMode = false;

  constructor(
    private route: ActivatedRoute,
    private teamService: TeamService,
    private userService: UserService,
    private toastService: ToastService
  ) { }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.toastService.error('Invalid team ID.');
      return;
    }
    this.loadTeam(id);
    this.loadAvailableUsers();
  }

  loadTeam(id: number): void {
    this.teamId = id;
    this.isLoading = true;
    this.teamService.getTeamById(id).subscribe({
      next: (team) => {
        this.team = team;
        this.isLoading = false;
        this.updateAvailableUsers();
      },
      error: () => {
        this.isLoading = false;
        this.toastService.error('Failed to load team details.');
      }
    });
  }

  loadAvailableUsers(): void {
    this.userService.getAssignableUsers().subscribe({
      next: (users) => {
        this.allUsers = users;
        this.updateAvailableUsers();
      },
      error: () => {
        // ignore as this is optional UI enhancement
      }
    });
  }

  updateAvailableUsers(): void {
    if (!this.team) {
      this.availableUsers = this.allUsers;
      return;
    }

    const memberIds = this.team.members?.map(m => m.id) || [];
    this.availableUsers = this.allUsers
      .filter(u => u.id != null && !memberIds.includes(u.id))
      .filter(u => {
        if (!this.searchTerm) return true;
        const term = this.searchTerm.toLowerCase();
        return u.username.toLowerCase().includes(term) || u.email.toLowerCase().includes(term);
      });
  }

  addMember(): void {
    if (!this.team?.id || !this.addMemberUserId) {
      this.toastService.error('Please select a user first.');
      return;
    }

    this.isAddingMember = true;
    this.teamService.addMember(this.team.id, this.addMemberUserId).subscribe({
      next: () => {
        this.toastService.success('Member added successfully.');
        this.addMemberUserId = undefined;
        this.isAddingMember = false;
        if (this.teamId) {
          this.loadTeam(this.teamId);
        }
      },
      error: () => {
        this.toastService.error('Failed to add member.');
        this.isAddingMember = false;
      }
    });
  }

  removeMember(member: TeamMember): void {
    if (!this.team?.id) return;

    this.toastService.confirm(`Remove ${member.username} from this team?`, () => {
      this.teamService.removeMember(this.team!.id!, member.id).subscribe({
        next: () => {
          this.toastService.success(`Member ${member.username} removed from team ${this.team?.name}.`);
          if (this.teamId) {
            this.loadTeam(this.teamId);
          }
        },
        error: () => {
          this.toastService.error('Failed to remove member.');
        }
      });
    });
  }

  promoteToLead(member: TeamMember): void {
    if (!this.team?.id || !member.id) return;
    this.teamService.toggleLeader(this.team.id, member.id, true).subscribe({
      next: () => {
        this.toastService.success(`${member.username} promoted to Team Lead.`);
        if (this.teamId) {
          this.loadTeam(this.teamId);
        }
      },
      error: () => {
        this.toastService.error('Failed to promote member to Team Lead.');
      }
    });
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

  toggleMemberSelection(id?: number): void {
    if (!id) return;
    if (this.selectedMemberIds.has(id)) {
      this.selectedMemberIds.delete(id);
    } else {
      this.selectedMemberIds.add(id);
    }
  }

  removeSelectedMembers(): void {
    if (this.selectedMemberIds.size === 0) {
      this.toastService.warning('Please select at least one member.');
      return;
    }

    if (!this.team?.id) return;

    this.toastService.confirm(`Remove ${this.selectedMemberIds.size} selected members from the team?`, () => {
      this.isLoading = true;
      const teamId = this.team!.id!;
      const ids = Array.from(this.selectedMemberIds);
      let count = 0;
      let errors = 0;

      ids.forEach(memberId => {
        this.teamService.removeMember(teamId, memberId).subscribe({
          next: () => {
            count++;
            if (count + errors === ids.length) this.finalizeBulkRemove(count, errors);
          },
          error: () => {
            errors++;
            if (count + errors === ids.length) this.finalizeBulkRemove(count, errors);
          }
        });
      });
    });
  }

  private finalizeBulkRemove(count: number, errors: number): void {
    this.isLoading = false;
    this.selectedMemberIds.clear();
    this.isSelectionMode = false;
    if (count > 0) this.toastService.success(`${count} members removed successfully.`);
    if (errors > 0) this.toastService.error(`${errors} removals failed.`);
    if (this.teamId) this.loadTeam(this.teamId);
  }

  getInitials(name?: string): string {
    if (!name) return '?';
    return name.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2);
  }

  filteredUsers(): User[] {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) return this.availableUsers;
    return this.availableUsers.filter(u =>
      u.username.toLowerCase().includes(term) || u.email.toLowerCase().includes(term)
    );
  }
}
