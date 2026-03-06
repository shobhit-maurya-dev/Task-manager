import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { TaskService } from '../../services/task.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { UserService } from '../../services/user.service';
import { Task, TaskStatus, TaskPriority } from '../../models/task.model';
import { User } from '../../models/user.model';
import { AnalyticsComponent } from '../../analytics/analytics.component';
import { ActivityFeedComponent } from '../../activity-feed/activity-feed.component';
import { CommentsComponent } from '../../comments/comments.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, AnalyticsComponent, ActivityFeedComponent, CommentsComponent],
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
  readonly TaskStatus = TaskStatus;
  readonly TaskPriority = TaskPriority;
  private subs: Subscription[] = [];

  // Users for assignment
  users: User[] = [];
  canAssign = false; // Only true for Admin/Manager

  // Comments toggle per task
  expandedCommentTaskId: number | null = null;

  // Task Detail Modal
  detailTask: Task | null = null;
  showDetailModal = false;

  // Sort by priority
  sortByPriority = false;

  // Create Task Modal
  showCreateModal = false;
  isCreating = false;
  todayDate = new Date().toISOString().split('T')[0];
  newTask: { title: string; description: string; dueDate: string; status: string; priority: string; assignedToId: number | null } = {
    title: '', description: '', dueDate: '', status: 'PENDING', priority: 'MEDIUM', assignedToId: null
  };

  // Edit Task Modal
  showEditModal = false;
  isUpdating = false;
  editTask: Task = { title: '', description: '', status: TaskStatus.PENDING, priority: TaskPriority.MEDIUM, dueDate: '', assignedToId: null };

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
  get assignedToMeTasks(): number { return this.tasks.filter(t => t.assignedToId === this.currentUserId).length; }
  get dueTodayTasks(): number { return this.tasks.filter(t => this.isDueToday(t.dueDate) && t.status !== TaskStatus.COMPLETED).length; }

  constructor(
    private taskService: TaskService,
    private authService: AuthService,
    private toastService: ToastService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.subs.push(
      this.authService.currentUser$.subscribe(user => {
        this.currentUsername = user?.username || 'User';
        this.currentUserId = user?.id || null;
      })
    );
    this.canAssign = this.authService.isAdminOrManager();
    this.loadTasks();
    if (this.canAssign) {
      this.loadUsers();
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  loadTasks(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.taskService.getAllTasks().subscribe({
      next: (tasks) => {
        this.tasks = tasks;
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
      error: () => {} // Non-critical, fail silently
    });
  }

  applyFilter(): void {
    let result = [...this.tasks];

    // Status / special filters
    if (this.activeFilter === 'ASSIGNED_TO_ME') {
      result = result.filter(t => t.assignedToId === this.currentUserId);
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
        (t.assignedToName && t.assignedToName.toLowerCase().includes(query))
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
  }

  closeDetailModal(): void {
    this.detailTask = null;
    this.showDetailModal = false;
  }

  getAssigneeName(task: Task): string {
    if (task.assignedToName) return task.assignedToName;
    if (task.assignedToId) {
      const u = this.users.find(u => u.id === task.assignedToId);
      return u ? u.username : 'Unknown';
    }
    return 'Unassigned';
  }

  getAssigneeInitial(name: string): string {
    return (name || 'U').charAt(0).toUpperCase();
  }

  getSelectedAssigneeName(): string {
    if (!this.newTask.assignedToId) return 'Unassigned';
    const u = this.users.find(u => u.id === this.newTask.assignedToId);
    return u ? u.username : 'Unknown';
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
        this.toastService.success(`Task marked as "${this.getStatusLabel(newStatus)}".`);
      },
      error: (err) => {
        this.errorMessage = 'Failed to update task status.';
        this.toastService.error('Failed to update task status.');
        this.updatingTaskId = null;
        console.error('Error updating task:', err);
      }
    });
  }

  private showSuccess(message: string): void {
    this.successMessage = message;
    setTimeout(() => this.successMessage = '', 3000);
  }

  // --- Create Task Modal ---
  openCreateModal(): void {
    this.newTask = { title: '', description: '', dueDate: this.todayDate, status: 'PENDING', priority: 'MEDIUM', assignedToId: null };
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
  }

  // --- Edit Task Modal ---
  openEditModal(task: Task): void {
    this.editTask = { ...task };
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
  }

  getEditAssigneeName(): string {
    if (!this.editTask.assignedToId) return 'Unassigned';
    const u = this.users.find(u => u.id === this.editTask.assignedToId);
    return u ? u.username : 'Unknown';
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
      assignedToId: this.newTask.assignedToId
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
