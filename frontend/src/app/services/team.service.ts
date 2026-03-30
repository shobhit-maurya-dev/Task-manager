import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Team, TeamDetail } from '../models/team.model';

const API = `${environment.apiUrl}/teams`;

@Injectable({
  providedIn: 'root'
})
export class TeamService {
  constructor(private http: HttpClient) {}

  getMyTeams(): Observable<Team[]> {
    return this.http.get<Team[]>(API);
  }

  getTeamById(id: number): Observable<TeamDetail> {
    return this.http.get<TeamDetail>(`${API}/${id}`);
  }

  createTeam(team: Partial<Team>): Observable<Team> {
    return this.http.post<Team>(API, team);
  }

  updateTeam(teamId: number, team: Partial<Team>): Observable<Team> {
    return this.http.put<Team>(`${API}/${teamId}`, team);
  }

  addMember(teamId: number, userId: number): Observable<any> {
    // The backend expects userId as a @RequestParam, not in the body.
    return this.http.post(`${API}/${teamId}/members`, null, {
      params: { userId: userId.toString() }
    });
  }

  removeMember(teamId: number, userId: number): Observable<any> {
    return this.http.delete(`${API}/${teamId}/members/${userId}`);
  }

  deleteTeam(teamId: number): Observable<any> {
    return this.http.delete(`${API}/${teamId}`);
  }

  toggleLeader(teamId: number, userId: number, isLeader: boolean): Observable<any> {
    return this.http.put(`${API}/${teamId}/members/${userId}/leader`, null, {
      params: { isLeader: isLeader.toString() }
    });
  }
}
