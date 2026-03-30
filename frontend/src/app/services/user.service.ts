import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { User } from '../models/user.model';

const API = `${environment.apiUrl}/users`;

@Injectable({
  providedIn: 'root'
})
export class UserService {

  constructor(private http: HttpClient) {}

  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(API);
  }

  getAdminUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${environment.apiUrl}/admin/users`);
  }

  // Returns only DEVELOPER + TESTER users (for assignment dropdown)
  getAssignableUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${API}/assignable`);
  }

  // Self role change max 2 times, 15-day 
  changeMyRole(role: string): Observable<any> {
    return this.http.put(`${API}/me/role`, { role });
  }

  // Admin/Manager changes another user's role
  changeUserRole(userId: number, role: string): Observable<any> {
    return this.http.put(`${API}/${userId}/role`, { role });
  }

  // Get current user profile
  getMyProfile(): Observable<User> {
    return this.http.get<User>(`${API}/me`);
  }

  // Update current user's profile (name/email)
  updateMyProfile(update: { username?: string; email?: string }): Observable<User> {
    return this.http.put<User>(`${API}/me`, update);
  }


  // Delete / deactivate current user's account
  deleteMyAccount(confirmEmail: string): Observable<void> {
    return this.http.request<void>('delete', `${API}/me`, { body: { email: confirmEmail } });
  }

  // Change password endpoint for security settings
  changeMyPassword(data: { currentPassword: string; newPassword: string }): Observable<any> {
    return this.http.patch(`${API}/me/password`, data);
  }

  // Get current user's sessions (active + inactive)
  getMySessions(status?: 'ACTIVE' | 'INACTIVE'): Observable<any[]> {
    const q = status ? `?status=${status}` : '';
    return this.http.get<any[]>(`${API}/me/sessions${q}`);
  }

  // Revoke a session by ID
  revokeMySession(sessionId: number): Observable<any> {
    return this.http.delete(`${API}/me/sessions/${sessionId}`);
  }

  setUserActive(userId: number, active: boolean): Observable<any> {
    return this.http.patch(`${environment.apiUrl}/admin/users/${userId}/status`, { active });
  }

  // Deletes a user through admin endpoint.
  deleteUser(userId: number): Observable<any> {
    return this.http.delete(`${environment.apiUrl}/admin/users/${userId}`);
  }
}
