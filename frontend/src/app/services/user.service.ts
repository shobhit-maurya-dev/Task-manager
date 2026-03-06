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
}
