export interface Task {
  id?: number;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string;
  userId?: number;
  userName?: string;
  assignedToId?: number | null;
  assignedToName?: string;
  assigneeIds?: number[]; // supports multi-assign and filtering
  assigneeNames?: string[]; // supports names shown in UI
  teamId?: number;
  teamName?: string;
  createdAt?: string;
  updatedAt?: string;
  // Subtask summary (optional)
  subtaskSummary?: SubtaskSummary;
}

export interface SubtaskSummary {
  total: number;
  completed: number;
}

export interface Subtask {
  id?: number;
  taskId: number;
  title: string;
  isComplete: boolean;
  assignedToId?: number | null;
  assignedToName?: string;
  createdById?: number;
  createdByName?: string;
  createdAt?: string;
  completedAt?: string | null;
}

export interface TaskAttachment {
  id?: number;
  taskId: number;
  uploaderId?: number;
  uploaderName?: string;
  originalName: string;
  mimeType: string;
  fileSizeBytes: number;
  uploadedAt?: string;
}

export enum TaskStatus {
  PENDING = 'PENDING',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED'
}

export enum TaskPriority {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH'
}

export interface TaskComment {
  id?: number;
  taskId: number;
  authorId?: number;
  authorName?: string;
  body: string;
  createdAt?: string;
}

export interface ActivityLog {
  id?: number;
  taskId?: number | null;
  actorId: number;
  actorName?: string;
  actionCode: string;
  message: string;
  createdAt?: string;
}

export interface TimeEntry {
  id?: number;
  taskId: number;
  userId?: number;
  username?: string;
  minutes: number;
  logDate: string;
  isManual: boolean;
  description?: string;
  createdAt?: string;
}

export interface ActiveTimer {
  id?: number;
  taskId: number;
  userId?: number;
  username?: string;
  startTime: string;
  startedAt?: string; // alias for older templates
  elapsedSeconds: number;
}

export interface TaskSummary {
  totalTasks: number;
  pendingTasks: number;
  inProgressTasks: number;
  completedTasks: number;
  overdueTasks: number;
  completionRate: number;
  highPriorityTasks?: number;
  mediumPriorityTasks?: number;
  lowPriorityTasks?: number;
  statusCounts: { [key: string]: number };
  priorityCounts: { [key: string]: number };
}
