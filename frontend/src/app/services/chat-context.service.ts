import { Injectable } from '@angular/core';
import { TaskService } from './task.service';
import { TeamService } from './team.service';
import { AuthService } from './auth.service';
import { Task } from '../models/task.model';

export interface ChatContext {
  tasks: Task[];
  teams: any[];
  username: string;
  loadedAt: Date;
}

@Injectable({
  providedIn: 'root'
})
export class ChatContextService {
  private context: ChatContext | null = null;
  private loading = false;

  constructor(
    private taskService: TaskService,
    private teamService: TeamService,
    private authService: AuthService
  ) {
    // Auto-load when user logs in
    this.authService.isLoggedIn$.subscribe(isLoggedIn => {
      if (isLoggedIn) {
        this.loadContext();
      } else {
        this.clearContext();
      }
    });
  }

  /** Load tasks & teams once after login — no repeated DB calls */
  loadContext(): void {
    if (this.loading || this.context) return;
    this.loading = true;

    const user = this.authService.getCurrentUser();
    const username = user?.username || 'User';

    // Load tasks
    this.taskService.getAllTasks().subscribe({
      next: (tasks) => {
        // Store tasks; teams load next
        this.context = {
          tasks,
          teams: [],
          username,
          loadedAt: new Date()
        };
        this.loadTeams();
      },
      error: () => {
        // Even if tasks fail, initialize empty context
        this.context = { tasks: [], teams: [], username, loadedAt: new Date() };
        this.loadTeams();
      }
    });
  }

  private loadTeams(): void {
    this.teamService.getMyTeams().subscribe({
      next: (teams) => {
        if (this.context) this.context.teams = teams;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  clearContext(): void {
    this.context = null;
    this.loading = false;
  }

  // Forced refresh — call after creating/updating tasks 
  refreshContext(): void {
    this.context = null;
    this.loadContext();
  }

  /**
   * Smart local search — searches tasks and teams using keywords.
   * Returns a human-readable string answer, or null if no relevant data found.
   */
  searchLocal(query: string): string | null {
    if (!this.context) return null;

    const q = query.toLowerCase();
    const { tasks, teams, username } = this.context;

    // --- Greeting ---
    if (/^(hi|hello|hey|hii|hey)/.test(q)) {
      return `Hello ${username}! I am tracking your ${tasks.length} tasks and ${teams.length} teams. How can I help you today?`;
    }

    //  Task count / summary 
    if (q.includes('tasks count') || q.includes('how many task') || q.includes('total task') || q.includes('status')) {
      const pending = tasks.filter(t => t.status === 'PENDING').length;
      const inProgress = tasks.filter(t => t.status === 'IN_PROGRESS').length;
      const done = tasks.filter(t => t.status === 'COMPLETED').length;
      return `You have a total of **${tasks.length} tasks**:\n• Pending: ${pending}\n• In Progress: ${inProgress}\n• Completed: ${done}`;
    }

    // Overdue tasks 
    if (q.includes('overdue') || q.includes('deadline') || q.includes('due') || q.includes('late')) {
      const now = new Date();
      const overdue = tasks.filter(t => t.dueDate && new Date(t.dueDate) < now && t.status !== 'COMPLETED');
      if (overdue.length === 0) return '✅ Great! No overdue tasks. Everything is on track.';
      const list = overdue.map(t => `• **${t.title}** (Due: ${new Date(t.dueDate).toLocaleDateString()})`).join('\n');
      return `⚠️ Found **${overdue.length} overdue tasks**:\n${list}`;
    }

    // High priority tasks 
    if (q.includes('high priority') || q.includes('urgent') || q.includes('important')) {
      const high = tasks.filter(t => t.priority === 'HIGH' && t.status !== 'COMPLETED');
      if (high.length === 0) return '✅ No urgent HIGH priority tasks right now.';
      const list = high.map(t => `• **${t.title}**`).join('\n');
      return `🔴 You have **${high.length} HIGH priority tasks** active:\n${list}`;
    }

    //  Specific task by name 
    const matchedTasks = tasks.filter(t => t.title.toLowerCase().includes(q) || (t.description || '').toLowerCase().includes(q));
    if (matchedTasks.length > 0) {
      const t = matchedTasks[0];
      const assignees = t.assigneeNames?.length ? t.assigneeNames.join(', ') : 'Unassigned';
      return `📋 Found task: **"${t.title}"**\nStatus: ${t.status} | Priority: ${t.priority}\nAssigned to: ${assignees}\nDue Date: ${t.dueDate ? new Date(t.dueDate).toLocaleDateString() : 'No date set'}`;
    }

    // --- Teams ---
    if (q.includes('team') || q.includes('member') || q.includes('collaboration')) {
      if (teams.length === 0) return 'You are not part of any team. Join or create one to start collaborating!';
      const list = teams.map((t: any) => `• **${t.name}** (${t.members?.length || 0} members)`).join('\n');
      return `👥 Your ${teams.length} Teams:\n${list}`;
    }

    // --- Completed tasks ---
    if (q.includes('completed') || q.includes('done') || q.includes('finished')) {
      const done = tasks.filter(t => t.status === 'COMPLETED');
      return `✅ Impressive! You have completed **${done.length} tasks** in total.`;
    }

    // No local match found → signal to fall back to AI
    return null;
  }

  isReady(): boolean {
    return this.context !== null;
  }
}
