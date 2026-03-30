import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AiQueryRequest {
  query: string;
}

export interface AiQueryResponse {
  answer: string;
}

@Injectable({
  providedIn: 'root'
})
export class AiService {

  private readonly API_URL = `${environment.apiUrl}/ai/query`;

  constructor(private http: HttpClient) {}

  askGemini(query: string): Observable<AiQueryResponse> {
    return this.http.post<AiQueryResponse>(this.API_URL, { query });
  }
}
