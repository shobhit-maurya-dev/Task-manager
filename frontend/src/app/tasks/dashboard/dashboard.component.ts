import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { TaskService } from '../../services/task.service';
import { TimeTrackingService } from '../../services/time-tracking.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { UserService } from '../../services/user.service';
import { TeamService } from '../../services/team.service';
import { Task, TaskStatus, TaskPriority, Subtask, SubtaskSummary, TaskAttachment, TimeEntry, ActiveTimer } from '../../models/task.model';
import { User } from '../../models/user.model';
import { Team } from '../../models/team.model';
import { AnalyticsComponent } from '../../analytics/analytics.component';
import { ActivityFeedComponent } from '../../activity-feed/activity-feed.component';
import { CommentsComponent } from '../../comments/comments.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, AnalyticsComponent, ActivityFeedComponent, CommentsComponent, RelativeTimePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {
  tasks: Task[] = [];
  filteredTasks: Task[] = [];
  activeFilter: string = 'ALL';
  activePriorityFilter: string = 'ALL';
  searchQuery = '';
  isLoading = true;
  errorMessage = '';
  successMessage = '';
  deleteTaskId: number | null = null;
  isDeleting = false;
  updatingTaskId: number | null = null;
  currentUsername = '';
  currentUserId: number | null = null;
  currentUserRole: string | null = null;
  readonly TaskStatus = TaskStatus;
  readonly TaskPriority = TaskPriority;
  private subs: Subscription[] = [];

  // Users for assignment
  users: User[] = [];
  // Teams (for team assignment)
  teams: Team[] = [];
  canAssign = false; // Only true for Admin/Manager

  // Comments toggle per task
  expandedCommentTaskId: number | null = null;

  // Task Detail Modal
  detailTask: Task | null = null;
  showDetailModal = false;
  detailSubtasks: Subtask[] = [];
  detailSubtaskSummary: SubtaskSummary = { total: 0, completed: 0 };
  detailAttachments: TaskAttachment[] = [];
  detailTimeLogs: TimeEntry[] = [];
  detailTotalMinutes = 0;
  activeTimer: ActiveTimer | null = null;
  timerDisplay = '00:00:00';
  isTimerActionLoading = false;
  timerIntervalId: any = null;
  activeDetailTab: 'subtasks' | 'attachments' | 'comments' | 'time' = 'subtasks';
  newSubtaskTitle = '';
  isAddingSubtask = false;
  isLoadingSubtasks = false;
  isLoadingAttachments = false;
  isLoadingTimeLogs = false;
  isLoggingTime = false;
  manualLogHours = 0;
  manualLogMinutes = 0;
  manualLogDate = new Date().toISOString().split('T')[0];
  manualLogNote = '';

  // Sort by priority
  sortByPriority = false;

  // Create Task Modal
  showCreateModal = false;
  isCreating = false;
  todayDate = new Date().toISOString().split('T')[0];
  newTask: { title: string; description: string; dueDate: string; status: string; priority: string; assigneeIds: number[]; teamId: number | null } = {
    title: '', description: '', dueDate: '', status: 'PENDING', priority: 'MEDIUM', assigneeIds: [], teamId: null
  };

  // Edit Task Modal
  showEditModal = false;
  isUpdating = false;
  editTask: Task = { title: '', description: '', status: TaskStatus.PENDING, priority: TaskPriority.MEDIUM, dueDate: '', assigneeIds: [] };

  filterOptions = [
    { label: 'All Tasks', value: 'ALL' },
    { label: 'To-Do', value: TaskStatus.PENDING },
    { label: 'In Progress', value: TaskStatus.IN_PROGRESS },
    { label: 'Done', value: TaskStatus.COMPLETED },
    { label: 'Assigned to Me', value: 'ASSIGNED_TO_ME' },
    { label: 'Due Today', value: 'DUE_TODAY' }
  ];

  priorityFilterOptions = [
    { label: 'All', value: 'ALL' },
    { label: 'High', value: 'HIGH' },
    { label: 'Medium', value: 'MEDIUM' },
    { label: 'Low', value: 'LOW' }
  ];

  // Stats
  get totalTasks(): number { return this.tasks.length; }
  get pendingTasks(): number { return this.tasks.filter(t => t.status === TaskStatus.PENDING).length; }
  get inProgressTasks(): number { return this.tasks.filter(t => t.status === TaskStatus.IN_PROGRESS).length; }
  get completedTasks(): number { return this.tasks.filter(t => t.status === TaskStatus.COMPLETED).length; }
  get overdueTasks(): number { return this.tasks.filter(t => this.isOverdue(t.dueDate) && t.status !== TaskStatus.COMPLETED).length; }
  get assignedToMeTasks(): number { return this.tasks.filter(t => t.assigneeIds?.includes(this.currentUserId || -1)).length; }
  get dueTodayTasks(): number { return this.tasks.filter(t => this.isDueToday(t.dueDate) && t.status !== TaskStatus.COMPLETED).length; }

  constructor(
    private taskService: TaskService,
    private timeTrackingService: TimeTrackingService,
    private authService: AuthService,
    private toastService: ToastService,
    private userService: UserService,
    private teamService: TeamService
  ) { }

  private resetDetailData(): void {
    this.detailSubtasks = [];
    this.detailSubtaskSummary = { total: 0, completed: 0 };
    this.detailAttachments = [];
    this.detailTimeLogs = [];
    this.detailTotalMinutes = 0;
    this.activeTimer = null;
    this.newSubtaskTitle = '';
    this.activeDetailTab = 'subtasks';
    this.manualLogHours = 0;
    this.manualLogMinutes = 0;
    this.manualLogDate = new Date().toISOString().split('T')[0];
    this.manualLogNote = '';
  }

  private loadSubtasks(taskId: number): void {
    this.isLoadingSubtasks = true;
    this.taskService.getSubtasks(taskId).subscribe({
      next: (subtasks) => {
        this.detailSubtasks = subtasks;
        this.isLoadingSubtasks = false;
      },
      error: () => {
        this.isLoadingSubtasks = false;
        this.toastService.error('Failed to load subtasks.');
      }
    });

    this.taskService.getSubtaskSummary(taskId).subscribe({
      next: (summary) => {
        this.detailSubtaskSummary = summary;
      },
      error: () => {
        // ignore
      }
    });
  }

  private loadAttachments(taskId: number): void {
    this.isLoadingAttachments = true;
    this.taskService.getAttachments(taskId).subscribe({
      next: (attachments) => {
        this.detailAttachments = attachments;
        this.isLoadingAttachments = false;
      },
      error: () => {
        this.isLoadingAttachments = false;
        this.toastService.error('Failed to load attachments.');
      }
    });
  }

  private loadTimeTracking(taskId: number): void {
    this.loadTimeLogs(taskId);
    this.loadTotalTime(taskId);
    this.fetchActiveTimer(taskId);
  }

  private loadTimeLogs(taskId: number): void {
    this.isLoadingTimeLogs = true;
    this.timeTrackingService.getTimeLogs(taskId).subscribe({
      next: (logs) => {
        this.detailTimeLogs = logs;
        this.isLoadingTimeLogs = false;
      },
      error: () => {
        this.isLoadingTimeLogs = false;
        this.toastService.error('Failed to load time logs.');
      }
    });
  }

  private loadTotalTime(taskId: number): void {
    this.timeTrackingService.getTotalTime(taskId).subscribe({
      next: (res) => {
        this.detailTotalMinutes = res.totalMinutes;
      },
      error: () => {
        this.detailTotalMinutes = 0;
      }
    });
  }

  private fetchActiveTimer(taskId: number): void {
    this.timeTrackingService.getActiveTimer(taskId).subscribe({
      next: (timer) => {
        this.activeTimer = timer;
        if (timer) {
          this.startTimerTicker();
        } else {
          this.stopTimerTicker();
        }
      },
      error: () => {
        this.activeTimer = null;
        this.stopTimerTicker();
      }
    });
  }

  private startTimerTicker(): void {
    this.stopTimerTicker();
    if (!this.activeTimer) return;
    this.timerIntervalId = setInterval(() => {
      if (!this.activeTimer) return;
      this.activeTimer = {
        ...this.activeTimer,
        elapsedSeconds: (this.activeTimer.elapsedSeconds || 0) + 1
      };
      this.updateTimerDisplay();
    }, 1000);
  }

  private stopTimerTicker(): void {
    if (this.timerIntervalId) {
      clearInterval(this.timerIntervalId);
      this.timerIntervalId = null;
    }
  }

  private updateTimerDisplay(): void {
    if (!this.activeTimer) {
      this.timerDisplay = '00:00:00';
      return;
    }

    const secs = this.activeTimer.elapsedSeconds ?? 0;
    const hours = Math.floor(secs / 3600);
    const minutes = Math.floor((secs % 3600) / 60);
    const seconds = secs % 60;
    const pad = (num: number) => String(num).padStart(2, '0');

    this.timerDisplay = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }

  startTimer(): void {
    if (!this.detailTask?.id) return;
    this.isTimerActionLoading = true;
    this.timeTrackingService.startTimer(this.detailTask.id).subscribe({
      next: (timer) => {
        this.activeTimer = timer;
        this.updateTimerDisplay();
        this.startTimerTicker();
        this.loadTimeLogs(this.detailTask!.id!);
        this.loadTotalTime(this.detailTask!.id!);
        this.isTimerActionLoading = false;
        this.toastService.success('Timer started.');
      },
      error: () => {
        this.isTimerActionLoading = false;
        this.toastService.error('Failed to start timer.');
      }
    });
  }

  stopTimer(): void {
    if (!this.detailTask?.id) return;
    this.isTimerActionLoading = true;
    this.timeTrackingService.stopTimer(this.detailTask.id).subscribe({
      next: (timer) => {
        this.activeTimer = null;
        this.stopTimerTicker();
        this.loadTimeLogs(this.detailTask!.id!);
        this.loadTotalTime(this.detailTask!.id!);
        this.isTimerActionLoading = false;
        this.toastService.success('Timer stopped.');
      },
      error: () => {
        this.isTimerActionLoading = false;
        this.toastService.error('Failed to stop timer.');
      }
    });
  }

  logManualTime(): void {
    if (!this.detailTask?.id) return;

    const minutes = (this.manualLogHours || 0) * 60 + (this.manualLogMinutes || 0);
    if (minutes <= 0) {
      this.toastService.error('Please enter a valid duration.');
      return;
    }

    this.isLoggingTime = true;
    this.timeTrackingService.logManualTime(this.detailTask!.id!, minutes, this.manualLogDate, this.manualLogNote).subscribe({
      next: () => {
        this.manualLogHours = 0;
        this.manualLogMinutes = 0;
        this.manualLogNote = '';
        this.loadTimeLogs(this.detailTask!.id!);
        this.loadTotalTime(this.detailTask!.id!);
        this.isLoggingTime = false;
        this.toastService.success('Time log added.');
      },
      error: () => {
        this.isLoggingTime = false;
        this.toastService.error('Failed to log time.');
      }
    });
  }

  deleteTimeLog(entryId: number, isManual: boolean | undefined): void {
    if (!entryId || !isManual) return;
    if (!confirm('Delete this time entry?')) return;
    this.timeTrackingService.deleteTimeLog(entryId).subscribe({
      next: () => {
        if (this.detailTask?.id) {
          this.loadTimeLogs(this.detailTask!.id!);
          this.loadTotalTime(this.detailTask!.id!);
        }
        this.toastService.success('Time entry deleted.');
      },
      error: () => {
        this.toastService.error('Failed to delete time entry.');
      }
    });
  }

  setDetailTab(tab: any): void {
    this.activeDetailTab = tab;
  }

  private refreshDetail(taskId: number): void {
    this.loadSubtasks(taskId);
    this.loadAttachments(taskId);
    this.loadTimeTracking(taskId);
  }

  ngOnInit(): void {
    this.subs.push(
      this.authService.currentUser$.subscribe(user => {
        this.currentUsername = user?.username || 'User';
        this.currentUserId = user?.id || null;
        this.currentUserRole = user?.role || null;
      })
    );
    this.canAssign = this.authService.isAdminOrManager();
    this.loadTasks();
    this.loadTeams();
    if (this.canAssign) {
      this.loadUsers();
    }
  }

  loadTeams(): void {
    this.teamService.getMyTeams().subscribe({
      next: (teams: Team[]) => this.teams = teams,
      error: () => {
        // non-critical
      }
    });
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.stopTimerTicker();
  }

  loadTasks(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.taskService.getAllTasks().subscribe({
      next: (tasks) => {
        this.tasks = tasks;
        this.fetchSubtaskSummaries();
        this.applyFilter();
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Failed to load tasks. Please try again.';
        this.toastService.error('Failed to load tasks.');
        console.error('Error loading tasks:', err);
      }
    });
  }

  loadUsers(): void {
    this.userService.getAssignableUsers().subscribe({
      next: (users) => this.users = users,
      error: () => { } // Non-critical, fail silently
    });
  }

  applyFilter(): void {
    let result = [...this.tasks];

    // Status / special filters
    if (this.activeFilter === 'ASSIGNED_TO_ME') {
      result = result.filter(t => t.assigneeIds?.includes(this.currentUserId || -1));
    } else if (this.activeFilter === 'DUE_TODAY') {
      result = result.filter(t => this.isDueToday(t.dueDate) && t.status !== TaskStatus.COMPLETED);
    } else if (this.activeFilter !== 'ALL') {
      result = result.filter(t => t.status === this.activeFilter);
    }

    // Priority filter
    if (this.activePriorityFilter !== 'ALL') {
      result = result.filter(t => t.priority === this.activePriorityFilter);
    }

    // Search
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase().trim();
      result = result.filter(t =>
        t.title.toLowerCase().includes(query) ||
        (t.description && t.description.toLowerCase().includes(query)) ||
        (t.assigneeNames && t.assigneeNames.some(name => name.toLowerCase().includes(query)))
      );
    }

    // Sort by priority (HIGH first → MEDIUM → LOW), then by due date ascending
    if (this.sortByPriority) {
      const priorityOrder: Record<string, number> = { 'HIGH': 0, 'MEDIUM': 1, 'LOW': 2 };
      result.sort((a, b) => {
        const pa = priorityOrder[a.priority || 'LOW'] ?? 3;
        const pb = priorityOrder[b.priority || 'LOW'] ?? 3;
        if (pa !== pb) return pa - pb;
        return (a.dueDate || '').localeCompare(b.dueDate || '');
      });
    }

    this.filteredTasks = result;
  }

  setFilter(filter: string): void {
    this.activeFilter = filter;
    this.applyFilter();
  }

  setPriorityFilter(priority: string): void {
    this.activePriorityFilter = priority;
    this.applyFilter();
  }

  onSearchChange(): void {
    this.applyFilter();
  }

  toggleSortByPriority(): void {
    this.sortByPriority = !this.sortByPriority;
    this.applyFilter();
  }

  getFilterCount(filter: string): number {
    if (filter === 'ALL') return this.tasks.length;
    if (filter === 'ASSIGNED_TO_ME') return this.assignedToMeTasks;
    if (filter === 'DUE_TODAY') return this.dueTodayTasks;
    return this.tasks.filter(t => t.status === filter).length;
  }

  private fetchSubtaskSummaries(): void {
    this.tasks.forEach(task => {
      if (!task.id) return;
      this.taskService.getSubtaskSummary(task.id).subscribe({
        next: (summary) => {
          task.subtaskSummary = summary;
        },
        error: () => {
          // ignore
        }
      });
    });
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case TaskStatus.PENDING: return 'bg-secondary';
      case TaskStatus.IN_PROGRESS: return 'bg-warning text-dark';
      case TaskStatus.COMPLETED: return 'bg-success';
      default: return 'bg-secondary';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case TaskStatus.PENDING: return 'To-Do';
      case TaskStatus.IN_PROGRESS: return 'In Progress';
      case TaskStatus.COMPLETED: return 'Done';
      default: return status;
    }
  }

  getPriorityBadgeClass(priority: string): string {
    switch (priority) {
      case 'HIGH': return 'tf-priority-high';
      case 'MEDIUM': return 'bg-warning text-amber-700';
      case 'LOW': return 'tf-priority-low';
      default: return 'bg-secondary';
    }
  }

  getPriorityLabel(priority: string): string {
    switch (priority) {
      case 'HIGH': return 'High';
      case 'MEDIUM': return 'Medium';
      case 'LOW': return 'Low';
      default: return priority;
    }
  }

  isOverdue(dueDate: string): boolean {
    if (!dueDate) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return new Date(dueDate) < today;
  }

  isDueToday(dueDate: string): boolean {
    if (!dueDate) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const due = new Date(dueDate);
    due.setHours(0, 0, 0, 0);
    return due.getTime() === today.getTime();
  }

  toggleComments(taskId: number): void {
    this.expandedCommentTaskId = this.expandedCommentTaskId === taskId ? null : taskId;
  }

  openDetailModal(task: Task): void {
    this.detailTask = task;
    this.showDetailModal = true;
    this.resetDetailData();
    if (task.id) {
      this.refreshDetail(task.id);
      this.loadTimeTracking(task.id);
    }
  }

  closeDetailModal(): void {
    this.showDetailModal = false;
    this.detailTask = null;
    this.resetDetailData();
    this.stopTimerTicker();
  }

  addSubtask(): void {
    if (!this.detailTask?.id || !this.newSubtaskTitle.trim()) return;

    this.isAddingSubtask = true;
    this.taskService.createSubtask(this.detailTask.id, this.newSubtaskTitle.trim()).subscribe({
      next: () => {
        this.newSubtaskTitle = '';
        this.isAddingSubtask = false;
        this.refreshDetail(this.detailTask!.id!);
      },
      error: () => {
        this.isAddingSubtask = false;
        this.toastService.error('Failed to add subtask.');
      }
    });
  }

  toggleSubtask(subtask: Subtask): void {
    if (!subtask.id) return;
    this.taskService.toggleSubtask(subtask.id).subscribe({
      next: () => {
        if (this.detailTask?.id) {
          this.refreshDetail(this.detailTask.id);
        }
      },
      error: () => {
        this.toastService.error('Failed to update subtask.');
      }
    });
  }

  deleteSubtask(subtask: Subtask): void {
    if (!subtask.id) return;
    if (!confirm('Delete this subtask?')) return;

    this.taskService.deleteSubtask(subtask.id).subscribe({
      next: () => {
        if (this.detailTask?.id) {
          this.refreshDetail(this.detailTask.id);
        }
      },
      error: () => {
        this.toastService.error('Failed to delete subtask.');
      }
    });
  }

  canModifyDetails(task: Task): boolean {
    // Admin and Manager can update any task (subject to backend access rules)
    if (this.authService.isAdminOrManager()) {
      return true;
    }

    // Members can edit tasks they own or tasks assigned to them.
    if (!this.currentUserId) {
      return false;
    }
    return !!(task.userId === this.currentUserId || task.assigneeIds?.includes(this.currentUserId || -1));
  }

  canLogTime(): boolean {
    // Viewers are read-only and cannot start/stop timers or log time.
    return this.currentUserRole !== 'VIEWER';
  }


  formatDuration(seconds: number): string {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  formatMinutes(minutes: number): string {
    const hrs = Math.floor(minutes / 60);
    const mins = minutes % 60;
    const parts = [];
    if (hrs > 0) parts.push(`${hrs}h`);
    if (mins > 0) parts.push(`${mins}m`);
    return parts.length ? parts.join(' ') : '0m';
  }

  uploadAttachment(file: File | undefined): void {
    if (!this.detailTask?.id || !file) return;
    if (this.detailAttachments.length >= 5) {
      this.toastService.error('Maximum 5 attachments reached.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.toastService.error('File too large (max 5 MB).');
      return;
    }

    this.taskService.uploadAttachment(this.detailTask.id, file).subscribe({
      next: () => {
        this.toastService.success('Attachment uploaded.');
        this.refreshDetail(this.detailTask!.id!);
      },
      error: () => {
        this.toastService.error('Failed to upload attachment.');
      }
    });
  }

  downloadAttachment(attachment: TaskAttachment): void {
    if (!attachment.id) return;
    this.taskService.downloadAttachment(attachment.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = attachment.originalName;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.toastService.error('Failed to download file.');
      }
    });
  }

  deleteAttachment(att: TaskAttachment): void {
    if (!att.id) return;
    if (!confirm('Delete this attachment?')) return;
    this.taskService.deleteAttachment(att.id).subscribe({
      next: () => {
        this.toastService.success('Attachment deleted.');
        if (this.detailTask?.id) {
          this.refreshDetail(this.detailTask.id);
        }
      },
      error: () => {
        this.toastService.error('Failed to delete attachment.');
      }
    });
  }

  getAssigneeNames(task: Task): string {
    if (task.assigneeNames && task.assigneeNames.length > 0) return task.assigneeNames.join(', ');
    return 'Unassigned';
  }

  getAssigneeInitial(name: string): string {
    return (name || 'U').charAt(0).toUpperCase();
  }

  getInitials(name: string | null | undefined): string {
    if (!name) return 'U';
    const parts = name.trim().split(' ');
    if (parts.length === 0) return 'U';
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  }

  isAssigneeSelected(userId: number | undefined, target: 'new' | 'edit'): boolean {
    if (userId == null) return false;
    const list = target === 'new' ? this.newTask.assigneeIds : (this.editTask.assigneeIds || []);
    return list.includes(userId);
  }

  toggleAssignee(userId: number | undefined, target: 'new' | 'edit'): void {
    if (userId == null) return;
    if (target === 'new') {
      if (this.newTask.assigneeIds.includes(userId)) {
        this.newTask.assigneeIds = this.newTask.assigneeIds.filter(id => id !== userId);
      } else {
        this.newTask.assigneeIds = [...this.newTask.assigneeIds, userId];
      }
    } else {
      if (!this.editTask.assigneeIds) this.editTask.assigneeIds = [];
      if (this.editTask.assigneeIds.includes(userId)) {
        this.editTask.assigneeIds = this.editTask.assigneeIds.filter(id => id !== userId);
      } else {
        this.editTask.assigneeIds = [...this.editTask.assigneeIds, userId];
      }
    }
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${parseFloat((bytes / Math.pow(1024, i)).toFixed(1))} ${sizes[i]}`;
  }

  getAttachmentIcon(mimeType: string): string {
    if (!mimeType) return 'bi-file-earmark';
    if (mimeType.includes('pdf')) return 'bi-file-earmark-pdf-fill';
    if (mimeType.includes('image')) return 'bi-file-earmark-image-fill';
    if (mimeType.includes('word')) return 'bi-file-earmark-word-fill';
    if (mimeType.includes('excel')) return 'bi-file-earmark-excel-fill';
    if (mimeType.includes('zip') || mimeType.includes('compressed')) return 'bi-file-earmark-zip-fill';
    if (mimeType.includes('text')) return 'bi-file-earmark-text-fill';
    return 'bi-file-earmark';
  }

  confirmDelete(taskId: number): void {
    this.deleteTaskId = taskId;
  }

  cancelDelete(): void {
    this.deleteTaskId = null;
  }

  deleteTask(): void {
    if (this.deleteTaskId === null) return;
    this.isDeleting = true;

    this.taskService.deleteTask(this.deleteTaskId).subscribe({
      next: () => {
        this.tasks = this.tasks.filter(t => t.id !== this.deleteTaskId);
        this.applyFilter();
        this.deleteTaskId = null;
        this.isDeleting = false;
        this.toastService.success('Task deleted successfully.');
      },
      error: (err) => {
        this.errorMessage = 'Failed to delete task. Please try again.';
        this.toastService.error('Failed to delete task.');
        this.deleteTaskId = null;
        this.isDeleting = false;
        console.error('Error deleting task:', err);
      }
    });
  }

  quickStatusChange(task: Task, newStatus: TaskStatus): void {
    this.updatingTaskId = task.id!;
    const updatedTask = { ...task, status: newStatus };
    this.taskService.updateTask(task.id!, updatedTask).subscribe({
      next: (result) => {
        const idx = this.tasks.findIndex(t => t.id === task.id);
        if (idx > -1) this.tasks[idx] = result;
        this.applyFilter();
        this.updatingTaskId = null;
        this.toastService.success(`Status updated to ${this.getStatusLabel(newStatus)}.`);
      },
      error: () => {
        this.updatingTaskId = null;
        this.toastService.error('Failed to update status.');
      }
    });
  }

  completeTask(task: Task): void {
    if (task.status === TaskStatus.COMPLETED) return;
    this.quickStatusChange(task, TaskStatus.COMPLETED);
  }

  private showSuccess(message: string): void {
    this.successMessage = message;
    setTimeout(() => this.successMessage = '', 3000);
  }

  // --- Create Task Modal ---
  openCreateModal(): void {
    this.newTask = { title: '', description: '', dueDate: this.todayDate, status: 'PENDING', priority: 'MEDIUM', teamId: null, assigneeIds: [] };
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
  }

  // --- Edit Task Modal ---
  openEditModal(task: Task): void {
    this.editTask = { ...task, assigneeIds: [...(task.assigneeIds || [])] };
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
  }

  getEditAssigneeNames(): string {
    if (!this.editTask.assigneeNames || this.editTask.assigneeNames.length === 0) return 'Unassigned';
    return this.editTask.assigneeNames.join(', ');
  }

  submitEditModal(): void {
    if (!this.editTask.title || !this.editTask.dueDate || !this.editTask.id) return;
    this.isUpdating = true;

    this.taskService.updateTask(this.editTask.id, this.editTask).subscribe({
      next: (updated) => {
        const idx = this.tasks.findIndex(t => t.id === updated.id);
        if (idx > -1) this.tasks[idx] = updated;
        this.applyFilter();
        this.isUpdating = false;
        this.showEditModal = false;
        this.toastService.success('Task updated successfully!');
      },
      error: (err) => {
        this.isUpdating = false;
        this.toastService.error('Failed to update task.');
        console.error('Error updating task:', err);
      }
    });
  }

  submitCreateModal(): void {
    if (!this.newTask.title || !this.newTask.dueDate) return;
    this.isCreating = true;

    const payload: any = {
      title: this.newTask.title,
      description: this.newTask.description,
      dueDate: this.newTask.dueDate,
      status: this.newTask.status,
      priority: this.newTask.priority,
      teamId: this.newTask.teamId,
      assigneeIds: this.newTask.assigneeIds
    };

    this.taskService.createTask(payload).subscribe({
      next: (created) => {
        this.tasks.push(created);
        this.applyFilter();
        this.isCreating = false;
        this.showCreateModal = false;
        this.toastService.success('Task created successfully!');
      },
      error: (err) => {
        this.isCreating = false;
        this.errorMessage = 'Failed to create task. Please try again.';
        this.toastService.error('Failed to create task.');
        console.error('Error creating task:', err);
      }
    });
  }
}
