import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { TaskService } from '../../services/task.service';
import { ToastService } from '../../services/toast.service';
import { UserService } from '../../services/user.service';
import { AuthService } from '../../services/auth.service';
import { Task, TaskStatus, TaskPriority } from '../../models/task.model';
import { User } from '../../models/user.model';
import { CommentsComponent } from '../../comments/comments.component';

@Component({
  selector: 'app-task-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, CommentsComponent],
  templateUrl: './task-form.component.html',
  styleUrl: './task-form.component.css'
})
export class TaskFormComponent implements OnInit {
  task: Task = {
    title: '',
    description: '',
    status: TaskStatus.PENDING,
    priority: TaskPriority.MEDIUM,
    dueDate: '',
    assignedToId: null
  };

  isEditMode = false;
  taskId: number | null = null;
  isLoading = false;
  isSaving = false;
  errorMessage = '';
  pageTitle = 'Create New Task';
  users: User[] = [];
  canAssign = false; // Only true for Admin/Manager

  statusOptions = [
    { label: 'To-Do', value: TaskStatus.PENDING },
    { label: 'In Progress', value: TaskStatus.IN_PROGRESS },
    { label: 'Done', value: TaskStatus.COMPLETED }
  ];

  priorityOptions = [
    { label: 'Low', value: TaskPriority.LOW },
    { label: 'Medium', value: TaskPriority.MEDIUM },
    { label: 'High', value: TaskPriority.HIGH }
  ];

  constructor(
    private taskService: TaskService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService,
    private userService: UserService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.canAssign = this.authService.isAdminOrManager();
    if (this.canAssign) {
      this.loadUsers();
    }
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.taskId = +id;
      this.pageTitle = 'Edit Task';
      this.loadTask();
    } else {
      // Set default due date to tomorrow
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      this.task.dueDate = this.formatDate(tomorrow);
    }
  }

  loadTask(): void {
    if (!this.taskId) return;
    this.isLoading = true;

    this.taskService.getTaskById(this.taskId).subscribe({
      next: (task) => {
        this.task = task;
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 404) {
          this.errorMessage = 'Task not found.';
        } else {
          this.errorMessage = 'Failed to load task. Please try again.';
        }
        console.error('Error loading task:', err);
      }
    });
  }

  get todayDate(): string {
    return this.formatDate(new Date());
  }

  onSubmit(): void {
    this.errorMessage = '';

    // Validate required fields
    if (!this.task.title?.trim()) {
      this.errorMessage = 'Task title is required.';
      return;
    }
    if (!this.task.dueDate) {
      this.errorMessage = 'Due date is required.';
      return;
    }

    this.isSaving = true;

    if (this.isEditMode && this.taskId) {
      this.taskService.updateTask(this.taskId, this.task).subscribe({
        next: () => {
          this.isSaving = false;
          this.toastService.success('Task updated successfully!');
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.isSaving = false;
          this.errorMessage = err.error?.message || 'Failed to update task. Please try again.';
          this.toastService.error('Failed to update task.');
          console.error('Error updating task:', err);
        }
      });
    } else {
      this.taskService.createTask(this.task).subscribe({
        next: () => {
          this.isSaving = false;
          this.toastService.success('Task created successfully!');
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.isSaving = false;
          this.errorMessage = err.error?.message || 'Failed to create task. Please try again.';
          this.toastService.error('Failed to create task.');
          console.error('Error creating task:', err);
        }
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/dashboard']);
  }

  loadUsers(): void {
    this.userService.getAssignableUsers().subscribe({
      next: (users) => this.users = users,
      error: () => {} // Non-critical
    });
  }

  getAssigneeInitial(): string {
    const name = this.getSelectedAssigneeName();
    return (name || 'U').charAt(0).toUpperCase();
  }

  getSelectedAssigneeName(): string {
    if (!this.task.assignedToId) return 'Unassigned';
    const u = this.users.find(u => u.id === this.task.assignedToId);
    return u ? u.username : 'Unknown';
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
