import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ActiveTimer, TimeEntry } from '../models/task.model';

const API = `${environment.apiUrl}`;

@Injectable({
  providedIn: 'root'
})
export class TimeTrackingService {
  constructor(private http: HttpClient) {}

  getActiveTimer(taskId: number): Observable<ActiveTimer | null> {
    return this.http.get<ActiveTimer>(`${API}/tasks/${taskId}/timer`);
  }

  startTimer(taskId: number): Observable<ActiveTimer> {
    return this.http.post<ActiveTimer>(`${API}/tasks/${taskId}/timer/start`, {});
  }

  stopTimer(taskId: number): Observable<ActiveTimer> {
    return this.http.post<ActiveTimer>(`${API}/tasks/${taskId}/timer/stop`, {});
  }

  getTimeLogs(taskId: number): Observable<TimeEntry[]> {
    return this.http.get<TimeEntry[]>(`${API}/tasks/${taskId}/time-logs`);
  }

  getTotalTime(taskId: number): Observable<{ totalMinutes: number }> {
    return this.http.get<{ totalMinutes: number }>(`${API}/tasks/${taskId}/time-logs/total`);
  }

  logManualTime(taskId: number, minutes: number, logDate: string, note?: string): Observable<TimeEntry> {
    return this.http.post<TimeEntry>(`${API}/tasks/${taskId}/time-logs`, {
      minutes,
      logDate,
      note
    });
  }

  deleteTimeLog(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/time-logs/${id}`);
  }
}
