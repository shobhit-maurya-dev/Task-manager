import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivityService } from '../services/activity.service';
import { ToastService } from '../services/toast.service';
import { ActivityLog } from '../models/task.model';
import { RelativeTimePipe } from '../pipes/relative-time.pipe';

@Component({
  selector: 'app-activity-feed',
  standalone: true,
  imports: [CommonModule, RelativeTimePipe],
  templateUrl: './activity-feed.component.html'
})
export class ActivityFeedComponent implements OnInit, OnDestroy {
  activities: ActivityLog[] = [];
  isLoading = false;
  private ticker: any;

  constructor(
    private activityService: ActivityService,
    private toastService: ToastService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadActivities();
    // Tick every 30s to force relativeTime pipe re-evaluation
    this.ticker = setInterval(() => { this.cdr.markForCheck(); }, 30_000);
  }

  ngOnDestroy(): void {
    if (this.ticker) clearInterval(this.ticker);
  }

  loadActivities(): void {
    this.isLoading = true;
    this.activityService.getRecentActivity(20).subscribe({
      next: (data) => {
        this.activities = data;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.toastService.error('Failed to load activity feed.');
      }
    });
  }

  refresh(): void {
    this.loadActivities();
  }

  getActionIcon(code?: string): string {
    const map: Record<string, string> = {
      'TASK_CREATED': 'bi-plus',
      'TASK_STATUS_CHANGED': 'bi-arrow-repeat',
      'TASK_ASSIGNED': 'bi-person-fill',
      'COMMENT_ADDED': 'bi-chat-fill',
      'TASK_DELETED': 'bi-trash-fill',
      'TASK_UPDATED': 'bi-pencil-fill',
      'PRIORITY_CHANGED': 'bi-exclamation-fill'
    };
    return map[code || ''] || 'bi-activity';
  }

  getActionCircleClass(code?: string): string {
    const map: Record<string, string> = {
      'COMMENT_ADDED': 'bg-blue-500/10 border-blue-500/20 text-blue-600',
      'TASK_STATUS_CHANGED': 'bg-amber-500/10 border-amber-500/20 text-amber-600',
      'TASK_ASSIGNED': 'bg-purple-500/10 border-purple-500/20 text-purple-600',
      'TASK_CREATED': 'bg-emerald-500/10 border-emerald-500/20 text-emerald-600',
      'PRIORITY_CHANGED': 'bg-red-500/10 border-red-500/20 text-red-600',
      'TASK_DELETED': 'bg-slate-500/10 border-slate-500/20 text-slate-600',
      'TASK_UPDATED': 'bg-blue-500/10 border-blue-500/20 text-blue-600'
    };
    return map[code || ''] || 'bg-slate-500/10 border-slate-500/20 text-slate-600';
  }

  getActionColor(code?: string): string {
    const map: Record<string, string> = {
      'TASK_CREATED': 'text-emerald-600',
      'TASK_STATUS_CHANGED': 'text-amber-600',
      'TASK_ASSIGNED': 'text-purple-600',
      'COMMENT_ADDED': 'text-blue-600',
      'TASK_DELETED': 'text-slate-600',
      'TASK_UPDATED': 'text-blue-600',
      'PRIORITY_CHANGED': 'text-red-600'
    };
    return map[code || ''] || 'text-slate-400';
  }

  formatActionCode(code: string | undefined): string {
    if (!code) return 'ACTIVITY';
    return code.replace(/_/g, ' ');
  }
}
