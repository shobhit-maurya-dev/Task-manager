import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ActiveTimer, Task, TaskSummary, Subtask, SubtaskSummary, TaskAttachment, TimeEntry } from '../models/task.model';

const TASK_API = `${environment.apiUrl}/tasks`;

@Injectable({
  providedIn: 'root'
})
export class TaskService {

  constructor(private http: HttpClient) {}

  getAllTasks(priority?: string): Observable<Task[]> {
    let params = new HttpParams();
    if (priority) {
      params = params.set('priority', priority);
    }
    return this.http.get<Task[]>(TASK_API, { params });
  }

  getTaskById(id: number): Observable<Task> {
    return this.http.get<Task>(`${TASK_API}/${id}`);
  }

  createTask(task: Task): Observable<Task> {
    return this.http.post<Task>(TASK_API, task);
  }

  updateTask(id: number, task: Task): Observable<Task> {
    return this.http.put<Task>(`${TASK_API}/${id}`, task);
  }

  deleteTask(id: number): Observable<void> {
    return this.http.delete<void>(`${TASK_API}/${id}`);
  }

  getSummary(): Observable<TaskSummary> {
    return this.http.get<TaskSummary>(`${TASK_API}/summary`);
  }

  getSubtasks(taskId: number): Observable<Subtask[]> {
    return this.http.get<Subtask[]>(`${TASK_API}/${taskId}/subtasks`);
  }

  createSubtask(taskId: number, title: string, assignedToId?: number): Observable<Subtask> {
    return this.http.post<Subtask>(`${TASK_API}/${taskId}/subtasks`, { title, assignedToId });
  }

  toggleSubtask(subtaskId: number): Observable<Subtask> {
    return this.http.patch<Subtask>(`${environment.apiUrl}/subtasks/${subtaskId}/toggle`, {});
  }

  deleteSubtask(subtaskId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/subtasks/${subtaskId}`);
  }

  getSubtaskSummary(taskId: number): Observable<SubtaskSummary> {
    return this.http.get<SubtaskSummary>(`${TASK_API}/${taskId}/subtasks/summary`);
  }

  getAttachments(taskId: number): Observable<TaskAttachment[]> {
    return this.http.get<TaskAttachment[]>(`${TASK_API}/${taskId}/attachments`);
  }

  downloadAttachment(attachmentId: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/attachments/${attachmentId}/download`, { responseType: 'blob' });
  }

  uploadAttachment(taskId: number, file: File): Observable<TaskAttachment> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<TaskAttachment>(`${TASK_API}/${taskId}/attachments`, form);
  }

  deleteAttachment(attachmentId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/attachments/${attachmentId}`);
  }

  /* Time tracking */
  startTimer(taskId: number): Observable<ActiveTimer> {
    return this.http.post<ActiveTimer>(`${TASK_API}/${taskId}/timer/start`, {});
  }

  stopTimer(taskId: number): Observable<ActiveTimer> {
    return this.http.post<ActiveTimer>(`${TASK_API}/${taskId}/timer/stop`, {});
  }

  getActiveTimer(taskId: number): Observable<ActiveTimer | null> {
    return this.http.get<ActiveTimer>(`${TASK_API}/${taskId}/timer`, { observe: 'response' })
      .pipe(map((resp: HttpResponse<ActiveTimer>) => resp.body ?? null));
  }

  getTimeLogs(taskId: number): Observable<TimeEntry[]> {
    return this.http.get<TimeEntry[]>(`${TASK_API}/${taskId}/time-logs`);
  }

  getTotalTime(taskId: number): Observable<{ totalMinutes: number }> {
    return this.http.get<{ totalMinutes: number }>(`${TASK_API}/${taskId}/time-logs/total`);
  }

  logTimeManual(taskId: number, minutes: number, date: string, note?: string): Observable<TimeEntry> {
    return this.http.post<TimeEntry>(`${TASK_API}/${taskId}/time-logs`, { minutes, logDate: date, note });
  }

  deleteTimeLog(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/time-logs/${id}`);
  }
}
