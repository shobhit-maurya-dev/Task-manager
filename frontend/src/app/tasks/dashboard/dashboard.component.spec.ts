import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { TaskService } from '../../services/task.service';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';
import { Task, TaskStatus, TaskPriority } from '../../models/task.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;
  let userServiceSpy: jasmine.SpyObj<UserService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockTasks: Task[] = [
    { id: 1, title: 'Task A', status: TaskStatus.PENDING, priority: TaskPriority.HIGH, dueDate: '2026-03-15' },
    { id: 2, title: 'Task B', description: 'Desc B', status: TaskStatus.IN_PROGRESS, priority: TaskPriority.MEDIUM, dueDate: '2026-03-10' },
    { id: 3, title: 'Task C', status: TaskStatus.COMPLETED, priority: TaskPriority.LOW, dueDate: '2026-02-01' },
    { id: 4, title: 'Overdue Task', status: TaskStatus.PENDING, priority: TaskPriority.HIGH, dueDate: '2025-01-01' }
  ];

  beforeEach(async () => {
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['getAllTasks', 'updateTask', 'deleteTask', 'getSubtaskSummary']);
    taskServiceSpy.getAllTasks.and.callFake(() => of(mockTasks.map(t => ({ ...t }))));
    taskServiceSpy.updateTask.and.returnValue(of({ ...mockTasks[0] }));
    taskServiceSpy.deleteTask.and.returnValue(of(undefined));
    taskServiceSpy.getSubtaskSummary.and.returnValue(of({ total: 0, completed: 0 }));

    userServiceSpy = jasmine.createSpyObj('UserService', ['getAssignableUsers']);
    userServiceSpy.getAssignableUsers.and.returnValue(of([]));

    authServiceSpy = jasmine.createSpyObj('AuthService', ['getCurrentUser', 'isAdminOrManager'], {
      currentUser$: of({ username: 'TestUser', email: 'test@test.com' }),
      isLoggedIn$: of(true)
    });
    authServiceSpy.isAdminOrManager.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, HttpClientTestingModule, RouterTestingModule, FormsModule],
      providers: [
        { provide: TaskService, useValue: taskServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: UserService, useValue: userServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load tasks on init', () => {
    expect(taskServiceSpy.getAllTasks).toHaveBeenCalled();
    expect(component.tasks.length).toBe(4);
    expect(component.isLoading).toBeFalse();
  });

  it('should set current username from AuthService', () => {
    expect(component.currentUsername).toBe('TestUser');
  });

  // --- Stats ---
  it('should calculate stats correctly', () => {
    expect(component.totalTasks).toBe(4);
    expect(component.pendingTasks).toBe(2);
    expect(component.inProgressTasks).toBe(1);
    expect(component.completedTasks).toBe(1);
  });

  it('should detect overdue tasks', () => {
    expect(component.overdueTasks).toBeGreaterThanOrEqual(1);
  });

  // --- Filtering ---
  it('should show all tasks with ALL filter', () => {
    component.setFilter('ALL');
    expect(component.filteredTasks.length).toBe(4);
  });

  it('should filter by PENDING', () => {
    component.setFilter(TaskStatus.PENDING);
    expect(component.filteredTasks.every(t => t.status === TaskStatus.PENDING)).toBeTrue();
  });

  it('should filter by IN_PROGRESS', () => {
    component.setFilter(TaskStatus.IN_PROGRESS);
    expect(component.filteredTasks.every(t => t.status === TaskStatus.IN_PROGRESS)).toBeTrue();
  });

  it('should filter by COMPLETED', () => {
    component.setFilter(TaskStatus.COMPLETED);
    expect(component.filteredTasks.every(t => t.status === TaskStatus.COMPLETED)).toBeTrue();
  });

  // --- Search ---
  it('should search tasks by title', () => {
    component.searchQuery = 'Task A';
    component.onSearchChange();
    expect(component.filteredTasks.length).toBe(1);
    expect(component.filteredTasks[0].title).toBe('Task A');
  });

  it('should search tasks by description', () => {
    component.searchQuery = 'Desc B';
    component.onSearchChange();
    expect(component.filteredTasks.length).toBe(1);
    expect(component.filteredTasks[0].title).toBe('Task B');
  });

  it('should show empty list for non-matching search', () => {
    component.searchQuery = 'XYZ_NO_MATCH';
    component.onSearchChange();
    expect(component.filteredTasks.length).toBe(0);
  });

  // --- Delete ---
  it('should set deleteTaskId on confirmDelete', () => {
    component.confirmDelete(1);
    expect(component.deleteTaskId).toBe(1);
  });

  it('should clear deleteTaskId on cancelDelete', () => {
    component.confirmDelete(1);
    component.cancelDelete();
    expect(component.deleteTaskId).toBeNull();
  });

  it('should delete a task and refresh list', () => {
    component.confirmDelete(1);
    component.deleteTask();

    expect(taskServiceSpy.deleteTask).toHaveBeenCalledWith(1);
    expect(component.tasks.find(t => t.id === 1)).toBeUndefined();
    expect(component.deleteTaskId).toBeNull();
  });

  // --- Quick status change ---
  it('should call updateTask on quickStatusChange', () => {
    const task = { ...mockTasks[0] };
    taskServiceSpy.updateTask.and.returnValue(of({ ...task, status: TaskStatus.IN_PROGRESS }));

    component.quickStatusChange(task, TaskStatus.IN_PROGRESS);

    expect(taskServiceSpy.updateTask).toHaveBeenCalledWith(task.id!, jasmine.objectContaining({ status: TaskStatus.IN_PROGRESS }));
  });

  // --- Error handling ---
  it('should display error message when loading fails', () => {
    taskServiceSpy.getAllTasks.and.returnValue(throwError(() => ({ status: 500 })));
    component.loadTasks();
    expect(component.errorMessage).toBe('Failed to load tasks. Please try again.');
  });

  // --- Badge helpers ---
  it('should return correct status badge class', () => {
    expect(component.getStatusBadgeClass(TaskStatus.PENDING)).toBe('bg-secondary');
    expect(component.getStatusBadgeClass(TaskStatus.IN_PROGRESS)).toBe('bg-warning text-dark');
    expect(component.getStatusBadgeClass(TaskStatus.COMPLETED)).toBe('bg-success');
  });

  it('should return correct priority badge class', () => {
    expect(component.getPriorityBadgeClass('HIGH')).toBe('tf-priority-high');
    expect(component.getPriorityBadgeClass('MEDIUM')).toBe('bg-warning text-amber-700');
    expect(component.getPriorityBadgeClass('LOW')).toBe('tf-priority-low');
  });

  it('should return correct status label', () => {
    expect(component.getStatusLabel(TaskStatus.PENDING)).toBe('To-Do');
    expect(component.getStatusLabel(TaskStatus.IN_PROGRESS)).toBe('In Progress');
    expect(component.getStatusLabel(TaskStatus.COMPLETED)).toBe('Done');
  });

  // --- isOverdue ---
  it('should detect past dates as overdue', () => {
    expect(component.isOverdue('2020-01-01')).toBeTrue();
  });

  it('should NOT detect future dates as overdue', () => {
    expect(component.isOverdue('2099-12-31')).toBeFalse();
  });

  it('should return false for empty dueDate', () => {
    expect(component.isOverdue('')).toBeFalse();
  });

  // --- Filter count ---
  it('should return correct filter counts', () => {
    expect(component.getFilterCount('ALL')).toBe(4);
    expect(component.getFilterCount(TaskStatus.PENDING)).toBe(2);
    expect(component.getFilterCount(TaskStatus.IN_PROGRESS)).toBe(1);
    expect(component.getFilterCount(TaskStatus.COMPLETED)).toBe(1);
  });
});
