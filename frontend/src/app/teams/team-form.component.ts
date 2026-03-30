import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TeamService } from '../services/team.service';
import { ToastService } from '../services/toast.service';

@Component({
  selector: 'app-team-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './team-form.component.html'
})
export class TeamFormComponent implements OnInit {
  name = '';
  description = '';
  isSaving = false;
  isEdit = false;
  teamId?: number;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private teamService: TeamService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.isEdit = true;
      this.teamId = id;
      this.loadTeam(id);
    }
  }

  loadTeam(id: number): void {
    this.teamService.getTeamById(id).subscribe({
      next: (team) => {
        this.name = team.name;
        this.description = team.description || '';
      },
      error: () => {
        this.toastService.error('Unable to load team details.');
        this.router.navigate(['/teams']);
      }
    });
  }

  save(): void {
    if (!this.name.trim()) {
      this.toastService.error('Team name is required.');
      return;
    }

    this.isSaving = true;
    const payload = { name: this.name.trim(), description: this.description.trim() };

    const save$ = this.isEdit && this.teamId
      ? this.teamService.updateTeam(this.teamId, payload)
      : this.teamService.createTeam(payload);

    save$.subscribe({
      next: () => {
        this.toastService.success(`Team ${this.isEdit ? 'updated' : 'created'} successfully.`);
        this.router.navigate(['/teams']);
      },
      error: () => {
        this.isSaving = false;
        this.toastService.error(`Failed to ${this.isEdit ? 'update' : 'create'} team.`);
      }
    });
  }
}
